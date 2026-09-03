package net.modtale.service.project.version;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.modtale.model.dto.project.ArtifactIdentityDTO;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.repository.project.ProjectRepository;
import org.springframework.stereotype.Service;

@Service
public class ArtifactIdentityService {
    private final ProjectRepository projects;
    private final CurseForgeApiClient curseForge;
    private volatile CachedAliases cachedAliases;
    private static final long ALIAS_CACHE_NANOS = java.time.Duration.ofMinutes(2).toNanos();

    public ArtifactIdentityService(ProjectRepository projects, CurseForgeApiClient curseForge) {
        this.projects = projects;
        this.curseForge = curseForge;
    }

    public ArtifactIdentityDTO.Response identify(ArtifactIdentityDTO.Request request) {
        List<ArtifactIdentityDTO.Artifact> artifacts = request == null ? List.of() : request.artifacts();
        List<String> hashes = values(artifacts, true);
        List<String> manifestIds = values(artifacts, false);
        Map<String, Project> byHash = indexVersions(
                hashes.isEmpty() ? List.of() : projects.findPublishedByVersionHashes(hashes), true);
        Map<String, Project> byManifest = indexVersions(
                manifestIds.isEmpty() ? List.of() : projects.findPublishedByManifestIds(manifestIds), false);
        List<Long> requestedFingerprints = artifacts.stream().map(ArtifactIdentityDTO.Artifact::curseForgeFingerprint)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, Project> modtaleByFingerprint = indexFingerprints(requestedFingerprints.isEmpty() ? List.of()
                : projects.findPublishedByCurseForgeFingerprints(requestedFingerprints));
        AliasIndex aliases = aliases();
        Map<Long, CurseForgeApiClient.CurseForgeFingerprintMatch> fingerprintMatches = curseForge.matchArtifacts(
                artifacts.stream()
                        .filter(artifact -> artifact.curseForgeFingerprint() != null)
                        .map(artifact -> new CurseForgeApiClient.CurseForgeArtifact(
                                artifact.curseForgeFingerprint(), artifact.key()))
                        .toList());
        List<ArtifactIdentityDTO.Match> matches = new ArrayList<>();
        for (ArtifactIdentityDTO.Artifact artifact : artifacts) {
            Project exact = byHash.get(normalize(artifact.sha256()));
            if (exact != null) {
                matches.add(modtaleMatch(artifact, exact, versionByHash(exact, artifact.sha256()), "sha256", 100));
                continue;
            }
            Project exactCfBinary = artifact.curseForgeFingerprint() == null ? null
                    : modtaleByFingerprint.get(artifact.curseForgeFingerprint());
            if (exactCfBinary != null) {
                matches.add(modtaleMatch(artifact, exactCfBinary,
                        versionByFingerprint(exactCfBinary, artifact.curseForgeFingerprint()), "curseforge-fingerprint", 100));
                continue;
            }
            CurseForgeApiClient.CurseForgeFingerprintMatch cf = artifact.curseForgeFingerprint() == null ? null
                    : fingerprintMatches.get(artifact.curseForgeFingerprint());
            if (cf != null) {
                Project canonical = aliases.byCurseForgeId().get(cf.projectId());
                if (canonical != null) matches.add(modtaleMatch(artifact, canonical, versionByNumber(canonical, artifact.version()), "curseforge-fingerprint+alias", 100));
                else matches.add(curseForgeMatch(artifact, cf.projectId(), cf.fileId(), null,
                        "curseforge-fingerprint", 100));
                continue;
            }
            Project linked = modtaleProjectFromUrl(artifact.website(), aliases);
            if (linked != null) {
                matches.add(modtaleMatch(artifact, linked, versionByNumber(linked, artifact.version()), "project-url", 98));
                continue;
            }
            String cfSlug = curseForgeSlug(artifact.website());
            if (cfSlug != null) {
                Project canonical = aliases.byCurseForgeSlug().get(cfSlug);
                if (canonical != null) {
                    matches.add(modtaleMatch(artifact, canonical, versionByNumber(canonical, artifact.version()),
                            "curseforge-url+alias", 98));
                    continue;
                }
                Optional<CurseForgeApiClient.CurseForgeProject> resolved = curseForge.resolveProject(cfSlug, null);
                if (resolved.isPresent()) {
                    CurseForgeApiClient.CurseForgeProject project = resolved.get();
                    matches.add(curseForgeMatch(artifact, Long.parseLong(project.id()), 0, project,
                            "curseforge-url", 96));
                    continue;
                }
            }
            Project manifest = byManifest.get(normalize(artifact.manifestId()));
            if (manifest != null) matches.add(modtaleMatch(artifact, manifest,
                    versionByManifest(manifest, artifact.manifestId(), artifact.version()), "hytale-manifest-id", 90));
        }
        return new ArtifactIdentityDTO.Response(matches);
    }

    public CurseForgeApiClient.CurseForgeSearchResult removeModtaleAliases(CurseForgeApiClient.CurseForgeSearchResult page) {
        AliasIndex index = aliases();
        List<CurseForgeApiClient.CurseForgeProject> filtered = page.projects().stream()
                .filter(project -> !index.byCurseForgeSlug().containsKey(normalize(project.slug())))
                .filter(project -> parseLong(project.id()).map(id -> !index.byCurseForgeId().containsKey(id)).orElse(true))
                .filter(project -> project.files().stream().map(CurseForgeApiClient.CurseForgeFile::fingerprint)
                        .filter(java.util.Objects::nonNull).noneMatch(index.byFingerprint()::containsKey))
                .toList();
        long removed = page.projects().size() - filtered.size();
        return new CurseForgeApiClient.CurseForgeSearchResult(filtered, page.index(), page.pageSize(),
                Math.max(filtered.size(), page.totalCount() - removed));
    }

    private AliasIndex aliases() {
        CachedAliases cached = cachedAliases;
        long now = System.nanoTime();
        if (cached != null && now - cached.createdAtNanos() < ALIAS_CACHE_NANOS) return cached.index();
        synchronized (this) {
            cached = cachedAliases;
            if (cached != null && now - cached.createdAtNanos() < ALIAS_CACHE_NANOS) return cached.index();
            AliasIndex rebuilt = buildAliases();
            cachedAliases = new CachedAliases(now, rebuilt);
            return rebuilt;
        }
    }

    private AliasIndex buildAliases() {
        Map<String, Project> slugs = new HashMap<>();
        Map<Long, Project> ids = new HashMap<>();
        Map<String, Project> modtaleSlugs = new HashMap<>();
        Map<Long, Project> fingerprints = new HashMap<>();
        for (Project project : projects.findPublishedIdentityIndex()) {
            modtaleSlugs.putIfAbsent(normalize(project.getSlug()), project);
            List<String> urls = new ArrayList<>(project.getLinks() == null ? List.of() : project.getLinks().values());
            if (project.getRepositoryUrl() != null) urls.add(project.getRepositoryUrl());
            for (String url : urls) {
                String slug = curseForgeSlug(url);
                if (slug != null) slugs.putIfAbsent(slug, project);
                curseForgeProjectId(url).ifPresent(id -> ids.putIfAbsent(id, project));
            }
            for (ProjectVersion version : safeVersions(project)) if (version.getCurseForgeFingerprint() != null)
                fingerprints.putIfAbsent(version.getCurseForgeFingerprint(), project);
        }
        return new AliasIndex(Map.copyOf(slugs), Map.copyOf(ids), Map.copyOf(modtaleSlugs), Map.copyOf(fingerprints));
    }

    private static Project modtaleProjectFromUrl(String url, AliasIndex aliases) {
        String slug = modtaleSlug(url);
        if (slug == null) return null;
        return aliases.byModtaleSlug().get(slug);
    }

    private Map<String, Project> indexVersions(List<Project> source, boolean hash) {
        Map<String, Project> result = new LinkedHashMap<>();
        java.util.Set<String> ambiguous = new java.util.HashSet<>();
        for (Project project : source) for (ProjectVersion version : safeVersions(project)) {
            String key = normalize(hash ? version.getHash() : version.getManifestId());
            if (!key.isBlank() && result.putIfAbsent(key, project) != null && result.get(key) != project) ambiguous.add(key);
        }
        ambiguous.forEach(result::remove);
        return result;
    }

    private static Map<Long, Project> indexFingerprints(List<Project> source) {
        Map<Long, Project> result = new LinkedHashMap<>();
        java.util.Set<Long> ambiguous = new java.util.HashSet<>();
        for (Project project : source) for (ProjectVersion version : safeVersions(project)) {
            Long key = version.getCurseForgeFingerprint();
            if (key != null && result.putIfAbsent(key, project) != null && result.get(key) != project) ambiguous.add(key);
        }
        ambiguous.forEach(result::remove);
        return result;
    }

    private static List<String> values(List<ArtifactIdentityDTO.Artifact> artifacts, boolean hash) {
        return artifacts.stream().map(a -> hash ? a.sha256() : a.manifestId()).map(ArtifactIdentityService::normalize)
                .filter(s -> !s.isBlank()).distinct().toList();
    }

    private static ArtifactIdentityDTO.Match modtaleMatch(ArtifactIdentityDTO.Artifact a, Project p, ProjectVersion v, String evidence, int confidence) {
        return new ArtifactIdentityDTO.Match(a.key(), "MODTALE", p.getId(), p.getSlug(), p.getTitle(),
                p.getClassification() == null ? "PLUGIN" : p.getClassification().name(),
                v == null ? a.version() : v.getVersionNumber(), v == null ? "" : v.getId(), evidence, confidence);
    }

    private static ArtifactIdentityDTO.Match curseForgeMatch(ArtifactIdentityDTO.Artifact a, long id, long fileId,
                                                               CurseForgeApiClient.CurseForgeProject p, String evidence, int confidence) {
        String slug = p == null ? "" : p.slug();
        String title = p == null ? "" : p.title();
        return new ArtifactIdentityDTO.Match(a.key(), "CURSEFORGE", "curseforge:" + id, slug, title,
                "PLUGIN", a.version(), fileId > 0 ? Long.toString(fileId) : "", evidence, confidence);
    }

    private static ProjectVersion versionByHash(Project p, String hash) { return safeVersions(p).stream().filter(v -> normalize(hash).equals(normalize(v.getHash()))).findFirst().orElse(null); }
    private static ProjectVersion versionByNumber(Project p, String number) { return safeVersions(p).stream().filter(v -> normalize(number).equals(normalize(v.getVersionNumber()))).findFirst().orElse(null); }
    private static ProjectVersion versionByManifest(Project p, String id, String version) { return safeVersions(p).stream().filter(v -> normalize(id).equals(normalize(v.getManifestId())) && (normalize(version).isBlank() || normalize(version).equals(normalize(v.getManifestVersion())))).findFirst().orElse(null); }
    private static ProjectVersion versionByFingerprint(Project p, Long fingerprint) { return safeVersions(p).stream().filter(v -> fingerprint != null && fingerprint.equals(v.getCurseForgeFingerprint())).findFirst().orElse(null); }
    private static List<ProjectVersion> safeVersions(Project p) { return p.getVersions() == null ? List.of() : p.getVersions(); }
    private static String normalize(String value) { return value == null ? "" : value.trim().toLowerCase(Locale.ROOT); }

    private static String curseForgeSlug(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if ((!"www.curseforge.com".equalsIgnoreCase(uri.getHost())
                    && !"curseforge.com".equalsIgnoreCase(uri.getHost())) || uri.getPath() == null) return null;
            var matcher = java.util.regex.Pattern.compile("^/hytale/mods/([a-z0-9-]+)(?:/.*)?$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(uri.getPath());
            return matcher.matches() ? normalize(matcher.group(1)) : null;
        } catch (IllegalArgumentException ex) { return null; }
    }

    private static Optional<Long> curseForgeProjectId(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (!"curseforge.com".equalsIgnoreCase(uri.getHost()) && !"www.curseforge.com".equalsIgnoreCase(uri.getHost())) return Optional.empty();
            String query = uri.getQuery();
            if (query == null) return Optional.empty();
            for (String item : query.split("&")) if (item.matches("(?:projectId|project-id)=\\d+")) return parseLong(item.substring(item.indexOf('=') + 1));
        } catch (IllegalArgumentException ignored) {}
        return Optional.empty();
    }

    private static String modtaleSlug(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (uri.getHost() == null) return null;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (!host.equals("modtale.net") && !host.endsWith(".modtale.net")) return null;
            var matcher = java.util.regex.Pattern.compile("^/(?:mod|project)/([a-z0-9-]+)/?.*$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(uri.getPath());
            return matcher.matches() ? normalize(matcher.group(1)) : null;
        } catch (IllegalArgumentException ex) { return null; }
    }

    private static Optional<Long> parseLong(String value) { try { return Optional.of(Long.parseLong(value)); } catch (RuntimeException ex) { return Optional.empty(); } }
    private record AliasIndex(Map<String, Project> byCurseForgeSlug, Map<Long, Project> byCurseForgeId,
                              Map<String, Project> byModtaleSlug, Map<Long, Project> byFingerprint) {}
    private record CachedAliases(long createdAtNanos, AliasIndex index) {}
}
