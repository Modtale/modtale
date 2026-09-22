package net.modtale.launcher.wardrobe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Offline discovery and portable outfit files. No identity, thumbnails, or usage data are exported. */
public final class LocalSkinLibrary {
    private static final ObjectMapper JSON = com.fasterxml.jackson.databind.json.JsonMapper.builder(
            com.fasterxml.jackson.core.JsonFactory.builder()
                    .streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder()
                            .maxNestingDepth(8).maxStringLength(2048).build())
                    .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build();
    private LocalSkinLibrary() {}

    public static Path assets(net.modtale.launcher.settings.LauncherSettings settings) {
        Path game = settings.getHytaleGamePath().isBlank()
                ? net.modtale.launcher.settings.HytalePathDetector.defaultGameDirectory() : settings.hytaleGameDirectory();
        Path file = game.resolve("Assets.zip");
        if (Files.isRegularFile(file)) return file;
        return game.getFileName() != null && game.getFileName().toString().equalsIgnoreCase("Client") && game.getParent() != null
                ? game.getParent().resolve("Assets.zip") : file;
    }

    /** Saved outfits plus deterministic starting looks from installed, unrestricted cosmetics. */
    public static List<WardrobeItem> discover(Path assets, List<WardrobeItem> saved, String search) throws IOException {
        Map<UUID, WardrobeItem> items = new LinkedHashMap<>();
        for (WardrobeItem item : saved) if (item.kind() == WardrobeItem.Kind.SKIN) items.put(item.id(), item);
        if (Files.isRegularFile(assets)) {
            CosmeticCatalogClient catalog = new CosmeticCatalogClient(assets);
            ObjectNode baseline = catalog.defaultSkin();
            WardrobeItem initial = item("Default outfit", baseline);
            items.putIfAbsent(initial.id(), initial);
            // One change per look keeps each suggestion understandable and editable.
            for (String category : List.of("haircut", "undertop", "pants", "shoes", "overtop", "cape")) {
                for (var option : catalog.browseAssets(category, "", 1, 32).options()) {
                    if (!option.entitlements().isEmpty()) continue;
                    ObjectNode skin = baseline.deepCopy(); skin.put(category, option.id());
                    WardrobeItem look = item(option.label(), skin);
                    items.putIfAbsent(look.id(), look);
                }
            }
        }
        String needle = search == null ? "" : search.strip().toLowerCase(Locale.ROOT);
        return items.values().stream().filter(item -> (item.name() + " " + item.collection()).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    public static WardrobeItem importFile(Path path) throws IOException {
        byte[] bytes;
        try (var input = Files.newInputStream(path)) { bytes = input.readNBytes(WardrobeStore.MAX_PAYLOAD_BYTES + 1); }
        if (bytes.length > WardrobeStore.MAX_PAYLOAD_BYTES) throw new IOException("Outfit file exceeds size limit");
        JsonNode document = JSON.readTree(bytes);
        if (document == null || !document.isObject() || (!document.path("version").isIntegralNumber() || !document.path("version").canConvertToInt() || document.path("version").asInt() != 1))
            throw new IOException("Unsupported outfit file");
        return item("Imported outfit", validatedSkin(document.path("skin")));
    }

    public static void exportFile(Path path, WardrobeItem item) throws IOException {
        JsonNode payload = JSON.readTree(item.payload());
        ObjectNode document = JSON.createObjectNode().put("version", 1);
        document.set("skin", validatedSkin(payload.path("skin")));
        byte[] bytes = JSON.writerWithDefaultPrettyPrinter().writeValueAsBytes(document);
        if (bytes.length > WardrobeStore.MAX_PAYLOAD_BYTES) throw new IOException("Outfit file exceeds size limit");
        Files.write(path, bytes);
    }

    private static ObjectNode validatedSkin(JsonNode skin) throws IOException {
        if (!skin.isObject() || !skin.path("bodyCharacteristic").isTextual() || skin.path("bodyCharacteristic").asText().isBlank())
            throw new IOException("Outfit requires a body definition");
        ObjectNode clean = JSON.createObjectNode();
        for (var entry : skin.properties()) {
            try { CosmeticCatalogClient.assetFile(entry.getKey()); }
            catch (IllegalArgumentException ex) { throw new IOException("Unknown cosmetic slot", ex); }
            JsonNode value = entry.getValue();
            if (!value.isNull() && (!value.isTextual() || !value.asText().matches("[A-Za-z0-9_+\\-]+(?:\\.[A-Za-z0-9_+\\-]+){0,2}")))
                throw new IOException("Invalid cosmetic selection");
            if (value.isTextual() && value.asText().length() > 512) throw new IOException("Cosmetic selection too long");
            clean.set(entry.getKey(), value);
        }
        return clean;
    }

    private static WardrobeItem item(String name, ObjectNode skin) {
        ObjectNode canonical = JSON.createObjectNode();
        skin.properties().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> canonical.set(e.getKey(), e.getValue()));
        ObjectNode payload = JSON.createObjectNode(); payload.set("skin", canonical);
        return new WardrobeItem(UUID.nameUUIDFromBytes(canonical.toString().getBytes(StandardCharsets.UTF_8)),
                WardrobeItem.Kind.SKIN, name, false, "", payload.toString());
    }
}
