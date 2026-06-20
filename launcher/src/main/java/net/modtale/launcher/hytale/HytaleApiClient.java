package net.modtale.launcher.hytale;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public class HytaleApiClient {

    static final String AUTH_URL = "https://oauth.accounts.hytale.com/oauth2/auth";
    static final String TOKEN_URL = "https://oauth.accounts.hytale.com/oauth2/token";
    static final String LAUNCHER_DATA_URL = "https://account-data.hytale.com/my-account/get-launcher-data";
    static final String PUBLIC_PROFILE_BY_UUID_URL = "https://account-data.hytale.com/profile/uuid";
    static final String SOCIAL_FRIENDS_URL = "https://social.hytale.com/friends";
    static final String SESSION_URL = "https://sessions.hytale.com/game-session/new";
    static final String PATCHES_BASE_URL = "https://account-data.hytale.com/patches";
    static final String LAUNCHER_INFO_URL = "https://launcher.hytale.com/version/release/launcher.json";
    static final String BLOG_URL = "https://hytale.com/news";
    static final String BLOG_RSS_URL = "https://hytale.com/rss.xml";
    static final String CLIENT_ID = "hytale-launcher";
    static final String REDIRECT_URI = "https://accounts.hytale.com/consent/client";
    static final String SCOPES = "openid offline auth:launcher";

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration BLOG_CACHE_TTL = Duration.ofMinutes(15);
    private static final String FALLBACK_LAUNCHER_VERSION = "unknown";
    private static final String RELEASE_PATCHLINE = "release";
    private static final String PRE_RELEASE_PATCHLINE = "pre-release";
    private static final Pattern VERSIONED_PATCHLINE_PATTERN = Pattern.compile("v?\\d+\\.\\d+");
    private static final Pattern BLOG_ARTICLE_PATTERN = Pattern.compile("<article\\b[\\s\\S]*?</article>", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOG_LINK_PATTERN = Pattern.compile("<a\\b[^>]*href=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOG_IMAGE_PATTERN = Pattern.compile("<img\\b[^>]*src=\"([^\"]+)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOG_TITLE_PATTERN = Pattern.compile("<h[1-6]\\b[^>]*>([\\s\\S]*?)</h[1-6]>", Pattern.CASE_INSENSITIVE);
    private static final Pattern BLOG_DATE_PATTERN = Pattern.compile("\\b((?:January|February|March|April|May|June|July|August|September|October|November|December)\\s+\\d{1,2},\\s+\\d{4})\\b");
    private static final DateTimeFormatter BLOG_PAGE_DATE = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMMM d, yyyy")
            .toFormatter(Locale.ENGLISH);

    private final HttpClient httpClient;
    private final ObjectMapper mapper;
    private volatile String launcherVersion;
    private volatile long launcherVersionFetchedAt;
    private volatile List<HytaleBlogPost> blogPostCache = List.of();
    private volatile long blogPostFetchedAt;

    public HytaleApiClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    HytaleApiClient(HttpClient httpClient) {
        this.httpClient = httpClient;
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public TokenResponse exchangeCode(String code, String codeVerifier) {
        return postForm(TOKEN_URL, Map.of(
                "grant_type", "authorization_code",
                "code", code == null ? "" : code,
                "redirect_uri", REDIRECT_URI,
                "client_id", CLIENT_ID,
                "code_verifier", codeVerifier == null ? "" : codeVerifier
        ), TokenResponse.class);
    }

    public TokenResponse refreshToken(String refreshToken) {
        return postForm(TOKEN_URL, Map.of(
                "grant_type", "refresh_token",
                "refresh_token", refreshToken == null ? "" : refreshToken,
                "client_id", CLIENT_ID
        ), TokenResponse.class);
    }

    public HytaleProfile fetchProfile(String accessToken) {
        return fetchProfiles(accessToken).getFirst();
    }

    public List<HytaleProfile> fetchProfiles(String accessToken) {
        HttpRequest request = officialRequestBuilder(launcherDataUrl(), "release")
                .GET()
                .header("Authorization", "Bearer " + accessToken)
                .build();
        ProfilesResponse response = sendJson(request, ProfilesResponse.class);
        if (response.profiles == null || response.profiles.isEmpty()) {
            throw new HytaleApiException("No Hytale game profile was returned for this account.");
        }

        String owner = response.owner == null ? "" : response.owner;
        return response.profiles.stream()
                .map(profile -> new HytaleProfile(profile.username, profile.uuid, owner, profile.playtimeSeconds))
                .toList();
    }

    public List<HytaleFriend> fetchFriends(String sessionToken) {
        HttpRequest request = officialRequestBuilder(URI.create(SOCIAL_FRIENDS_URL), "release")
                .GET()
                .header("Authorization", "Bearer " + sessionToken)
                .build();
        return parseFriends(sendJson(request, JsonNode.class));
    }

    public Map<String, String> fetchPublicProfileUsernames(String sessionToken, List<String> uuids) {
        if (sessionToken == null || sessionToken.isBlank() || uuids == null || uuids.isEmpty()) {
            return Map.of();
        }
        Map<String, String> usernames = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();
        for (String uuid : uuids) {
            String normalizedUuid = uuid == null ? "" : uuid.trim();
            String key = normalizedUuid.toLowerCase(Locale.ROOT);
            if (normalizedUuid.isBlank() || !seen.add(key)) {
                continue;
            }
            try {
                HttpRequest request = officialRequestBuilder(
                        URI.create(PUBLIC_PROFILE_BY_UUID_URL + "/" + encodePathSegment(normalizedUuid)),
                        "release")
                        .GET()
                        .header("Authorization", "Bearer " + sessionToken)
                        .build();
                parsePublicProfileUsername(sendJson(request, JsonNode.class))
                        .ifPresent(username -> usernames.put(key, username));
            } catch (HytaleApiException ex) {
                if (ex.isAuthFailure()) {
                    throw ex;
                }
            }
        }
        return usernames;
    }

    public HytaleGameSession createGameSession(String accessToken, String uuid) {
        try {
            HttpRequest request = officialRequestBuilder(URI.create(SESSION_URL), "release")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(
                            mapper.writeValueAsString(Map.of("uuid", uuid == null ? "" : uuid)),
                            StandardCharsets.UTF_8))
                    .build();
            GameSessionResponse response = sendJson(request, GameSessionResponse.class);
            return new HytaleGameSession(
                    response.sessionToken == null ? "" : response.sessionToken,
                    response.identityToken == null ? "" : response.identityToken
            );
        } catch (IOException ex) {
            throw new HytaleApiException("Could not write Hytale game-session request.", ex);
        }
    }

    public List<HytaleVersion> getAvailableVersions(String accessToken, String branch) {
        String normalizedBranch = normalizeBranch(branch);
        String os = HytalePlatform.os();
        String arch = HytalePlatform.arch();
        Map<Integer, HytaleVersion> versionsByBuild = new LinkedHashMap<>();

        OfficialPatchesResponse latest = fetchPatches(accessToken, os, arch, normalizedBranch, 0);
        if (latest.steps != null) {
            latest.steps.stream()
                    .max(Comparator.comparingInt(step -> step.to))
                    .ifPresent(step -> versionsByBuild.put(step.to, toVersion(normalizedBranch, step, true)));
        }

        try {
            OfficialPatchesResponse chain = fetchPatches(accessToken, os, arch, normalizedBranch, 1);
            if (chain.steps != null) {
                chain.steps.stream()
                        .sorted(Comparator.comparingInt((OfficialPatchStep step) -> step.to).reversed())
                        .forEach(step -> versionsByBuild.putIfAbsent(step.to, toVersion(normalizedBranch, step, false)));
            }
        } catch (HytaleApiException ex) {
            if (ex.isAuthFailure()) {
                throw ex;
            }
        }

        return versionsByBuild.values().stream()
                .sorted(Comparator.comparingInt(HytaleVersion::build).reversed())
                .toList();
    }

    public List<String> getAvailablePatchlines(String accessToken, List<String> candidatePatchlines) {
        String os = HytalePlatform.os();
        String arch = HytalePlatform.arch();
        LinkedHashSet<String> patchlines = new LinkedHashSet<>();
        addAvailablePatchline(accessToken, os, arch, RELEASE_PATCHLINE, patchlines, true);

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(PRE_RELEASE_PATCHLINE);
        launcherDataPatchlines(accessToken).forEach(candidates::add);
        if (candidatePatchlines != null) {
            candidatePatchlines.stream()
                    .map(HytaleApiClient::normalizePatchlineId)
                    .flatMap(Optional::stream)
                    .forEach(candidates::add);
        }

        for (String candidate : candidates) {
            if (!RELEASE_PATCHLINE.equals(candidate)) {
                addAvailablePatchline(accessToken, os, arch, candidate, patchlines, false);
            }
        }
        return List.copyOf(patchlines);
    }

    public List<HytaleBlogPost> getBlogPosts(int count) {
        int safeCount = Math.max(1, count);
        return getAllBlogPosts().stream().limit(safeCount).toList();
    }

    public List<HytaleBlogPost> getAllBlogPosts() {
        long now = System.currentTimeMillis();
        List<HytaleBlogPost> cached = blogPostCache;
        if (!cached.isEmpty() && now - blogPostFetchedAt < BLOG_CACHE_TTL.toMillis()) {
            return cached;
        }

        HytaleApiException rssError = null;
        List<HytaleBlogPost> posts;
        try {
            posts = getBlogPostsFromRss(Integer.MAX_VALUE);
        } catch (HytaleApiException ex) {
            rssError = ex;
            posts = List.of();
        }
        if (!posts.isEmpty()) {
            posts = enrichRssPostsWithNewsImages(posts);
        }
        if (posts.isEmpty()) {
            try {
                posts = getBlogPostsFromHtml(Integer.MAX_VALUE);
            } catch (HytaleApiException ex) {
                if (rssError != null) {
                    throw rssError;
                }
                throw ex;
            }
        }
        blogPostCache = posts;
        blogPostFetchedAt = now;
        return posts;
    }

    private List<HytaleBlogPost> getBlogPostsFromHtml(int count) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BLOG_URL))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "text/html, application/xhtml+xml")
                .GET()
                .build();
        return parseBlogPosts(sendString(request), count);
    }

    private List<HytaleBlogPost> getBlogPostsFromRss(int count) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(BLOG_RSS_URL))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/rss+xml, application/xml, text/xml")
                .GET()
                .build();
        return parseBlogPosts(sendString(request), count);
    }

    private List<HytaleBlogPost> enrichRssPostsWithNewsImages(List<HytaleBlogPost> rssPosts) {
        List<HytaleBlogPost> htmlPosts;
        try {
            htmlPosts = getBlogPostsFromHtml(Integer.MAX_VALUE);
        } catch (HytaleApiException ex) {
            return rssPosts;
        }
        if (htmlPosts.isEmpty()) {
            return rssPosts;
        }

        Map<String, String> imageByUrl = new LinkedHashMap<>();
        Map<String, String> imageByTitle = new LinkedHashMap<>();
        for (HytaleBlogPost post : htmlPosts) {
            if (post.imageUrl().isBlank()) {
                continue;
            }
            imageByUrl.put(normalizedBlogUrl(post.url()), post.imageUrl());
            imageByTitle.put(normalizedBlogTitle(post.title()), post.imageUrl());
        }

        return rssPosts.stream()
                .map(post -> {
                    if (!post.imageUrl().isBlank()) {
                        return post;
                    }
                    String imageUrl = imageByUrl.getOrDefault(
                            normalizedBlogUrl(post.url()),
                            imageByTitle.getOrDefault(normalizedBlogTitle(post.title()), "")
                    );
                    return imageUrl.isBlank()
                            ? post
                            : new HytaleBlogPost(post.title(), post.url(), imageUrl, post.publishedAt());
                })
                .toList();
    }

    private OfficialPatchesResponse fetchPatches(String accessToken, String os, String arch, String branch, int fromBuild) {
        URI uri = URI.create(PATCHES_BASE_URL + "/" + os + "/" + arch + "/" + normalizeBranch(branch) + "/" + fromBuild);
        HttpRequest request = officialRequestBuilder(uri, branch)
                .GET()
                .header("Authorization", "Bearer " + accessToken)
                .build();
        return sendJson(request, OfficialPatchesResponse.class);
    }

    private void addAvailablePatchline(
            String accessToken,
            String os,
            String arch,
            String patchline,
            LinkedHashSet<String> patchlines,
            boolean required
    ) {
        try {
            OfficialPatchesResponse response = fetchPatches(accessToken, os, arch, patchline, 0);
            if (response.steps != null) {
                patchlines.add(patchline);
            }
        } catch (HytaleApiException ex) {
            if (required || ex.statusCode() == 429) {
                throw ex;
            }
        }
    }

    private List<String> launcherDataPatchlines(String accessToken) {
        try {
            HttpRequest request = officialRequestBuilder(launcherDataUrl(), RELEASE_PATCHLINE)
                    .GET()
                    .header("Authorization", "Bearer " + accessToken)
                    .build();
            return parsePatchlines(sendJson(request, JsonNode.class));
        } catch (HytaleApiException ex) {
            if (ex.isAuthFailure() || ex.statusCode() == 429) {
                throw ex;
            }
            return List.of();
        }
    }

    private HytaleVersion toVersion(String branch, OfficialPatchStep step, boolean latest) {
        return new HytaleVersion(
                branch,
                step.to,
                step.from,
                latest,
                emptyIfNull(step.pwr),
                emptyIfNull(step.pwrHead),
                emptyIfNull(step.sig)
        );
    }

    private URI launcherDataUrl() {
        return URI.create(LAUNCHER_DATA_URL + "?client_id=" + CLIENT_ID);
    }

    private HttpRequest.Builder officialRequestBuilder(URI uri, String branch) {
        String version = launcherVersion();
        return HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "hytale-launcher/" + version)
                .header("x-hytale-launcher-version", version)
                .header("x-hytale-launcher-branch", normalizeHeaderBranch(branch));
    }

    private String launcherVersion() {
        long now = System.currentTimeMillis();
        if (launcherVersion != null && now - launcherVersionFetchedAt < Duration.ofHours(6).toMillis()) {
            return launcherVersion;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(LAUNCHER_INFO_URL))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                launcherVersion = extractLauncherVersion(response.body()).orElse(FALLBACK_LAUNCHER_VERSION);
            } else if (launcherVersion == null) {
                launcherVersion = FALLBACK_LAUNCHER_VERSION;
            }
        } catch (IOException ex) {
            if (launcherVersion == null) {
                launcherVersion = FALLBACK_LAUNCHER_VERSION;
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (launcherVersion == null) {
                launcherVersion = FALLBACK_LAUNCHER_VERSION;
            }
        }

        launcherVersionFetchedAt = now;
        return launcherVersion;
    }

    private Optional<String> extractLauncherVersion(String json) throws IOException {
        JsonNode root = mapper.readTree(json);
        for (String key : List.of("version", "launcher_version", "build", "id")) {
            JsonNode node = root.get(key);
            if (node != null && !node.asText("").isBlank()) {
                return Optional.of(node.asText());
            }
        }
        return Optional.empty();
    }

    private <T> T postForm(String url, Map<String, String> values, Class<T> type) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formEncode(values), StandardCharsets.UTF_8))
                .build();
        return sendJson(request, type);
    }

    private <T> T sendJson(HttpRequest request, Class<T> type) {
        String body = sendString(request);
        try {
            return mapper.readValue(body, type);
        } catch (IOException ex) {
            throw new HytaleApiException("Could not read Hytale API response from " + request.uri(), ex);
        }
    }

    private String sendString(HttpRequest request) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new HytaleApiException("Hytale API returned HTTP " + response.statusCode()
                        + " for " + request.uri() + responseSnippet(response.body()),
                        response.statusCode(),
                        null,
                        retryAfterMillis(response.headers(), Instant.now()));
            }
            return response.body();
        } catch (IOException ex) {
            throw new HytaleApiException("Could not read Hytale API response from " + request.uri(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new HytaleApiException("Hytale API request was interrupted.", ex);
        }
    }

    private static String formEncode(Map<String, String> values) {
        List<String> encoded = new ArrayList<>();
        values.forEach((key, value) -> encoded.add(encode(key) + "=" + encode(value)));
        return String.join("&", encoded);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String encodePathSegment(String value) {
        return encode(value).replace("+", "%20");
    }

    public static String normalizeBranch(String branch) {
        return normalizePatchlineId(branch).orElse(RELEASE_PATCHLINE);
    }

    public static Optional<String> normalizePatchlineId(String branch) {
        if (branch == null || branch.isBlank()) {
            return Optional.empty();
        }
        String normalized = branch.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "release", "latest", "latest-release" -> Optional.of(RELEASE_PATCHLINE);
            case "pre-release", "pre release", "prerelease", "pre_release" -> Optional.of(PRE_RELEASE_PATCHLINE);
            default -> VERSIONED_PATCHLINE_PATTERN.matcher(normalized).matches()
                    ? Optional.of(normalized.startsWith("v") ? normalized : "v" + normalized)
                    : Optional.empty();
        };
    }

    private static String normalizeHeaderBranch(String branch) {
        return switch (normalizeBranch(branch)) {
            case PRE_RELEASE_PATCHLINE -> RELEASE_PATCHLINE;
            default -> RELEASE_PATCHLINE;
        };
    }

    private static String responseSnippet(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        return ": " + compact.substring(0, Math.min(compact.length(), 240));
    }

    private static long retryAfterMillis(HttpHeaders headers, Instant now) {
        if (headers == null) {
            return 0;
        }
        Optional<String> retryAfter = headers.firstValue("Retry-After");
        if (retryAfter.isPresent()) {
            long millis = parseRetryAfterMillis(retryAfter.get(), now);
            if (millis > 0) {
                return millis;
            }
        }
        for (String header : List.of("X-RateLimit-Reset", "RateLimit-Reset")) {
            Optional<String> reset = headers.firstValue(header);
            if (reset.isPresent()) {
                long millis = parseRateLimitResetMillis(reset.get(), now);
                if (millis > 0) {
                    return millis;
                }
            }
        }
        return 0;
    }

    private static long parseRetryAfterMillis(String value, Instant now) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        String trimmed = value.trim();
        try {
            return Math.max(0, Long.parseLong(trimmed) * 1000L);
        } catch (NumberFormatException ignored) {
            // Retry-After may be an HTTP-date.
        }
        try {
            Instant resetAt = ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            return Math.max(0, resetAt.toEpochMilli() - now.toEpochMilli());
        } catch (DateTimeParseException ignored) {
            return 0;
        }
    }

    private static long parseRateLimitResetMillis(String value, Instant now) {
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            long epochSeconds = now.getEpochSecond();
            if (parsed > epochSeconds) {
                return Math.max(0, (parsed - epochSeconds) * 1000L);
            }
            return Math.max(0, parsed * 1000L);
        } catch (NumberFormatException ex) {
            return parseRetryAfterMillis(value, now);
        }
    }

    private static String emptyIfNull(String value) {
        return value == null ? "" : value;
    }

    static List<HytaleBlogPost> parseBlogPosts(String content, int count) {
        if (content == null || content.isBlank() || count <= 0) {
            return List.of();
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("<?xml") || trimmed.contains("<rss")) {
            return parseBlogRss(trimmed, count);
        }
        return parseBlogHtml(trimmed, count);
    }

    private static List<HytaleBlogPost> parseBlogHtml(String html, int count) {
        List<HytaleBlogPost> posts = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Matcher articleMatcher = BLOG_ARTICLE_PATTERN.matcher(html);
        while (articleMatcher.find() && posts.size() < count) {
            String article = articleMatcher.group();
            String link = absoluteHytaleUrl(firstMatch(BLOG_LINK_PATTERN, article));
            String title = cleanRssText(firstMatch(BLOG_TITLE_PATTERN, article));
            if (title.isBlank() || link.isBlank() || !seen.add(link)) {
                continue;
            }
            posts.add(new HytaleBlogPost(
                    title,
                    link,
                    absoluteHytaleUrl(firstMatch(BLOG_IMAGE_PATTERN, article)),
                    parseBlogPageDate(firstMatch(BLOG_DATE_PATTERN, article))
            ));
        }
        return posts;
    }

    private static List<HytaleBlogPost> parseBlogRss(String xml, int count) {
        if (xml == null || xml.isBlank() || count <= 0) {
            return List.of();
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            configureSecureXml(factory);
            Document document = factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))
            );
            NodeList items = document.getElementsByTagName("item");
            List<HytaleBlogPost> posts = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (int index = 0; index < items.getLength() && posts.size() < count; index++) {
                if (!(items.item(index) instanceof Element item)) {
                    continue;
                }
                String title = cleanRssText(childText(item, "title"));
                String link = cleanRssText(childText(item, "link"));
                if (title.isBlank() || link.isBlank() || !seen.add(link)) {
                    continue;
                }
                posts.add(new HytaleBlogPost(
                        title,
                        link,
                        rssImageUrl(item),
                        parseRssDate(childText(item, "pubDate"))
                ));
            }
            return posts;
        } catch (IOException | ParserConfigurationException | SAXException ex) {
            throw new HytaleApiException("Could not parse Hytale blog feed.", ex);
        }
    }

    private static String rssImageUrl(Element item) {
        String imageUrl = childAttribute(item, "enclosure", "url");
        if (imageUrl.isBlank()) {
            imageUrl = childAttribute(item, "media:content", "url");
        }
        if (imageUrl.isBlank()) {
            imageUrl = childAttribute(item, "media:thumbnail", "url");
        }
        if (imageUrl.isBlank()) {
            imageUrl = firstMatch(BLOG_IMAGE_PATTERN, childText(item, "description"));
        }
        return absoluteHytaleUrl(imageUrl);
    }

    private static String firstMatch(Pattern pattern, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        Matcher matcher = pattern.matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String absoluteHytaleUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.startsWith("//")) {
            return "https:" + trimmed;
        }
        if (trimmed.startsWith("/")) {
            return "https://hytale.com" + trimmed;
        }
        return "https://hytale.com/" + trimmed;
    }

    private static String normalizedBlogUrl(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().replaceAll("/+$", "").toLowerCase(Locale.ROOT);
    }

    private static String normalizedBlogTitle(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return cleanRssText(value).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    private static Instant parseBlogPageDate(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            LocalDate date = LocalDate.parse(value.trim(), BLOG_PAGE_DATE);
            return date.atTime(LocalTime.NOON).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ex) {
            return Instant.EPOCH;
        }
    }

    static List<HytaleFriend> parseFriends(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return List.of();
        }
        List<HytaleFriend> friends = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (root.isArray()) {
            addFriends(root, friends, seen);
        }
        List<JsonNode> friendArrays = new ArrayList<>();
        collectFriendArrays(root, "", friendArrays);
        for (JsonNode array : friendArrays) {
            addFriends(array, friends, seen);
        }
        return friends;
    }

    static Optional<String> parsePublicProfileUsername(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return Optional.empty();
        }
        if (root.isArray()) {
            for (JsonNode candidate : root) {
                Optional<String> username = parsePublicProfileUsername(candidate);
                if (username.isPresent()) {
                    return username;
                }
            }
            return Optional.empty();
        }
        if (!root.isObject()) {
            return Optional.empty();
        }
        String username = firstText(root, "username", "displayName", "display_name", "name", "playerName", "player_name");
        if (!username.isBlank()) {
            return Optional.of(username);
        }
        JsonNode profile = firstObject(root, "profile", "player", "user");
        username = firstText(profile, "username", "displayName", "display_name", "name", "playerName", "player_name");
        return username.isBlank() ? Optional.empty() : Optional.of(username);
    }

    static List<String> parsePatchlines(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return List.of();
        }
        LinkedHashSet<String> patchlines = new LinkedHashSet<>();
        collectPatchlines(root, "", patchlines);
        return List.copyOf(patchlines);
    }

    private static void collectPatchlines(JsonNode node, String fieldName, LinkedHashSet<String> patchlines) {
        if (node == null || node.isNull()) {
            return;
        }
        String field = fieldName == null ? "" : fieldName.toLowerCase(Locale.ROOT);
        boolean patchlineContext = field.contains("patchline") || field.contains("channel");
        if (node.isTextual()) {
            if (patchlineContext) {
                normalizePatchlineId(node.asText()).ifPresent(patchlines::add);
            }
            return;
        }
        if (node.isArray()) {
            node.forEach(child -> collectPatchlines(child, fieldName, patchlines));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        if (patchlineContext) {
            for (String identityField : List.of("id", "name", "patchline", "channel", "selected_channel")) {
                JsonNode identity = node.get(identityField);
                if (identity != null && identity.isTextual()) {
                    normalizePatchlineId(identity.asText()).ifPresent(patchlines::add);
                }
            }
        }
        node.fields().forEachRemaining(entry -> {
            if (patchlineContext) {
                normalizePatchlineId(entry.getKey()).ifPresent(patchlines::add);
            }
            collectPatchlines(entry.getValue(), entry.getKey(), patchlines);
        });
    }

    private static void addFriends(JsonNode array, List<HytaleFriend> friends, Set<String> seen) {
        for (JsonNode candidate : array) {
            Optional<HytaleFriend> friend = toFriend(candidate);
            if (friend.isEmpty()) {
                continue;
            }
            HytaleFriend value = friend.get();
            String key = value.uuid().isBlank()
                    ? value.displayName().toLowerCase(Locale.ROOT)
                    : value.uuid().toLowerCase(Locale.ROOT);
            if (seen.add(key)) {
                friends.add(value);
            }
        }
    }

    private static void collectFriendArrays(JsonNode node, String fieldName, List<JsonNode> friendArrays) {
        if (node == null || node.isNull()) {
            return;
        }
        String normalized = fieldName == null ? "" : fieldName.toLowerCase();
        if (node.isArray()) {
            if (normalized.contains("friend")) {
                friendArrays.add(node);
                return;
            }
            node.forEach(child -> collectFriendArrays(child, "", friendArrays));
            return;
        }
        if (!node.isObject()) {
            return;
        }
        node.fields().forEachRemaining(entry -> collectFriendArrays(entry.getValue(), entry.getKey(), friendArrays));
    }

    private static Optional<HytaleFriend> toFriend(JsonNode node) {
        if (node == null || !node.isObject()) {
            return Optional.empty();
        }
        JsonNode identity = firstObject(node, "friend", "profile", "player", "user", "account", "member");
        String username = firstIdentityText(node, identity,
                "username", "displayName", "display_name", "name", "playerName", "player_name");
        String uuid = firstIdentityText(node, identity,
                "uuid", "profileUuid", "profile_uuid", "playerUuid", "player_uuid", "id", "playerId", "player_id");
        if (username.isBlank() && uuid.isBlank()) {
            return Optional.empty();
        }
        String status = firstText(node, "status", "presence", "activity", "state");
        JsonNode presence = firstObject(node, "presence", "activity", "state");
        String normalizedStatus = status.toLowerCase(Locale.ROOT).replace('_', ' ').replace('-', ' ');
        boolean online = firstBoolean(node, "online", "isOnline", "is_online")
                || firstBoolean(presence, "online", "isOnline", "is_online")
                || normalizedStatus.contains("online")
                || normalizedStatus.contains("playing")
                || normalizedStatus.contains("in game");
        return Optional.of(new HytaleFriend(
                username,
                uuid,
                readablePresence(status, online),
                firstIdentityText(node, identity, "avatarUrl", "avatar_url", "avatar", "imageUrl", "image_url"),
                online
        ));
    }

    private static String firstIdentityText(JsonNode node, JsonNode identity, String... fields) {
        String direct = firstText(node, fields);
        if (!direct.isBlank()) {
            return direct;
        }
        String nested = firstText(identity, fields);
        if (!nested.isBlank()) {
            return nested;
        }
        JsonNode nestedIdentity = firstObject(identity, "friend", "profile", "player", "user", "account", "member");
        return firstText(nestedIdentity, fields);
    }

    private static JsonNode firstObject(JsonNode node, String... fields) {
        if (node == null || !node.isObject()) {
            return null;
        }
        for (String field : fields) {
            JsonNode child = node.get(field);
            if (child != null && child.isObject()) {
                return child;
            }
        }
        return null;
    }

    private static String firstText(JsonNode node, String... fields) {
        if (node == null) {
            return "";
        }
        for (String field : fields) {
            JsonNode child = node.get(field);
            String value = textValue(child);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static String textValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isObject()) {
            return firstText(node, "status", "state", "activity", "text", "value", "username",
                    "displayName", "display_name", "name");
        }
        if (node.isArray()) {
            return "";
        }
        return node.asText("").trim();
    }

    private static boolean firstBoolean(JsonNode node, String... fields) {
        if (node == null) {
            return false;
        }
        for (String field : fields) {
            JsonNode child = node.get(field);
            if (child != null && child.isBoolean() && child.asBoolean()) {
                return true;
            }
        }
        return false;
    }

    private static String readablePresence(String rawStatus, boolean online) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return online ? "Online" : "Offline";
        }
        String compact = rawStatus.trim().replace('_', ' ').replace('-', ' ');
        if (compact.equalsIgnoreCase("online")) {
            return "Online";
        }
        if (compact.equalsIgnoreCase("offline")) {
            return "Offline";
        }
        if (compact.equalsIgnoreCase("playing")) {
            return "Playing";
        }
        if (compact.equalsIgnoreCase("in game")) {
            return "In game";
        }
        return compact;
    }

    private static void configureSecureXml(DocumentBuilderFactory factory) throws ParserConfigurationException {
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        setXmlFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setXmlFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        setXmlFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        factory.setExpandEntityReferences(false);
    }

    private static void setXmlFeature(DocumentBuilderFactory factory, String feature, boolean value) throws ParserConfigurationException {
        try {
            factory.setFeature(feature, value);
        } catch (ParserConfigurationException ex) {
            throw ex;
        } catch (Exception ignored) {
            // Some XML implementations do not support every hardening flag.
        }
    }

    private static String childText(Element element, String tagName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || nodes.item(0) == null) {
            return "";
        }
        return nodes.item(0).getTextContent();
    }

    private static String childAttribute(Element element, String tagName, String attributeName) {
        NodeList nodes = element.getElementsByTagName(tagName);
        if (nodes.getLength() == 0 || !(nodes.item(0) instanceof Element child)) {
            return "";
        }
        return child.getAttribute(attributeName);
    }

    private static String cleanRssText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replaceAll("<[^>]+>", " ")
                .replace("&quot;", "\"")
                .replace("&#34;", "\"")
                .replace("&apos;", "'")
                .replace("&#39;", "'")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static Instant parseRssDate(String value) {
        if (value == null || value.isBlank()) {
            return Instant.EPOCH;
        }
        try {
            return ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (DateTimeParseException ex) {
            return Instant.EPOCH;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static final class TokenResponse {
        @JsonProperty("access_token")
        public String accessToken = "";

        @JsonProperty("refresh_token")
        public String refreshToken = "";

        @JsonProperty("expires_in")
        public long expiresIn = 0;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ProfilesResponse {
        public String owner = "";
        public List<ProfileEntry> profiles = List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class ProfileEntry {
        public String uuid = "";
        public String username = "";
        public long playtimeSeconds;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class GameSessionResponse {
        public String sessionToken = "";
        public String identityToken = "";
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class OfficialPatchesResponse {
        public List<OfficialPatchStep> steps = List.of();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class OfficialPatchStep {
        public int from;
        public int to;
        public String pwr = "";
        public String pwrHead = "";
        public String sig = "";
    }
}
