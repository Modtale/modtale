package net.modtale.launcher.news;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ModtaleNewsClientTest {
    private static final URI BASE = URI.create("https://modtale.net/rss.xml");
    private static final String RSS = """
            <?xml version="1.0"?>
            <rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/"><channel>
              <item><title>Mods &amp; worlds</title><link>/news/new-tools</link>
                <pubDate>Tue, 08 Sep 2026 12:00:00 GMT</pubDate>
                <media:content url="/assets/news/image.png" medium="image"/>
              </item>
            </channel></rss>
            """;

    @Test void readsSiteFeedDatesImagesAndRelativeLinks() {
        var posts = ModtaleNewsClient.parse(bytes(RSS), BASE);
        assertEquals(1, posts.size());
        var post = posts.getFirst();
        assertEquals("Mods & worlds", post.title());
        assertEquals("https://modtale.net/news/new-tools", post.url());
        assertEquals("https://modtale.net/assets/news/image.png", post.imageUrl());
        assertEquals(Instant.parse("2026-09-08T12:00:00Z"), post.publishedAt());
        assertEquals("Modtale", post.source());
    }

    @Test void rejectsHtmlAndExternalEntities() {
        assertThrows(IllegalArgumentException.class, () -> ModtaleNewsClient.parse(bytes("<html><body>Home</body></html>"), BASE));
        assertThrows(IllegalArgumentException.class, () -> ModtaleNewsClient.parse(bytes(
                "<!DOCTYPE rss [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><rss><channel>&xxe;</channel></rss>"), BASE));
    }

    @Test void ignoresNonWebLinksAndHandlesMissingDatesAndImages() {
        assertTrue(ModtaleNewsClient.parse(bytes(RSS.replace("/news/new-tools", "javascript:alert(1)")), BASE).isEmpty());
        var posts = ModtaleNewsClient.parse(bytes("<rss><channel><item><title>News</title><link>/news/test</link></item></channel></rss>"), BASE);
        assertEquals(Instant.EPOCH, posts.getFirst().publishedAt());
        assertEquals("", posts.getFirst().imageUrl());
    }

    @Test void fetchesFromConfiguredFeedAndRejectsHttpErrors() throws Exception {
        var server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/rss.xml", exchange -> {
            assertNull(exchange.getRequestHeaders().getFirst("Cookie"));
            assertNull(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = bytes(RSS);
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) { body.write(response); }
        });
        server.createContext("/missing", exchange -> { exchange.sendResponseHeaders(503, -1); exchange.close(); });
        server.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            assertEquals(1, new ModtaleNewsClient(base.resolve("/rss.xml")).fetch().size());
            assertThrows(IllegalStateException.class, () -> new ModtaleNewsClient(base.resolve("/missing")).fetch());
        } finally { server.stop(0); }
    }
    private static byte[] bytes(String text) { return text.getBytes(StandardCharsets.UTF_8); }
}
