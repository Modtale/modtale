package net.modtale.launcher.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;
import net.modtale.launcher.model.project.WikiBundle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModtaleApiClientWikiTest {

    @TempDir
    Path tempDir;

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void encodesEachWikiPathSegmentWithoutFlatteningHierarchy() {
        assertEquals("/guides/getting%20started", ModtaleApiClient.wikiPath("guides/getting started"));
        assertEquals("", ModtaleApiClient.wikiPath(null));
    }

    @Test
    void rejectsUnsafeWikiPaths() {
        assertThrows(IllegalArgumentException.class, () -> ModtaleApiClient.wikiPath("../admin"));
        assertThrows(IllegalArgumentException.class, () -> ModtaleApiClient.wikiPath("guides//admin"));
        assertThrows(IllegalArgumentException.class, () -> ModtaleApiClient.wikiPath("/admin"));
    }

    @Test
    void fetchesNestedWikiBundleThroughLauncherApiContract() throws Exception {
        AtomicReference<String> requestPath = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v1/wiki/project-one/_bundle", exchange -> {
            requestPath.set(exchange.getRequestURI().getRawPath());
            byte[] body = """
                    {
                      "metadata":{"mod":{"name":"Project Wiki","pages":[]}},
                      "page":{"title":"Install","content":"Documentation"},
                      "pageSlug":"guides/getting started"
                    }
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        ModtaleApiClient client = new ModtaleApiClient(
                HttpClient.newHttpClient(),
                "http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1",
                new ApiResponseCache(tempDir)
        );

        WikiBundle bundle = client.getWikiBundle("project-one", "guides/getting started");

        assertEquals("/api/v1/wiki/project-one/_bundle/guides/getting%20started", requestPath.get());
        assertEquals("Install", bundle.content().title());
        assertEquals("Documentation", bundle.content().content());
    }
}
