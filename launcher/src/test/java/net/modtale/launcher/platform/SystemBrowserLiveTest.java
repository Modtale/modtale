package net.modtale.launcher.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class SystemBrowserLiveTest {

    @Test
    void systemBrowserOpensAndCallsBack() throws Exception {
        assumeTrue("true".equalsIgnoreCase(System.getenv("MODTALE_LIVE_BROWSER_TEST")));
        CompletableFuture<URI> request = new CompletableFuture<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/browser-test", exchange -> {
            request.complete(exchange.getRequestURI());
            byte[] response = "Modtale browser integration test passed. You can close this tab."
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, response.length);
            try (var body = exchange.getResponseBody()) {
                body.write(response);
            }
        });
        server.start();
        try {
            URI probe = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/browser-test");
            SystemBrowser.open(probe);
            assertEquals("/browser-test", request.get(Duration.ofSeconds(20).toMillis(), TimeUnit.MILLISECONDS).getPath());
        } finally {
            server.stop(0);
        }
    }
}
