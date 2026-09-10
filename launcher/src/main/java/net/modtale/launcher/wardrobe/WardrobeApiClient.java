package net.modtale.launcher.wardrobe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import net.modtale.launcher.hytale.HytaleAuthService;
import net.modtale.launcher.settings.LauncherSettings;

/**
 * Public contract evidence: https://hytags.com/api/username/KayNeko and https://hyvatar.io/.
 * Catalogs are HTML compatibility reads, not a documented JSON API. Official UUID reads
 * follow the existing HytaleApiClient contract. Skin slot GET/PUT contracts were recovered
 * from the public HyTags 1.0.0 executable (FetchPlayerSkins/UpdatePlayerSkin).
 * Outfit CRUD and permissions follow the installed official Linux client, SHA-256
 * 8ab9c6f19dbe3bb96266fde4e5dd52df92792517d380d2d97716ececac383c12:
 * route references: GET b83952, POST b84fe5, DELETE b82900, active PUT b8629a, slot PUT b86831;
 * cosmetics b83eef; HTTP method table e4d970 and generated skin JSON serializers a31250/a32180.
 * These recovered contracts are not a public compatibility guarantee.
 */
public class WardrobeApiClient {
    private static final URI HYTAGS = URI.create("https://hytags.com/");
    private static final URI OFFICIAL = URI.create("https://account-data.hytale.com/");
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private final HttpClient http;
    private final HytaleAuthService auth;
    private final URI hytags;
    private final URI official;
    private final ObjectMapper mapper = new ObjectMapper();

    public WardrobeApiClient(HytaleAuthService auth) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(), auth);
    }

    public WardrobeApiClient(HttpClient http, HytaleAuthService auth) {
        this(http, auth, HYTAGS, OFFICIAL);
    }

    /** Base URL overrides are for local contract tests only; auth is never sent to HyTags. */
    public WardrobeApiClient(HttpClient http, HytaleAuthService auth, URI hytags, URI official) {
        this.http = Objects.requireNonNull(http);
        if (http.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("Wardrobe HTTP client must not follow redirects");
        }
        this.auth = Objects.requireNonNull(auth);
        this.hytags = base(hytags, HYTAGS);
        this.official = base(official, OFFICIAL);
    }

    public record Profile(UUID uuid, String username, String skin) {}

    public static final class MissingArchivedSkinException extends IllegalStateException {
        private MissingArchivedSkinException() {
            super("Profile has no current archived skin identifier");
        }
    }

    public record SkinSlot(String id, String name, String skinData) {}
    public record SkinSlots(String activeId, int max, List<SkinSlot> slots) {
        public SkinSlots { slots = List.copyOf(slots); }
    }

    /** Official game-client contract: profile is scoped by the game-session bearer token. */
    public SkinSlots slots(LauncherSettings settings) {
        UUID profile = selectedProfile(settings);
        return parseSlots(profileJson(settings, profile, "player-skins").body());
    }

    /**
     * PlayerSkinProperty keys (haircut, cape, headAccessory, etc.) map to base catalog Ids,
     * not serialized color/variant selections. Missing membership is not an unlock.
     * Evidence: category parser a2e450; resolved catalog ID membership check b8138e-b813d6.
     * No permission cache: callers must scope any cache to the linked account/profile.
     */
    public Map<String, Set<String>> unlockedCosmetics(LauncherSettings settings) {
        UUID profile = selectedProfile(settings);
        JsonNode root = profileJson(settings, profile, "my-account/cosmetics").body();
        requireSelectedProfile(settings, profile.toString());
        if (root == null || !root.isObject()) throw failure("Invalid unlocked cosmetics response");
        Map<String, Set<String>> result = new LinkedHashMap<>();
        root.fields().forEachRemaining(entry -> result.put(entry.getKey(), stringSet(entry.getValue())));
        return java.util.Collections.unmodifiableMap(result);
    }

    private static Set<String> stringSet(JsonNode values) {
        if (values != null && values.isNull()) return Set.of();
        if (values == null || !values.isArray()) throw failure("Official cosmetic permissions must be arrays");
        Set<String> result = new LinkedHashSet<>();
        for (JsonNode value : values) {
            if (!value.isTextual() || value.asText().isBlank()) throw failure("Invalid official cosmetic permission");
            result.add(value.asText());
        }
        return java.util.Collections.unmodifiableSet(result);
    }

    private SkinSlots parseSlots(JsonNode root) {
        if (root == null || !root.isObject() || !root.path("skins").isArray()
                || !root.path("maxSkins").isIntegralNumber() || !root.path("maxSkins").canConvertToInt()
                || root.path("maxSkins").intValue() < 0) throw failure("Invalid official outfit slots response");
        JsonNode active = root.get("activeSkin");
        if (active == null || (!active.isNull() && !active.isTextual())) throw failure("Invalid active outfit slot");
        String activeId = active.isNull() ? "" : active.asText();
        List<SkinSlot> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (JsonNode slot : root.path("skins")) {
            String id = slot.path("id").asText("");
            validSlotId(id);
            if (!ids.add(id) || !slot.path("name").isTextual() || !slot.path("skinData").isTextual()) {
                throw failure("Invalid official outfit slot");
            }
            String definition = slot.path("skinData").asText();
            requireSkin(readJson(definition));
            result.add(new SkinSlot(id, slot.path("name").asText(), definition));
        }
        if (!activeId.isEmpty() && !ids.contains(activeId)) throw failure("Active outfit slot is missing from response");
        return new SkinSlots(activeId, root.path("maxSkins").intValue(), result);
    }

    private static void validSlotId(String id) {
        if (id == null || !UUID.fromString(id).toString().equalsIgnoreCase(id)) {
            throw new IllegalArgumentException("Invalid official outfit slot UUID");
        }
    }

    private static UUID selectedProfile(LauncherSettings settings) {
        if (settings == null || settings.getHytaleAuthSession() == null) throw failure("Hytale account unavailable");
        return UUID.fromString(settings.getHytaleAuthSession().getUuid());
    }

    private String profileSession(LauncherSettings settings, UUID profile) {
        Objects.requireNonNull(profile, "Expected profile UUID");
        requireSelectedProfile(settings, profile.toString());
        String token = auth.freshSessionToken(settings);
        requireSelectedProfile(settings, profile.toString());
        if (token == null || token.isBlank()) throw failure("Official authentication returned an empty session token");
        return token;
    }

    private record ProfileResponse(JsonNode body, String token) {}

    private ProfileResponse profileJson(LauncherSettings settings, UUID profile, String path) {
        String token = profileSession(settings, profile);
        JsonNode body;
        try {
            body = json(official.resolve(path), token);
        } catch (RejectedSessionException ex) {
            requireSelectedProfile(settings, profile.toString());
            token = auth.renewRejectedSessionToken(settings, profile.toString(), token);
            requireSelectedProfile(settings, profile.toString());
            body = json(official.resolve(path), token);
        }
        requireSelectedProfile(settings, profile.toString());
        return new ProfileResponse(body, token);
    }

    /** Page numbers are one-based. Sort must match a value/label actually published in the form. */
    public List<WardrobeItem> browseSkins(int page, String sort) {
        if (page < 1) throw new IllegalArgumentException("Page must be at least 1");
        URI uri = hytags.resolve("skins?page=" + page);
        Page document = page(uri);
        if (sort != null && !sort.isBlank() && !sort.equalsIgnoreCase("default")) {
            String query = document.sortQuery(sort);
            document = page(URI.create(uri + "&" + query));
        }
        Map<String, WardrobeItem> result = new LinkedHashMap<>();
        for (Link link : document.links) {
            URI target = localLink(uri, link.href, "/skin/[a-fA-F0-9]{32}");
            if (target == null) continue;
            String hash = target.getPath().substring("/skin/".length()).toLowerCase(java.util.Locale.ROOT);
            ObjectNode payload = mapper.createObjectNode().put("skinId", hash);
            payload.put("thumbnailUrl", "https://hyvatar.io/render/full/NPC?size=256&skin_id=" + hash);
            result.putIfAbsent(hash, item(WardrobeItem.Kind.SKIN, hash, "Skin #" + hash.substring(0, 8), payload));
        }
        if (result.isEmpty() && !document.text.toString().contains("Skin archive")) {
            throw failure("HyTags skin catalog format is unavailable or changed");
        }
        return List.copyOf(result.values());
    }

    public Profile profile(String username) {
        validUsername(username);
        JsonNode root = json(hytags.resolve("api/username/" + encode(username)), null);
        Profile profile = parseProfile(root);
        if (!profile.username().equalsIgnoreCase(username)) throw failure("HyTags returned a different username");
        return profile;
    }

    /** HyTags' tracked skin is not guaranteed to be the account's current official skin. */
    public WardrobeItem lookupSkin(String username) {
        Profile profile = profile(username);
        URI uri = hytags.resolve("username/" + encode(profile.username()));
        Page document = page(uri);
        for (Link link : document.links) {
            if (!link.current) continue;
            URI target = localLink(uri, link.href, "/skin/[a-fA-F0-9]{32}");
            if (target == null) continue;
            WardrobeItem saved = lookupSkinHash(target.getPath().substring("/skin/".length()));
            ObjectNode payload = (ObjectNode) readJson(saved.payload());
            if (!payload.path("skin").equals(readJson(profile.skin()))) {
                throw failure("Profile changed while resolving its saved skin; please retry");
            }
            payload.put("username", profile.username()).put("playerUuid", profile.uuid().toString());
            return new WardrobeItem(saved.id(), saved.kind(), profile.username(), false, "", payload.toString());
        }
        throw new MissingArchivedSkinException();
    }

    public WardrobeItem lookupSkinHash(String hash) {
        if (hash == null || !hash.matches("[a-fA-F0-9]{32}")) throw new IllegalArgumentException("Invalid skin hash");
        hash = hash.toLowerCase(java.util.Locale.ROOT);
        JsonNode skin = json(hytags.resolve("api/skin/" + hash), null);
        requireSkin(skin);
        ObjectNode payload = mapper.createObjectNode().put("skinId", hash);
        payload.set("skin", skin);
        if (skin.path("cape").isTextual()) payload.put("cape", skin.path("cape").asText());
        payload.put("thumbnailUrl", "https://hyvatar.io/render/full/NPC?size=256&skin_id=" + hash);
        return item(WardrobeItem.Kind.SKIN, hash, "Skin #" + hash.substring(0, 8), payload);
    }

    /** Resolves hash-only catalog entries before persisting; never follows a moving username. */
    public WardrobeItem hydrate(WardrobeItem item) {
        Objects.requireNonNull(item);
        JsonNode original = readJson(item.payload());
        if (item.kind() == WardrobeItem.Kind.CAPE) {
            JsonNode cape = original.get("cape");
            if (cape == null || (!cape.isNull() && (!cape.isTextual() || cape.asText().isBlank()))) {
                throw new IllegalArgumentException("Cape item has no cosmetic identifier");
            }
            return item;
        }
        if (original.path("skin").isObject()) {
            requireSkin(original.path("skin"));
            return item;
        }
        WardrobeItem resolved = lookupSkinHash(original.path("skinId").asText(""));
        ObjectNode payload = (ObjectNode) readJson(resolved.payload());
        for (String key : List.of("username", "playerUuid")) {
            if (original.path(key).isTextual()) payload.set(key, original.path(key));
        }
        return new WardrobeItem(item.id(), item.kind(), item.name(), item.favorite(), item.collection(), payload.toString());
    }

    /** Exact active official slot contents, suitable for restore; no moving username preview. */
    public WardrobeItem currentSkin(LauncherSettings settings) {
        ActiveSkin slot = activeSkin(settings);
        ObjectNode payload = mapper.createObjectNode().put("playerUuid", slot.profileId());
        payload.set("skin", slot.skin());
        return item(WardrobeItem.Kind.SKIN, slot.id() + ":" + slot.skin(), slot.name(), payload);
    }

    public Profile profile(UUID uuid, LauncherSettings settings) {
        Objects.requireNonNull(uuid);
        Profile profile = parseProfile(json(official.resolve("profile/uuid/" + uuid), auth.freshSessionToken(settings)));
        if (!uuid.equals(profile.uuid())) throw failure("Official profile response does not match requested UUID");
        return profile;
    }

    public void apply(WardrobeItem item, LauncherSettings settings) {
        if (settings == null || settings.getHytaleAuthSession() == null) throw failure("Hytale account unavailable");
        apply(item, settings, UUID.fromString(settings.getHytaleAuthSession().getUuid()));
    }

    /** Pass the UUID captured before creating a backup to protect the entire backup/apply flow. */
    public void apply(WardrobeItem item, LauncherSettings settings, UUID expectedProfile) {
        Objects.requireNonNull(item);
        Objects.requireNonNull(settings);
        Objects.requireNonNull(expectedProfile);
        requireSelectedProfile(settings, expectedProfile.toString());
        JsonNode payload = readJson(item.payload());
        JsonNode definition = null;
        if (item.kind() == WardrobeItem.Kind.SKIN) {
            definition = payload.path("skin");
            if (!definition.isObject() && payload.path("skinId").isTextual()) {
                definition = readJson(lookupSkinHash(payload.path("skinId").asText()).payload()).path("skin");
            }
            requireSkin(definition);
        }
        ActiveSkin slot = activeSkin(settings);
        if (!expectedProfile.toString().equals(slot.profileId())) throw failure("Selected Hytale profile changed before applying skin");
        if (item.kind() == WardrobeItem.Kind.CAPE) {
            JsonNode cape = payload.get("cape");
            if (cape == null || (!cape.isNull() && (!cape.isTextual() || cape.asText().isBlank()))) {
                throw new IllegalArgumentException("Cape item has no cosmetic identifier");
            }
            ObjectNode updated = slot.skin().deepCopy();
            updated.set("cape", cape);
            definition = updated;
        }
        ObjectNode body = mapper.createObjectNode().put("name", slot.name()).put("skinData", definition.toString());
        requireSelectedProfile(settings, slot.profileId());
        HttpRequest request = HttpRequest.newBuilder(official.resolve("player-skins/" + encode(slot.id())))
                .timeout(Duration.ofSeconds(30)).header("Authorization", "Bearer " + slot.token())
                .header("Content-Type", "application/json").header("Accept", "application/json").header("User-Agent", "ModtaleLauncher/1.0")
                .PUT(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
        send(request, null, true);
    }

    private record ActiveSkin(String profileId, String id, String name, ObjectNode skin, String token) {}

    private ActiveSkin activeSkin(LauncherSettings settings) {
        UUID profile = selectedProfile(settings);
        ProfileResponse response = profileJson(settings, profile, "player-skins");
        SkinSlots slots = parseSlots(response.body());
        SkinSlot active = slots.slots().stream().filter(slot -> slot.id().equals(slots.activeId())).findFirst()
                .orElseThrow(() -> failure("Official account has no active skin slot"));
        return new ActiveSkin(profile.toString(), active.id(), active.name(), (ObjectNode) readJson(active.skinData()), response.token());
    }

    private static void requireSelectedProfile(LauncherSettings settings, String uuid) {
        if (settings.getHytaleAuthSession() == null || !uuid.equals(settings.getHytaleAuthSession().getUuid())) {
            throw failure("Selected Hytale profile changed before applying skin");
        }
    }

    private static void requireSkin(JsonNode skin) {
        if (skin == null || !skin.isObject() || skin.isEmpty() || !skin.has("bodyCharacteristic")) {
            throw failure("No complete cosmetic definition is available");
        }
        skin.elements().forEachRemaining(value -> {
            if (!value.isNull() && !value.isTextual()) throw failure("Cosmetic values must be strings or null");
        });
    }

    private Profile parseProfile(JsonNode root) {
        if (root == null || !root.isObject()) throw failure("Profile response is not an object");
        if (root.has("profile") && root.get("profile").isObject()) root = root.get("profile");
        String name = root.path("username").asText("");
        validUsername(name);
        UUID uuid;
        try {
            String value = root.path("uuid").asText("");
            uuid = UUID.fromString(value);
            if (!uuid.toString().equalsIgnoreCase(value)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) { throw failure("Profile response has no valid UUID"); }
        JsonNode skin = root.path("skin");
        return new Profile(uuid, name, skin.isObject() ? skin.toString() : "{}");
    }

    private WardrobeItem item(WardrobeItem.Kind kind, String key, String name, ObjectNode payload) {
        return new WardrobeItem(UUID.nameUUIDFromBytes((kind + ":" + key).getBytes(StandardCharsets.UTF_8)),
                kind, name, false, "", payload.toString());
    }

    private Page page(URI uri) {
        Page page = new Page();
        try { new ParserDelegator().parse(new StringReader(get(uri, null, "text/html")), page, true); }
        catch (IOException e) { throw failure("Could not parse HyTags page"); }
        return page;
    }

    private JsonNode json(URI uri, String token) { return readJson(get(uri, token, "application/json")); }
    private JsonNode readJson(String text) {
        try { return mapper.readTree(text); }
        catch (IOException e) { throw failure("Invalid profile JSON response"); }
    }

    private String get(URI uri, String token, String accept) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("User-Agent", "ModtaleLauncher/1.0")
                .header("Accept", accept).GET();
        if (token != null) {
            if (token.isBlank()) throw failure("Official authentication returned an empty session token");
            request.header("Authorization", "Bearer " + token);
        }
        return send(request.build(), accept, false);
    }

    private String send(HttpRequest request, String accept, boolean write) {
        try {
            java.io.ByteArrayOutputStream body = new java.io.ByteArrayOutputStream();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.ofByteArrayConsumer(chunk -> chunk.ifPresent(bytes -> {
                if ((long) body.size() + bytes.length > MAX_RESPONSE_BYTES) throw failure("Wardrobe response is too large");
                body.writeBytes(bytes);
            })));
            {
                if (write ? response.statusCode() < 200 || response.statusCode() >= 300 : response.statusCode() != 200) {
                    if (!write && (response.statusCode() == 401 || response.statusCode() == 403)
                            && body.toString(StandardCharsets.UTF_8).trim().equals("invalid token")) {
                        throw new RejectedSessionException(response.statusCode());
                    }
                    throw failure("Wardrobe request failed (HTTP " + response.statusCode() + ")");
                }
                if (write) return "";
                String type = response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].trim();
                if (!type.equalsIgnoreCase(accept)) throw failure("Unexpected wardrobe response content type");
                byte[] bytes = body.toByteArray();
                if (bytes.length > MAX_RESPONSE_BYTES) throw failure("Wardrobe response is too large");
                return new String(bytes, StandardCharsets.UTF_8);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failure("Wardrobe request interrupted");
        } catch (IOException e) { throw failure("Wardrobe service could not be reached"); }
    }

    private static final class RejectedSessionException extends IllegalStateException {
        private RejectedSessionException(int status) {
            super("Hytale rejected the wardrobe session (HTTP " + status + ")");
        }
    }

    private static URI base(URI uri, URI production) {
        Objects.requireNonNull(uri);
        boolean local = "http".equals(uri.getScheme()) && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()));
        if ((!uri.equals(production) && !local) || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || !"/".equals(uri.getPath())) throw new IllegalArgumentException("Use the production origin or a loopback test server");
        return uri;
    }
    private static URI localLink(URI base, String href, String pathPattern) {
        try {
            URI uri = base.resolve(href);
            if (!Objects.equals(uri.getScheme(), base.getScheme()) || !Objects.equals(uri.getAuthority(), base.getAuthority())
                    || uri.getUserInfo() != null || !uri.getPath().matches(pathPattern)) return null;
            return uri;
        } catch (IllegalArgumentException e) { return null; }
    }
    private static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static void validUsername(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) throw new IllegalArgumentException("Username must contain 3–16 letters, digits or underscores");
    }
    private static IllegalStateException failure(String message) { return new IllegalStateException(message); }

    private static class Link {
        final String href;
        boolean current;
        final StringBuilder text = new StringBuilder();
        Link(String href) { this.href = href; }
    }
    private static class Page extends HTMLEditorKit.ParserCallback {
        final List<Link> links = new ArrayList<>();
        final StringBuilder text = new StringBuilder();
        final List<String[]> options = new ArrayList<>();
        Link current;
        String select;
        String option;
        StringBuilder label;
        @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attrs, int pos) {
            if (tag == HTML.Tag.A && attrs.getAttribute(HTML.Attribute.HREF) != null) {
                current = new Link(attrs.getAttribute(HTML.Attribute.HREF).toString()); links.add(current);
                String classes = String.valueOf(attrs.getAttribute(HTML.Attribute.CLASS));
                current.current = List.of(classes.split("\\s+")).contains("is-current");
            }
            if (tag == HTML.Tag.SELECT) select = (String) attrs.getAttribute(HTML.Attribute.NAME);
            if (tag == HTML.Tag.OPTION) { option = (String) attrs.getAttribute(HTML.Attribute.VALUE); label = new StringBuilder(); }
        }
        @Override public void handleEndTag(HTML.Tag tag, int pos) {
            if (tag == HTML.Tag.A) current = null;
            if (tag == HTML.Tag.OPTION && select != null && option != null) options.add(new String[]{select, option, label.toString().trim()});
            if (tag == HTML.Tag.OPTION) label = null;
            if (tag == HTML.Tag.SELECT) select = null;
        }
        @Override public void handleText(char[] data, int pos) {
            text.append(data).append(' ');
            if (current != null) current.text.append(data);
            if (label != null) label.append(data);
        }
        String sortQuery(String sort) {
            for (String[] entry : options) {
                if (entry[0].toLowerCase(java.util.Locale.ROOT).contains("sort") && (entry[1].equalsIgnoreCase(sort) || entry[2].equalsIgnoreCase(sort))) {
                    return encode(entry[0]) + "=" + encode(entry[1]);
                }
            }
            throw new IllegalArgumentException("Sort is not published by the HyTags catalog: " + sort);
        }
    }
}
