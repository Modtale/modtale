package net.modtale.launcher.wardrobe;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipFile;

/**
 * Installed CharacterCreator catalogs are complete for that installed version. The no-argument
 * client uses HyTags' observed-value suggestions, which are capped and never claimed complete.
 * No account/settings access, asset extraction, ownership claims, or generated remote endpoints.
 * HyTags route and twenty wire keys are published by https://hytags.com/cosmetics's own script.
 */
public final class CosmeticCatalogClient {
    private static final int MAX_ENTRY_BYTES = 4 * 1024 * 1024;
    private static final int MAX_OPTIONS = 100_000;
    private static final URI HYTAGS = URI.create("https://hytags.com/");
    private static final String ROOT = "Cosmetics/CharacterCreator/";
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(40).maxStringLength(65536).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private static final Map<String, String> FILES = files();
    private static final Set<String> INHERITED_SKIN = Set.of("face", "ears", "mouth");
    private final Map<String, List<CosmeticOption>> catalog = new LinkedHashMap<>();
    private final Map<String, Map<String, JsonNode>> definitions = new LinkedHashMap<>();
    private final Map<String, JsonNode> gradients = new LinkedHashMap<>();
    private final Map<String, String> labels = new LinkedHashMap<>();
    private final Map<String, JsonNode> animationCatalogs = new LinkedHashMap<>();
    private List<Tag> tags = List.of();
    private final Map<String, String> suggestionCache = new LinkedHashMap<>();
    private final HttpClient http;
    private final URI base;
    private final String source;
    private final boolean local;

    public record Page(List<CosmeticOption> options, int page, int pageSize, int total,
                       boolean hasNext, boolean complete, String source) {
        public Page { options = List.copyOf(options); }
    }

    /** Raw resolved metadata is defensively copied, including on access. Paths are archive-relative. */
    public record ResolvedPart(String category, String selectedId, JsonNode definition) {
        public ResolvedPart { definition = Objects.requireNonNull(definition).deepCopy(); }
        @Override public JsonNode definition() { return definition.deepCopy(); }
    }

    public record AnimationOption(String kind, String id, String label, String animation, String icon,
                                  boolean looping, boolean hideItemInHand, double speed) {}
    public record Tag(String id, String label, int displayOrder) {}

    public CosmeticCatalogClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), HYTAGS);
    }

    /** Loopback base override for contract tests; the production origin is fixed. */
    public CosmeticCatalogClient(HttpClient http, URI base) {
        this.http = Objects.requireNonNull(http);
        this.base = Objects.requireNonNull(base);
        if (http.followRedirects() != HttpClient.Redirect.NEVER) throw new IllegalArgumentException("Redirects are not supported");
        if (!(base.equals(HYTAGS) || ("http".equals(base.getScheme()) && Set.of("localhost", "127.0.0.1", "[::1]").contains(base.getHost())))
                || base.getUserInfo() != null || base.getQuery() != null || base.getFragment() != null || !"/".equals(base.getPath())) {
            throw new IllegalArgumentException("Catalog origin must be HyTags or loopback");
        }
        local = false;
        source = "HyTags observed suggestions (incomplete)";
    }

    /** Reads a bounded snapshot of actual definitions. Malformed or incomplete archives fail closed. */
    public CosmeticCatalogClient(Path assetsZip) throws IOException {
        Objects.requireNonNull(assetsZip, "assetsZip");
        http = null; base = null; local = true;
        source = "Installed catalog: " + assetsZip.toAbsolutePath().normalize();
        try (ZipFile zip = new ZipFile(assetsZip.toFile())) {
            loadLabels(zip);
            for (JsonNode gradient : array(read(zip, ROOT + "GradientSets.json", true), "GradientSets")) {
                String id = token(gradient.path("Id"), "gradient Id");
                JsonNode values = gradient.get("Gradients");
                if (values == null || !values.isObject()) throw new IOException("Invalid gradient set " + id);
                if (gradients.putIfAbsent(id, values.deepCopy()) != null) throw new IOException("Duplicate gradient set " + id);
            }
            for (var file : FILES.entrySet()) {
                var records = new LinkedHashMap<String, JsonNode>();
                var options = new LinkedHashMap<String, CosmeticOption>();
                for (JsonNode record : array(read(zip, ROOT + file.getValue() + ".json", true), file.getKey())) {
                    if (!record.isObject()) throw new IOException("Cosmetic definition must be an object");
                    String id = token(record.path("Id"), "asset Id");
                    if (records.putIfAbsent(id, record.deepCopy()) != null) throw new IOException("Duplicate asset: " + id);
                    enumerate(file.getKey(), record, options);
                }
                definitions.put(file.getKey(), Collections.unmodifiableMap(records));
                catalog.put(file.getKey(), List.copyOf(options.values()));
            }
            for (String kind : List.of("Emotes", "EmotesFace", "EmotesInGame")) {
                JsonNode node = read(zip, ROOT + kind + ".json", false);
                if (node != null) { array(node, kind); animationCatalogs.put(kind, node); }
            }
            JsonNode tagData = read(zip, ROOT + "Tags.json", false);
            if (tagData != null) {
                var availableTags = new ArrayList<Tag>();
                for (JsonNode tag : array(tagData, "Tags")) {
                    String id = tag.path("Id").asText();
                    String key = tag.path("NameKey").asText();
                    if (id.isBlank()) throw new IOException("Tag requires Id");
                    availableTags.add(new Tag(id, labels.getOrDefault(key, humanize(id)), tag.path("DisplayOrder").asInt(0)));
                }
                tags = List.copyOf(availableTags);
            }
        } catch (IllegalArgumentException ex) { throw new IOException("Invalid installed cosmetic catalog", ex); }
    }

    public List<CosmeticCategory> categories() {
        return FILES.keySet().stream().map(key -> new CosmeticCategory(key, switch (key) {
            case "bodyCharacteristic" -> "Body";
            case "haircut" -> "Hairstyles";
            case "headAccessory" -> "Headwear";
            case "overtop" -> "Outer tops";
            case "undertop" -> "Inner tops";
            case "overpants" -> "Outer pants";
            case "earAccessory" -> "Ear accessories";
            case "faceAccessory" -> "Face accessories";
            default -> humanize(key);
        })).toList();
    }

    public boolean complete() { return local; }
    /** Published tag definitions; assignment semantics remain in the raw asset metadata. */
    public List<Tag> tags() { requireLocal(); return tags; }
    public String source() { return source; }
    public static String assetFile(String category) { checkCategory(category); return ROOT + FILES.get(category) + ".json"; }

    /** One-based local pagination. Remote total is -1 because autocomplete is not an exhaustive catalog. */
    public Page browse(String category, String search, int page, int pageSize) throws IOException {
        validatePage(page, pageSize);
        return page(select(category, search), page, pageSize);
    }

    public Page browseAssets(String category, String search, int page, int pageSize) throws IOException {
        validatePage(page, pageSize);
        var unique = new LinkedHashMap<String, CosmeticOption>();
        for (var option : select(category, search)) unique.putIfAbsent(option.assetId(), option);
        return page(List.copyOf(unique.values()), page, pageSize);
    }

    /** All known valid combinations for an asset; local mode is exhaustive for the installed version. */
    public List<CosmeticOption> options(String category, String assetId) throws IOException {
        Objects.requireNonNull(assetId, "assetId");
        return select(category, local ? "" : assetId).stream().filter(option -> option.assetId().equals(assetId)).toList();
    }

    public JsonNode definition(String category, String assetId) {
        requireLocal(); checkCategory(category);
        JsonNode value = definitions.get(category).get(assetId);
        if (value == null) throw new IllegalArgumentException("Unknown cosmetic asset: " + assetId);
        return value.deepCopy();
    }

    /** Base+variant metadata, with concrete Texture, optional GradientTexture, and swatches resolved. */
    public JsonNode resolve(String category, String selectedId) {
        requireLocal(); checkCategory(category);
        CosmeticOption option = catalog.get(category).stream().filter(value -> value.id().equals(selectedId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown cosmetic selection for " + category + ": " + selectedId));
        ObjectNode merged = merged(definitions.get(category).get(option.assetId()), option.variantId());
        JsonNode textures = merged.get("Textures");
        if (textures != null && textures.isObject() && !option.colorId().isEmpty()) {
            JsonNode texture = textures.get(option.colorId());
            merged.put("Texture", texture.path("Texture").asText());
            if (texture.has("BaseColor")) merged.set("BaseColor", texture.get("BaseColor").deepCopy());
        } else if (merged.has("GreyscaleTexture")) {
            merged.put("Texture", merged.path("GreyscaleTexture").asText());
        }
        if (!option.colorId().isEmpty() && merged.has("GradientSet") && textures == null) {
            applyGradient(merged, option.colorId());
        }
        merged.put("Category", category); merged.put("SelectedId", selectedId);
        return merged;
    }

    /** Requires an explicit body. Null slots mean unequipped; unknown categories/selections are rejected. */
    public List<ResolvedPart> resolveComposition(JsonNode skin) {
        requireLocal();
        if (skin == null || !skin.isObject() || !skin.path("bodyCharacteristic").isTextual())
            throw new IllegalArgumentException("Composition requires bodyCharacteristic");
        skin.fieldNames().forEachRemaining(CosmeticCatalogClient::checkCategory);
        ObjectNode body = (ObjectNode) resolve("bodyCharacteristic", skin.path("bodyCharacteristic").asText());
        String bodyColor = catalog.get("bodyCharacteristic").stream().filter(option -> option.id().equals(skin.path("bodyCharacteristic").asText()))
                .findFirst().orElseThrow().colorId();
        var parts = new ArrayList<ResolvedPart>();
        for (String category : FILES.keySet()) {
            JsonNode value = skin.get(category);
            if (value == null || value.isNull()) continue;
            if (!value.isTextual()) throw new IllegalArgumentException("Cosmetic slot must be a string or null: " + category);
            ObjectNode resolved = category.equals("bodyCharacteristic") ? body : (ObjectNode) resolve(category, value.asText());
            if (INHERITED_SKIN.contains(category) && "Skin".equals(resolved.path("GradientSet").asText())) applyGradient(resolved, bodyColor);
            parts.add(new ResolvedPart(category, value.asText(), resolved));
        }
        return List.copyOf(parts);
    }

    /** Explicit local baseline assembled from IsDefaultAsset, never an invented remote/NPC outfit. */
    public ObjectNode defaultSkin() {
        requireLocal();
        ObjectNode skin = JSON.createObjectNode();
        for (String category : FILES.keySet()) {
            skin.putNull(category);
            for (var record : definitions.get(category).values()) {
                if (record.path("IsDefaultAsset").asBoolean(false)) {
                    String id = record.path("Id").asText();
                    catalog.get(category).stream().filter(option -> option.assetId().equals(id)).findFirst()
                            .ifPresent(option -> skin.put(category, option.id()));
                    break;
                }
            }
        }
        if (skin.path("bodyCharacteristic").isNull()) throw new IllegalStateException("Installed catalog has no default body");
        return skin;
    }

    /** Animation catalogs are separate from the twenty cosmetic skin slots; no emote apply key is invented. */
    public List<AnimationOption> animations(String kind) {
        requireLocal();
        JsonNode values = animationCatalogs.get(kind);
        if (values == null) throw new IllegalArgumentException("Unknown/unavailable animation catalog: " + kind);
        var result = new ArrayList<AnimationOption>();
        for (JsonNode value : values) result.add(new AnimationOption(kind, value.path("Id").asText(), label(value),
                value.path("Animation").asText(), value.path("Icon").asText(), value.path("IsLooping").asBoolean(),
                value.path("HideItemInHand").asBoolean(), value.path("Speed").asDouble(1)));
        return List.copyOf(result);
    }

    private void enumerate(String category, JsonNode record, Map<String, CosmeticOption> output) throws IOException {
        JsonNode variants = record.get("Variants");
        if (variants != null && !variants.isObject()) throw new IOException("Invalid Variants");
        List<String> variantIds = variants == null ? List.of("") : fieldNames(variants);
        if (variantIds.isEmpty()) throw new IOException("Empty variant catalog");
        for (String variantId : variantIds) {
            if (!variantId.isEmpty()) token(JSON.getNodeFactory().textNode(variantId), "variant Id");
            ObjectNode merged = merged(record, variantId);
            JsonNode colors = merged.get("Textures");
            if (colors == null && merged.has("GradientSet") && !INHERITED_SKIN.contains(category)) {
                colors = gradients.get(merged.path("GradientSet").asText());
                if (colors == null) throw new IOException("Unknown gradient set " + merged.path("GradientSet").asText());
            }
            if (colors != null && (!colors.isObject() || colors.isEmpty())) throw new IOException("Invalid texture palette");
            for (String colorId : colors == null ? List.of("") : fieldNames(colors)) {
                if (!colorId.isEmpty()) token(JSON.getNodeFactory().textNode(colorId), "color Id");
                String assetId = record.path("Id").asText();
                String id = assetId + (colorId.isEmpty() ? "" : "." + colorId) + (variantId.isEmpty() ? "" : "." + variantId);
                List<String> swatches = colors == null ? List.of() : strings(colors.path(colorId).path("BaseColor"));
                CosmeticOption option = new CosmeticOption(category, id, assetId, colorId, variantId, label(record),
                        swatches, strings(record.path("Entitlements")));
                if (output.putIfAbsent(id, option) != null) throw new IOException("Duplicate selection " + id);
                if (output.size() > MAX_OPTIONS) throw new IOException("Cosmetic catalog exceeds option limit");
            }
        }
    }

    private static ObjectNode merged(JsonNode base, String variantId) {
        ObjectNode result = base.deepCopy();
        if (!variantId.isEmpty()) {
            JsonNode variant = base.path("Variants").get(variantId);
            if (variant == null || !variant.isObject()) throw new IllegalArgumentException("Invalid variant " + variantId);
            variant.properties().forEach(entry -> result.set(entry.getKey(), entry.getValue().deepCopy()));
        }
        return result;
    }

    private void applyGradient(ObjectNode metadata, String colorId) {
        JsonNode set = gradients.get(metadata.path("GradientSet").asText());
        JsonNode gradient = set == null ? null : set.get(colorId);
        if (gradient == null || !gradient.path("Texture").isTextual()) throw new IllegalArgumentException("Invalid gradient color " + colorId);
        metadata.put("GradientTexture", gradient.path("Texture").asText());
        if (gradient.has("BaseColor")) metadata.set("BaseColor", gradient.get("BaseColor").deepCopy());
    }

    private List<CosmeticOption> select(String category, String search) throws IOException {
        checkCategory(category);
        String query = search == null ? "" : WardrobeStore.text(search, "search", 256, true);
        List<CosmeticOption> values = local ? catalog.get(category) : suggestions(category, query);
        String needle = query.toLowerCase(Locale.ROOT);
        return values.stream().filter(option -> (option.id() + " " + option.label()).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    private Page page(List<CosmeticOption> options, int page, int size) {
        long from = (long) (page - 1) * size;
        int start = (int) Math.min(from, options.size()), end = Math.min(start + size, options.size());
        return new Page(options.subList(start, end), page, size, local ? options.size() : -1, end < options.size(), local, source);
    }

    private static void validatePage(int page, int size) {
        if (page < 1 || size < 1 || size > 100) throw new IllegalArgumentException("Page must be positive; page size must be 1–100");
    }

    private synchronized List<CosmeticOption> suggestions(String category, String query) throws IOException {
        String key = category + ":" + query;
        String body = suggestionCache.get(key);
        if (body == null) {
            URI uri = base.resolve("api/cosmetic-values/" + category + "?search=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
            HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("Accept", "application/json").GET().build();
            try {
                var response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (var input = response.body()) {
                    if (response.statusCode() != 200) throw new IOException("Cosmetic lookup returned HTTP " + response.statusCode());
                    byte[] bytes = input.readNBytes(MAX_ENTRY_BYTES + 1);
                    if (bytes.length > MAX_ENTRY_BYTES) throw new IOException("Cosmetic response exceeds size limit");
                    body = new String(bytes, StandardCharsets.UTF_8);
                }
            } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IOException("Cosmetic lookup interrupted", ex); }
        }
        JsonNode root = JSON.readTree(body);
        array(root, "cosmetic suggestions");
        if (root.size() > 1000) throw new IOException("Too many cosmetic suggestions");
        var result = new LinkedHashMap<String, CosmeticOption>();
        for (JsonNode value : root) {
            if (!value.isTextual() || !value.asText().matches("[A-Za-z0-9_+-]+(?:\\.[A-Za-z0-9_+-]+){0,2}")) throw new IOException("Invalid cosmetic selection");
            String[] segments = value.asText().split("\\.");
            result.putIfAbsent(value.asText(), new CosmeticOption(category, value.asText(), segments[0],
                    segments.length > 1 ? segments[1] : "", segments.length > 2 ? segments[2] : "",
                    humanize(segments[0]), List.of(), List.of()));
        }
        if (suggestionCache.size() >= 100) suggestionCache.remove(suggestionCache.keySet().iterator().next());
        suggestionCache.put(key, body);
        return List.copyOf(result.values());
    }

    private void loadLabels(ZipFile zip) throws IOException {
        String prefix = "Common/Languages/en-US/avatarCustomization/";
        var entries = zip.entries();
        int count = 0;
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            if (!entry.getName().startsWith(prefix) || !entry.getName().endsWith(".lang")) continue;
            if (++count > 100) throw new IOException("Too many catalog translation files");
            String namespace = "avatarCustomization." + entry.getName().substring(prefix.length(), entry.getName().length() - 5).replace('/', '.') + ".";
            String body = new String(readBytes(zip, entry.getName()), StandardCharsets.UTF_8);
            for (String line : body.lines().toList()) {
                if (line.stripLeading().startsWith("#")) continue;
                int equals = line.indexOf('=');
                if (equals > 0) labels.put(namespace + line.substring(0, equals).strip(), line.substring(equals + 1).strip());
            }
        }
    }

    private String label(JsonNode record) {
        String key = record.path("Name").asText(record.path("Id").asText());
        return labels.getOrDefault(key, key.startsWith("avatarCustomization.") ? humanize(record.path("Id").asText()) : key);
    }

    private static JsonNode read(ZipFile zip, String name, boolean required) throws IOException {
        if (zip.getEntry(name) == null) {
            if (required) throw new IOException("Missing installed catalog entry: " + name);
            return null;
        }
        return JSON.readTree(readBytes(zip, name));
    }

    private static byte[] readBytes(ZipFile zip, String name) throws IOException {
        var entry = zip.getEntry(name);
        if (entry.getSize() > MAX_ENTRY_BYTES) throw new IOException("Catalog entry too large: " + name);
        try (var input = zip.getInputStream(entry)) {
            byte[] bytes = input.readNBytes(MAX_ENTRY_BYTES + 1);
            if (bytes.length > MAX_ENTRY_BYTES) throw new IOException("Catalog entry too large: " + name);
            return bytes;
        }
    }

    private static JsonNode array(JsonNode value, String description) throws IOException {
        if (value == null || !value.isArray()) throw new IOException("Expected array: " + description);
        return value;
    }
    private static String token(JsonNode node, String description) throws IOException {
        if (!node.isTextual() || node.asText().length() > 128 || !node.asText().matches("[A-Za-z0-9_+-]+"))
            throw new IOException("Invalid " + description);
        return node.asText();
    }
    private static List<String> fieldNames(JsonNode node) { var names = new ArrayList<String>(); node.fieldNames().forEachRemaining(names::add); return names; }
    private static List<String> strings(JsonNode node) throws IOException {
        if (node.isMissingNode()) return List.of();
        if (!node.isArray()) throw new IOException("Expected string array");
        var result = new ArrayList<String>();
        for (JsonNode value : node) {
            if (!value.isTextual() || value.asText().length() > 256) throw new IOException("Invalid string array");
            result.add(value.asText());
        }
        return List.copyOf(result);
    }
    private static String humanize(String id) {
        String text = id.replace('_', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
    private static void checkCategory(String category) { if (!FILES.containsKey(category)) throw new IllegalArgumentException("Unknown cosmetic category: " + category); }
    private void requireLocal() { if (!local) throw new IllegalStateException("Installed Assets.zip is required for complete cosmetic metadata"); }
    private static Map<String, String> files() {
        String[] keys = {"bodyCharacteristic", "underwear", "face", "ears", "mouth", "haircut", "facialHair", "eyebrows", "eyes", "pants", "overpants", "undertop", "overtop", "shoes", "headAccessory", "faceAccessory", "earAccessory", "skinFeature", "gloves", "cape"};
        String[] files = {"BodyCharacteristics", "Underwear", "Faces", "Ears", "Mouths", "Haircuts", "FacialHair", "Eyebrows", "Eyes", "Pants", "Overpants", "Undertops", "Overtops", "Shoes", "HeadAccessory", "FaceAccessory", "EarAccessory", "SkinFeatures", "Gloves", "Capes"};
        var result = new LinkedHashMap<String, String>();
        for (int i = 0; i < keys.length; i++) result.put(keys[i], files[i]);
        return Collections.unmodifiableMap(result);
    }
}
