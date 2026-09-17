package net.modtale.launcher.wardrobe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
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
import net.modtale.launcher.hytale.HytaleAuthService;
import net.modtale.launcher.settings.LauncherSettings;

/** Official Hytale account operations. Skin discovery and rendering are local. */
public class WardrobeApiClient {
    private static final URI OFFICIAL = URI.create("https://account-data.hytale.com/");
    private static final int MAX_RESPONSE_BYTES = 4 * 1024 * 1024;
    private final HttpClient http;
    private final HytaleAuthService auth;
    private final URI official;
    private final ObjectMapper mapper = new ObjectMapper();

    public WardrobeApiClient(HytaleAuthService auth) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build(), auth);
    }

    public WardrobeApiClient(HttpClient http, HytaleAuthService auth) {
        this(http, auth, OFFICIAL);
    }

    /** Base URL override for loopback contract tests. */
    public WardrobeApiClient(HttpClient http, HytaleAuthService auth, URI official) {
        this.http = Objects.requireNonNull(http);
        if (http.followRedirects() != HttpClient.Redirect.NEVER) {
            throw new IllegalArgumentException("Wardrobe HTTP client must not follow redirects");
        }
        this.auth = Objects.requireNonNull(auth);
        this.official = base(official, OFFICIAL);
    }

    public record Profile(UUID uuid, String username, String skin) {}

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
        root.properties().forEach(entry -> result.put(entry.getKey(), stringSet(entry.getValue())));
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

    /** Saved definitions remain usable offline; legacy hash-only entries require re-import. */
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
        throw failure("This saved look has no cosmetic definition. Import its outfit file or save it again from your Hytale account.");
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
        if (skin.isTextual() && !skin.asText().isBlank()) skin = readJson(skin.asText());
        return new Profile(uuid, name, skin.isObject() ? skin.toString() : "{}");
    }

    private WardrobeItem item(WardrobeItem.Kind kind, String key, String name, ObjectNode payload) {
        return new WardrobeItem(UUID.nameUUIDFromBytes((kind + ":" + key).getBytes(StandardCharsets.UTF_8)),
                kind, name, false, "", payload.toString());
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
    private static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20"); }
    private static void validUsername(String name) {
        if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) throw new IllegalArgumentException("Username must contain 3–16 letters, digits or underscores");
    }
    private static IllegalStateException failure(String message) { return new IllegalStateException(message); }

}
