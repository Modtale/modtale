package net.modtale.launcher.wardrobe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.html.HTML;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;

/** Public, read-only popular feed. No account access, username lookup, cookies, or uploads. */
public class PopularSkinClient {
    private static final URI ORIGIN = URI.create("https://hytags.com/");
    private static final int LIMIT = 4 * 1024 * 1024;
    private static final ObjectMapper JSON = new ObjectMapper();
    private final HttpClient http;
    private final URI origin;
    private final Map<Integer, Page> pages = new LinkedHashMap<>();
    public record Page(List<WardrobeItem> items, boolean hasNext) {
        public Page { items = List.copyOf(items); }
    }

    public PopularSkinClient() { this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), ORIGIN); }
    public PopularSkinClient(HttpClient http, URI origin) {
        if (http.followRedirects() != HttpClient.Redirect.NEVER || http.cookieHandler().isPresent() || http.authenticator().isPresent())
            throw new IllegalArgumentException("Popular feed requires an anonymous HTTP client without redirects");
        boolean loopback = "http".equals(origin.getScheme()) && Set.of("localhost", "127.0.0.1").contains(origin.getHost());
        if ((!ORIGIN.equals(origin) && !loopback) || origin.getUserInfo() != null || origin.getQuery() != null
                || origin.getFragment() != null || !"/".equals(origin.getPath())) throw new IllegalArgumentException("Invalid popular feed origin");
        this.http = http; this.origin = origin;
    }

    public synchronized Page page(int number) {
        if (number < 1) throw new IllegalArgumentException("Page must be positive");
        if (pages.containsKey(number)) return pages.get(number);
        String html = get(origin.resolve("skins?page=" + number + "&sort=user_count&order=desc"), "text/html");
        Map<String, WardrobeItem> items = new LinkedHashMap<>();
        StringBuilder text = new StringBuilder();
        boolean[] next = {false};
        try {
            new ParserDelegator().parse(new StringReader(html), new HTMLEditorKit.ParserCallback() {
                @Override public void handleStartTag(HTML.Tag tag, MutableAttributeSet attrs, int pos) {
                    if (tag != HTML.Tag.A || attrs.getAttribute(HTML.Attribute.HREF) == null) return;
                    URI link;
                    try { link = origin.resolve(attrs.getAttribute(HTML.Attribute.HREF).toString()); }
                    catch (IllegalArgumentException invalid) { return; }
                    if (!origin.getScheme().equals(link.getScheme()) || !origin.getAuthority().equals(link.getAuthority()) || link.getUserInfo() != null) return;
                    if (link.getPath().matches("/skin/[a-fA-F0-9]{32}")) {
                        String hash = link.getPath().substring(6).toLowerCase(Locale.ROOT);
                        items.putIfAbsent(hash, item(hash, null));
                    }
                    if ("/skins".equals(link.getPath()) && link.getQuery() != null
                            && Arrays.asList(link.getQuery().split("&")).contains("page=" + (number + 1))) next[0] = true;
                }
                @Override public void handleText(char[] data, int pos) { text.append(data).append(' '); }
            }, true);
        } catch (IOException error) { throw new IllegalStateException("Could not read the popular skins feed", error); }
        if ((next[0] && items.size() != 20) || items.size() > 20 || (items.isEmpty() && (!text.toString().contains("Skin archive") || next[0])))
            throw new IllegalStateException("Popular skins feed format is unavailable or changed");
        Page result = new Page(List.copyOf(items.values()), next[0]);
        if (pages.size() >= 32) pages.remove(pages.keySet().iterator().next());
        pages.put(number, result); return result;
    }

    public WardrobeItem download(String hash) {
        hash = validHash(hash);
        try {
            JsonNode skin = JSON.readTree(get(origin.resolve("api/skin/" + hash), "application/json"));
            if (skin == null || !skin.isObject() || !skin.path("bodyCharacteristic").isTextual() || skin.path("bodyCharacteristic").asText().isBlank())
                throw new IllegalStateException("Popular skin has no cosmetic definition");
            skin.properties().forEach(entry -> {
                CosmeticCatalogClient.assetFile(entry.getKey());
                var value = entry.getValue();
                if (!value.isNull() && (!value.isTextual() || value.asText().length() > 512 || !value.asText().matches("[A-Za-z0-9_+\\-]+(?:\\.[A-Za-z0-9_+\\-]+){0,2}")))
                    throw new IllegalStateException("Invalid popular skin cosmetic");
            });
            return item(hash, skin);
        } catch (IOException error) { throw new IllegalStateException("Invalid popular skin response", error); }
    }

    public String thumbnail(String hash) {
        return "https://hyvatar.io/render/full/NPC?size=256&skin_id=" + validHash(hash);
    }

    private static String validHash(String hash) {
        if (hash == null || !hash.matches("[a-fA-F0-9]{32}")) throw new IllegalArgumentException("Invalid popular skin identifier");
        return hash.toLowerCase(Locale.ROOT);
    }

    private static WardrobeItem item(String hash, JsonNode skin) {
        var payload = JSON.createObjectNode().put("skinId", hash);
        if (skin != null) payload.set("skin", skin);
        return new WardrobeItem(UUID.nameUUIDFromBytes(("SKIN:" + hash).getBytes(StandardCharsets.UTF_8)),
                WardrobeItem.Kind.SKIN, "Skin #" + hash.substring(0, 8), false, "", payload.toString());
    }

    private String get(URI uri, String accept) {
        var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20)).header("Accept", accept)
                .header("User-Agent", "ModtaleLauncher/1.0").GET().build();
        try {
            var bytes = new java.io.ByteArrayOutputStream();
            var response = http.send(request, HttpResponse.BodyHandlers.ofByteArrayConsumer(chunk -> chunk.ifPresent(part -> {
                if ((long)bytes.size() + part.length > LIMIT) throw new IllegalStateException("Popular feed response too large");
                bytes.writeBytes(part);
            })));
            if (response.statusCode() != 200) throw new IllegalStateException("Popular skins request failed (HTTP " + response.statusCode() + ")");
            if (!response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].trim().equalsIgnoreCase(accept))
                throw new IllegalStateException("Unexpected popular skins response type");
            return bytes.toString(StandardCharsets.UTF_8);
        } catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException("Popular skins request interrupted", error); }
        catch (IOException error) { throw new IllegalStateException("Popular skins service unavailable", error); }
    }
}
