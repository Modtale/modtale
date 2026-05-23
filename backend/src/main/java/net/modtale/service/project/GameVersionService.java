package net.modtale.service.project;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import jakarta.annotation.PostConstruct;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.modtale.model.project.Project;

@Service
public class GameVersionService {
    private static final Logger logger = LoggerFactory.getLogger(GameVersionService.class);
    private static final Pattern GAME_VERSION_PATTERN = Pattern.compile("^(\\d{4})\\.(\\d{2})\\.(\\d{2})-([a-zA-Z0-9]+)$");
    private static final Pattern SEMVER_PATTERN = Pattern.compile(
            "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
                    + "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?"
                    + "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$"
    );

    private final RestTemplate restTemplate = new RestTemplate();
    private final MongoTemplate mongoTemplate;

    public GameVersionService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Value("${app.hytale.maven.release-url:https://maven.hytale.com/release/com/hypixel/hytale/Server/maven-metadata.xml}")
    private String releaseMetadataUrl;

    @Value("${app.hytale.maven.pre-release-url:https://maven.hytale.com/pre-release/com/hypixel/hytale/Server/maven-metadata.xml}")
    private String preReleaseMetadataUrl;

    private volatile GameVersionCatalog cachedCatalog = new GameVersionCatalog(List.of(), List.of(), List.of(), List.of());
    private final Object refreshLock = new Object();

    @PostConstruct
    public void initialRefresh() {
        refreshCatalog();
    }

    @Scheduled(fixedDelayString = "${app.hytale.maven.poll-ms:3600000}")
    public void pollCatalog() {
        refreshCatalog();
    }

    public GameVersionCatalog getCatalog() {
        if (!cachedCatalog.allVersions().isEmpty()) return cachedCatalog;
        refreshCatalog();
        return cachedCatalog;
    }

    private void refreshCatalog() {
        synchronized (refreshLock) {
            try {
                List<String> release = fetchVersionsFromMetadata(releaseMetadataUrl);
                List<String> preRelease = fetchVersionsFromMetadata(preReleaseMetadataUrl);
                List<String> indexed = fetchIndexedGameVersions();

                if (release.isEmpty() && preRelease.isEmpty() && indexed.isEmpty()) {
                    logger.warn("Fetched empty release, pre-release, and indexed game version catalogs; keeping previous cached catalog.");
                    return;
                }

                Set<String> releaseSet = new HashSet<>(release);
                Set<String> preReleaseSet = new HashSet<>(preRelease);
                for (String indexedVersion : indexed) {
                    if (!releaseSet.contains(indexedVersion) && !preReleaseSet.contains(indexedVersion)) {
                        preRelease.add(indexedVersion);
                    }
                }

                List<String> sortedRelease = sortDescDistinct(release);
                List<String> sortedPreRelease = sortDescDistinct(preRelease);
                List<String> sortedAll = sortDescDistinct(mergeLists(sortedRelease, sortedPreRelease));

                List<GameVersionEntry> entries = new ArrayList<>(sortedAll.size());
                for (String version : sortedAll) {
                    if (releaseSet.contains(version)) {
                        entries.add(new GameVersionEntry(version, false, releaseMetadataUrl));
                    } else if (preReleaseSet.contains(version)) {
                        entries.add(new GameVersionEntry(version, true, preReleaseMetadataUrl));
                    } else {
                        entries.add(new GameVersionEntry(version, true, "indexed"));
                    }
                }

                cachedCatalog = new GameVersionCatalog(sortedRelease, sortedPreRelease, sortedAll, entries);
            } catch (Exception e) {
                logger.error("Failed to refresh Hytale game versions from Maven metadata.", e);
            }
        }
    }

    private List<String> fetchIndexedGameVersions() {
        Query query = new Query(Criteria.where("deletedAt").is(null));
        List<String> distinct = mongoTemplate.findDistinct(query, "versions.gameVersions", Project.class, String.class);
        return distinct == null ? List.of() : distinct;
    }

    private List<String> fetchVersionsFromMetadata(String metadataUrl) {
        String xml = restTemplate.getForObject(metadataUrl, String.class);
        if (xml == null || xml.isBlank()) {
            return List.of();
        }

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignored) {}

        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(new InputSource(new StringReader(xml)));
            NodeList versionNodes = document.getElementsByTagName("version");
            List<String> versions = new ArrayList<>();
            for (int i = 0; i < versionNodes.getLength(); i++) {
                String version = versionNodes.item(i).getTextContent();
                if (version != null && !version.isBlank()) {
                    versions.add(version.trim());
                }
            }
            return versions;
        } catch (Exception e) {
            throw new IllegalStateException("Unable to parse Maven metadata XML from: " + metadataUrl, e);
        }
    }

    private static List<String> sortDescDistinct(List<String> input) {
        return input.stream().filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(() -> new LinkedHashSet<>()),
                        set -> set.stream().sorted(GameVersionService::compareGameVersions).toList()
                ));
    }

    private static int compareGameVersions(String a, String b) {
        ParsedSemver semverA = parseSemver(a);
        ParsedSemver semverB = parseSemver(b);
        ParsedVersion legacyA = parseLegacyVersion(a);
        ParsedVersion legacyB = parseLegacyVersion(b);

        int rankA = semverA != null ? 0 : (legacyA != null ? 1 : 2);
        int rankB = semverB != null ? 0 : (legacyB != null ? 1 : 2);
        if (rankA != rankB) {
            return Integer.compare(rankA, rankB);
        }

        if (semverA != null && semverB != null) {
            return compareSemverDesc(semverA, semverB);
        }

        if (legacyA != null && legacyB != null) {
            int dateCompare = legacyB.dateKey.compareTo(legacyA.dateKey);
            if (dateCompare != 0) return dateCompare;
            return legacyB.hash.compareTo(legacyA.hash);
        }

        return b.compareTo(a);
    }

    private static ParsedVersion parseLegacyVersion(String version) {
        Matcher matcher = GAME_VERSION_PATTERN.matcher(version);
        if (!matcher.matches()) return null;
        String dateKey = matcher.group(1) + matcher.group(2) + matcher.group(3);
        return new ParsedVersion(dateKey, matcher.group(4));
    }

    private static ParsedSemver parseSemver(String version) {
        Matcher matcher = SEMVER_PATTERN.matcher(version);
        if (!matcher.matches()) return null;

        int major = Integer.parseInt(matcher.group(1));
        int minor = Integer.parseInt(matcher.group(2));
        int patch = Integer.parseInt(matcher.group(3));
        String preRelease = matcher.group(4);
        List<String> preReleaseIdentifiers = preRelease == null || preRelease.isBlank()
                ? List.of()
                : List.of(preRelease.split("\\."));

        return new ParsedSemver(major, minor, patch, preReleaseIdentifiers);
    }

    private static int compareSemverDesc(ParsedSemver a, ParsedSemver b) {
        if (a.major != b.major) return Integer.compare(b.major, a.major);
        if (a.minor != b.minor) return Integer.compare(b.minor, a.minor);
        if (a.patch != b.patch) return Integer.compare(b.patch, a.patch);

        boolean aStable = a.preReleaseIdentifiers.isEmpty();
        boolean bStable = b.preReleaseIdentifiers.isEmpty();
        if (aStable && !bStable) return -1;
        if (!aStable && bStable) return 1;
        if (aStable) return 0;

        int minSize = Math.min(a.preReleaseIdentifiers.size(), b.preReleaseIdentifiers.size());
        for (int i = 0; i < minSize; i++) {
            String ai = a.preReleaseIdentifiers.get(i);
            String bi = b.preReleaseIdentifiers.get(i);
            boolean aNumeric = ai.chars().allMatch(Character::isDigit);
            boolean bNumeric = bi.chars().allMatch(Character::isDigit);

            if (aNumeric && bNumeric) {
                int an = Integer.parseInt(ai);
                int bn = Integer.parseInt(bi);
                if (an != bn) return Integer.compare(bn, an);
            } else if (aNumeric != bNumeric) {
                return aNumeric ? 1 : -1;
            } else {
                int lex = bi.compareTo(ai);
                if (lex != 0) return lex;
            }
        }

        return Integer.compare(b.preReleaseIdentifiers.size(), a.preReleaseIdentifiers.size());
    }

    private record ParsedVersion(String dateKey, String hash) {}
    private record ParsedSemver(int major, int minor, int patch, List<String> preReleaseIdentifiers) {}

    private static List<String> mergeLists(List<String> a, List<String> b) {
        List<String> merged = new ArrayList<>(a.size() + b.size());
        merged.addAll(a);
        merged.addAll(b);
        return merged;
    }

    public record GameVersionEntry(String version, boolean preRelease, String sourceUrl) {}
    public record GameVersionCatalog(List<String> releaseVersions, List<String> preReleaseVersions, List<String> allVersions, List<GameVersionEntry> versions) {}
}
