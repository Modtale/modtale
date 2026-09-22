package net.modtale.launcher.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import net.modtale.launcher.config.HytaleConfigFiles.Snapshot;

public final class ConfigSettingsDocument {
    private final ObjectMapper mapper;
    private final JsonNode root;
    private JsonNode baseline;
    private Snapshot snapshot;
    private final List<Setting> settings = new ArrayList<>();

    public ConfigSettingsDocument(Snapshot snapshot) throws IOException {
        this(snapshot, false);
    }

    public ConfigSettingsDocument(Snapshot snapshot, boolean worldSettings) throws IOException {
        this.snapshot = snapshot;
        String path = snapshot.file().path().toString().toLowerCase(Locale.ROOT);
        mapper = path.endsWith(".json") ? new ObjectMapper()
                : path.endsWith(".yaml") || path.endsWith(".yml") ? new YAMLMapper()
                : path.endsWith(".toml") ? new TomlMapper() : null;
        if (mapper == null) throw new IOException("Unsupported settings format");
        mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        mapper.enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        mapper.enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS);
        mapper.enable(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS);
        String text = snapshot.text().startsWith("\uFEFF") ? snapshot.text().substring(1) : snapshot.text();
        root = mapper.readTree(text);
        if (root == null || !root.isObject()) throw new IOException("Settings must be a mapping");
        baseline = root.deepCopy();
        if (worldSettings) {
            for (var spec : WorldSettingsSchema.SETTINGS) {
                JsonNode current = root.at(spec.pointer());
                JsonNode parent = root.at(spec.pointer().substring(0, spec.pointer().lastIndexOf('/')));
                if (!parent.isMissingNode() && !parent.isNull() && !parent.isObject())
                    throw new IOException("Invalid world settings section");
                if (!current.isMissingNode() && !current.isNull() && !spec.accepts(current))
                    throw new IOException("Invalid world setting: " + spec.name());
                settings.add(new Setting(spec.pointer(), spec.name(),
                        snapshot.file().path().getParent().getFileName().toString(), spec.type(), spec));
            }
        } else collect(root, "", "", 0);
    }

    private void collect(JsonNode node, String pointer, String context, int depth) throws IOException {
        if (depth > 16 || settings.size() > 1500) throw new IOException("Settings are too large for this form");
        if (node.isObject()) {
            var fields = node.properties().iterator();
            while (fields.hasNext()) {
                if (settings.size() >= 1500) throw new IOException("Settings are too large for this form");
                var field = fields.next();
                String name = label(field.getKey());
                String path = pointer + "/" + field.getKey().replace("~", "~0").replace("/", "~1");
                if (field.getValue().isContainerNode()) collect(field.getValue(), path, context.isEmpty() ? name : context + " · " + name, depth + 1);
                else if (!field.getValue().isNull()) settings.add(new Setting(path, name, context, field.getValue()));
            }
        } else if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) {
                JsonNode child = node.get(i);
                if (child.isContainerNode()) collect(child, pointer + "/" + i, context + " · Item " + (i + 1), depth + 1);
                else if (!child.isNull()) settings.add(new Setting(pointer + "/" + i, "Item " + (i + 1), context, child));
            }
        }
    }

    public static String label(String key) {
        String text = key.replaceAll("([A-Z]+)([A-Z][a-z])", "$1 $2")
                .replaceAll("([a-z0-9])([A-Z])", "$1 $2").replace('_', ' ').replace('-', ' ').trim();
        if (text.isEmpty()) return "Setting";
        return text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }

    public List<Setting> settings() { return List.copyOf(settings); }
    public Snapshot snapshot() { return snapshot; }
    public boolean dirty() { return !root.equals(baseline); }
    public String serialize() throws IOException {
        if (!dirty()) return snapshot.text();
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root);
    }
    public void saved(Snapshot saved) { snapshot = saved; baseline = root.deepCopy(); }
    public void reset() {
        ((ObjectNode) root).removeAll();
        ((ObjectNode) root).setAll((ObjectNode) baseline.deepCopy());
    }

    public final class Setting {
        private final String pointer;
        private final String name;
        private final String context;
        private final JsonNode original;
        private final WorldSettingsSchema.Spec spec;
        Setting(String pointer, String name, String context, JsonNode original) {
            this(pointer, name, context, original, null);
        }
        Setting(String pointer, String name, String context, JsonNode original, WorldSettingsSchema.Spec spec) {
            this.pointer = pointer; this.name = name; this.context = context; this.original = original; this.spec = spec;
        }
        public String name() {
            if (spec != null) return name;
            String readable = toggle() ? name.replaceFirst("^Enable ", "").replaceFirst(" Config$", "") : name;
            String[] words = readable.split(" ");
            for (int i = 1; i < words.length; i++) {
                if (!words[i].equals(words[i].toUpperCase(Locale.ROOT))) words[i] = words[i].toLowerCase(Locale.ROOT);
            }
            return String.join(" ", words).replace("Msgs", "messages").replace("msgs", "messages");
        }
        public String context() { return context; }
        public boolean toggle() { return original.isBoolean(); }
        public boolean number() { return original.isNumber(); }
        public String value() {
            JsonNode value = root.at(pointer);
            return spec != null && (value.isMissingNode() || value.isNull()) ? spec.fallback() : value.asText();
        }
        public List<String> choices() { return spec == null ? List.of() : spec.choices(); }
        public boolean readOnly() { return spec != null && spec.readOnly(); }
        public String hint() { return spec == null ? "" : spec.hint(); }
        public String category() {
            if (spec != null) return context;
            if (!context.isBlank()) return context.split(" · ")[0];
            String key = name.toLowerCase(Locale.ROOT);
            if (key.contains("death") || key.contains("xp loss") || key.contains("level down") || key.contains("levels lost")) return "Death & penalties";
            if (key.contains("sound") || key.contains("notification") || key.contains("chat") || key.contains("hud") || key.contains("titles")) return "Display & sounds";
            if (key.contains("stat") || key.contains("health") || key.contains("stamina") || key.contains("mana")) return "Attributes";
            if (key.contains("xp") || key.contains("level") || key.contains("reward")) return "Progression";
            return "General";
        }
        public void set(String value) {
            if (spec != null) {
                if (spec.readOnly()) throw new IllegalArgumentException("This setting is read-only.");
                JsonNode before = baseline.at(pointer);
                String baselineValue = before.isMissingNode() || before.isNull() ? spec.fallback() : before.asText();
                if (value.equals(baselineValue)) { assign(before); return; }
                spec.validate(value);
                if (spec.optional() && value.isBlank()) { assign(MissingNode.getInstance()); return; }
            }
            JsonNode replacement;
            if (toggle()) {
                if (!value.equals("true") && !value.equals("false")) throw new IllegalArgumentException("Choose on or off.");
                replacement = BooleanNode.valueOf(Boolean.parseBoolean(value));
            } else if (original.isIntegralNumber()) {
                try { replacement = BigIntegerNode.valueOf(new BigInteger(value.trim())); }
                catch (NumberFormatException ex) { throw new IllegalArgumentException("Enter a whole number."); }
            } else if (number()) {
                try { replacement = DecimalNode.valueOf(new BigDecimal(value.trim())); }
                catch (NumberFormatException ex) { throw new IllegalArgumentException("Enter a number."); }
            } else replacement = TextNode.valueOf(value);
            assign(replacement);
        }
        private void assign(JsonNode value) {
            int slash = pointer.lastIndexOf('/');
            JsonNode parent = root.at(pointer.substring(0, slash));
            if (spec != null && (parent.isMissingNode() || parent.isNull())) {
                if (value.isMissingNode() || value.isNull()) return;
                ObjectNode object = (ObjectNode) root;
                for (String part : pointer.substring(1, slash).split("/")) object = object.withObjectProperty(part);
                parent = object;
            }
            String key = pointer.substring(slash + 1).replace("~1", "/").replace("~0", "~");
            if (parent instanceof ObjectNode object) {
                if (value.isMissingNode()) object.remove(key); else object.set(key, value);
                if (spec != null && slash > 0 && object.isEmpty()) {
                    JsonNode previousParent = baseline.at(pointer.substring(0, slash));
                    String parentKey = pointer.substring(1, slash);
                    if (previousParent.isMissingNode()) ((ObjectNode) root).remove(parentKey);
                    else if (previousParent.isNull()) ((ObjectNode) root).set(parentKey, previousParent);
                }
            } else ((ArrayNode) parent).set(Integer.parseInt(key), value);
        }
    }
}
