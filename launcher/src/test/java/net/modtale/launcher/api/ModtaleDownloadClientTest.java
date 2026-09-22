package net.modtale.launcher.api;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModtaleDownloadClientTest {
    @Test
    void failedTransferDoesNotLeavePartialDownload() throws Exception {
        String filename = "audit-" + UUID.randomUUID() + ".jar";
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/file", exchange -> {
            exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=" + filename);
            exchange.sendResponseHeaders(200, 100);
            try (var body = exchange.getResponseBody()) {
                body.write(new byte[]{1, 2, 3});
            } finally {
                exchange.close();
            }
        });
        server.start();
        try (HttpClient http = HttpClient.newHttpClient()) {
            URI uri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/file");
            var client = new ModtaleDownloadClient(http, () -> uri);
            assertThrows(ModtaleApiException.class, () -> client.download(uri.toString()));
            try (var files = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
                assertFalse(files.anyMatch(file -> file.getFileName().toString().endsWith(filename)));
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void savesCompleteDownloadAndRejectsHttpErrors() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/file", exchange -> {
            exchange.sendResponseHeaders(200, 3);
            try (var body = exchange.getResponseBody()) {
                body.write(new byte[]{1, 2, 3});
            }
        });
        server.start();
        try (HttpClient http = HttpClient.newHttpClient()) {
            URI base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
            var client = new ModtaleDownloadClient(http, () -> base);
            var result = client.download(base + "/file");
            try {
                assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(result.path()));
            } finally {
                Files.deleteIfExists(result.path());
            }
            assertThrows(ModtaleApiException.class, () -> client.download(base + "/missing"));
        } finally {
            server.stop(0);
        }
    }
}
