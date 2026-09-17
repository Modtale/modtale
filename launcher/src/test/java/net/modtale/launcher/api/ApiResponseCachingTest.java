package net.modtale.launcher.api;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApiResponseCachingTest {
    @TempDir Path temp;

    @Test
    void concurrentClassAndGenericReadsShareOneRequest() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(requests, new AtomicInteger(200));
        try {
            URI uri = uri(server, "/data");
            var transport = new ModtaleApiTransport(HttpClient.newHttpClient(), new ApiResponseCache(temp));
            try (var executor = Executors.newFixedThreadPool(8)) {
                var calls = new ArrayList<Future<?>>();
                for (int i = 0; i < 16; i++) {
                    boolean generic = i % 2 == 0;
                    calls.add(executor.submit(() -> {
                        Map<?, ?> result = generic
                                ? transport.get(uri, new TypeReference<Map<String, String>>() {}, Duration.ofMinutes(1))
                                : transport.get(uri, Map.class, Duration.ofMinutes(1));
                        assertEquals("cached", result.get("name"));
                    }));
                }
                for (var call : calls) call.get(10, TimeUnit.SECONDS);
            }
            assertEquals(1, requests.get());
        } finally { server.stop(0); }
    }

    @Test
    void rateLimitServesStaleDataWithoutBlockingOtherEndpoints() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        HttpServer server = server(requests, new AtomicInteger(429));
        try {
            URI uri = uri(server, "/data");
            var cache = new ApiResponseCache(temp);
            cache.put(uri, "{\"name\":\"cached\"}");
            var transport = new ModtaleApiTransport(HttpClient.newHttpClient(), cache);
            for (int i = 0; i < 3; i++) {
                assertEquals("cached", transport.get(uri, Map.class, Duration.ofNanos(1)).get("name"));
            }
            assertEquals(1, requests.get());
            assertThrows(ModtaleApiException.class, () -> transport.get(uri(server, "/other"), Map.class, Duration.ofMinutes(1)));
            assertEquals(2, requests.get());
        } finally { server.stop(0); }
    }

    @Test
    void serverErrorsUseStaleDataButAuthFailuresDoNot() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger status = new AtomicInteger(503);
        HttpServer server = server(requests, status);
        try {
            URI uri = uri(server, "/data");
            var cache = new ApiResponseCache(temp);
            cache.put(uri, "{\"name\":\"cached\"}");
            var transport = new ModtaleApiTransport(HttpClient.newHttpClient(), cache);
            assertEquals("cached", transport.get(uri, new TypeReference<Map<String, String>>() {}, Duration.ofNanos(1)).get("name"));
            status.set(401);
            assertThrows(ModtaleApiException.class, () -> transport.get(uri, Map.class, Duration.ofNanos(1)));
        } finally { server.stop(0); }
    }

    private static HttpServer server(AtomicInteger requests, AtomicInteger status) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = "{\"name\":\"cached\"}".getBytes(StandardCharsets.UTF_8);
            if (status.get() == 429) exchange.getResponseHeaders().set("Retry-After", "60");
            exchange.sendResponseHeaders(status.get(), body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        return server;
    }

    private static URI uri(HttpServer server, String path) {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + path);
    }
}
