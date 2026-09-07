package net.modtale.launcher.wardrobe;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import net.modtale.launcher.io.AtomicJsonFile;

/**
 * Local wardrobe. Share one instance per directory; methods are thread safe.
 * All writes validate the complete candidate and replace disk state before publishing memory state.
 */
public final class WardrobeStore {
    public static final int MAX_ITEMS = 1_000;
    public static final int MAX_PAYLOAD_BYTES = 64 * 1024;
    public static final int MAX_DOCUMENT_BYTES = 8 * 1024 * 1024;
    private static final int VERSION = 1;
    private static final JsonMapper JSON = JsonMapper.builder(JsonFactory.builder()
            .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(32)
                    .maxStringLength(MAX_PAYLOAD_BYTES * 2).maxNumberLength(128).build())
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .build();

    private final Path path;
    private Document state;

    public WardrobeStore(Path launcherDirectory) throws IOException {
        path = Objects.requireNonNull(launcherDirectory, "launcherDirectory").resolve("wardrobe.json");
        if (Files.notExists(path)) {
            state = new Document(VERSION, List.of());
        } else {
            try (var input = Files.newInputStream(path)) {
                byte[] bytes = input.readNBytes(MAX_DOCUMENT_BYTES + 1);
                if (bytes.length > MAX_DOCUMENT_BYTES) throw new IOException("Wardrobe file exceeds size limit");
                state = JSON.readValue(bytes, Document.class);
                validate(state);
            } catch (IllegalArgumentException | NullPointerException ex) {
                throw new IOException("Invalid wardrobe file", ex);
            }
        }
    }

    public synchronized List<WardrobeItem> items() { return state.items(); }

    /** Case-insensitive substring search over names and collections; null filters mean all. */
    public synchronized List<WardrobeItem> search(String query, WardrobeItem.Kind kind,
                                                  boolean favoritesOnly, String collection) {
        String needle = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        String group = collection == null ? null : collection.strip();
        return state.items().stream()
                .filter(item -> kind == null || item.kind() == kind)
                .filter(item -> !favoritesOnly || item.favorite())
                .filter(item -> group == null || item.collection().equalsIgnoreCase(group))
                .filter(item -> item.name().toLowerCase(Locale.ROOT).contains(needle)
                        || item.collection().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    public synchronized void saveItem(WardrobeItem item) throws IOException {
        Objects.requireNonNull(item, "item");
        var items = new ArrayList<>(state.items());
        int index = -1;
        for (int i = 0; i < items.size(); i++) if (items.get(i).id().equals(item.id())) index = i;
        if (index < 0) items.add(item); else items.set(index, item);
        save(new Document(VERSION, items));
    }

    public synchronized void removeItem(UUID id) throws IOException {
        Objects.requireNonNull(id, "id");
        save(new Document(VERSION, state.items().stream().filter(item -> !item.id().equals(id)).toList()));
    }

    /** Versioned JSON item exchange, with the same byte and item limits as imports. */
    public synchronized String exportItems() throws IOException {
        byte[] bytes = JSON.writeValueAsBytes(new ItemExchange(VERSION, state.items()));
        checkSize(bytes.length);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    /** Atomically exports the same versioned item document accepted by importItems. */
    public synchronized void exportItems(Path destination) throws IOException {
        Objects.requireNonNull(destination, "destination");
        if (destination.toAbsolutePath().normalize().equals(path.toAbsolutePath().normalize())
                || (Files.exists(destination) && Files.exists(path) && Files.isSameFile(destination, path))) {
            throw new IllegalArgumentException("Export destination cannot be the wardrobe state file");
        }
        var exchange = new ItemExchange(VERSION, state.items());
        checkSize(JSON.writeValueAsBytes(exchange).length);
        AtomicJsonFile.write(destination, JSON.writer(), exchange);
    }

    public synchronized void importItems(Path source) throws IOException {
        Objects.requireNonNull(source, "source");
        try (var input = Files.newInputStream(source)) {
            byte[] bytes = input.readNBytes(MAX_DOCUMENT_BYTES + 1);
            checkSize(bytes.length);
            // Decode strictly so malformed UTF-8 cannot silently change imported cosmetic data.
            String json = StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
            importItems(json);
        }
    }

    /** Merge atomically. Identical UUID/content is idempotent; conflicting content is rejected. */
    public synchronized void importItems(String json) throws IOException {
        if (json == null) throw new IllegalArgumentException("json is required");
        if (json.length() > MAX_DOCUMENT_BYTES) throw new IOException("Import exceeds size limit");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        checkSize(bytes.length);
        try {
            ItemExchange exchange = JSON.readValue(bytes, ItemExchange.class);
            if (exchange == null || exchange.version() != VERSION) throw new IllegalArgumentException("Unsupported import version");
            validateItems(exchange.items());
            var merged = new ArrayList<>(state.items());
            for (WardrobeItem item : exchange.items()) {
                var existing = merged.stream().filter(value -> value.id().equals(item.id())).findFirst();
                if (existing.isEmpty()) merged.add(item);
                else if (!existing.get().equals(item)) throw new IllegalArgumentException("Conflicting item UUID: " + item.id());
            }
            save(new Document(VERSION, merged));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IOException("Invalid wardrobe import", ex);
        }
    }

    private void save(Document candidate) throws IOException {
        validate(candidate);
        checkSize(JSON.writeValueAsBytes(candidate).length);
        AtomicJsonFile.write(path, JSON.writer(), candidate);
        state = candidate;
    }

    private static void validate(Document document) {
        if (document == null || document.version() != VERSION) throw new IllegalArgumentException("Unsupported wardrobe version");
        validateItems(document.items());
    }

    private static void validateItems(List<WardrobeItem> items) {
        Objects.requireNonNull(items, "items");
        if (items.size() > MAX_ITEMS) throw new IllegalArgumentException("Too many wardrobe items");
        var ids = new HashSet<UUID>();
        for (var item : items) if (!ids.add(item.id())) throw new IllegalArgumentException("Duplicate item UUID");
    }

    static String text(String value, String field, int limit, boolean allowEmpty) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
        if (value.length() > limit || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " exceeds limit or contains control characters");
        }
        value = value.strip();
        if (!allowEmpty && value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value;
    }

    static String validatePayload(String payload) {
        if (payload == null || payload.length() > MAX_PAYLOAD_BYTES
                || payload.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Payload is required and must fit within " + MAX_PAYLOAD_BYTES + " bytes");
        }
        try {
            var node = JSON.readTree(payload);
            if (node == null || !node.isObject()) throw new IllegalArgumentException("Payload must be a JSON object");
        } catch (IOException ex) {
            throw new IllegalArgumentException("Invalid JSON payload", ex);
        }
        return payload;
    }

    private static void checkSize(int size) throws IOException {
        if (size > MAX_DOCUMENT_BYTES) throw new IOException("Wardrobe document exceeds size limit");
    }

    private record ItemExchange(int version, List<WardrobeItem> items) {
        private ItemExchange { items = List.copyOf(items); }
    }

    // Read the retired fields only for compatibility with existing local files; never write them.
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties({"watches", "alerts"})
    private record Document(int version, List<WardrobeItem> items) {
        private Document { items = List.copyOf(items); }
    }
}
