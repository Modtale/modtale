package net.modtale.status;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import net.modtale.status.StatusModels.StatusHistoryEntry;
import net.modtale.status.StatusModels.SystemStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class StatusProbeServiceTest {

    private HttpServer server;
    private StatusProbeService probeService;

    @AfterEach
    void tearDown() {
        if (probeService != null) {
            probeService.closeClients();
        }
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void validatesTheExpectedSiteAndReadinessPayloads() throws IOException {
        startServer(
                "text/html; charset=utf-8", "<!doctype html><title>Modtale</title>",
                "application/vnd.spring-boot.actuator.v3+json", "{\"status\":\"UP\"}"
        );

        StatusHistoryEntry result = probe().performHealthCheck();

        assertEquals(SystemStatus.OPERATIONAL, result.siteStatus());
        assertEquals(SystemStatus.OPERATIONAL, result.apiStatus());
    }

    @Test
    void rejectsSuccessfulHttpResponsesWithTheWrongContent() throws IOException {
        startServer(
                "text/html", "<title>Just a proxy error</title>",
                "application/json", "{\"status\":\"DOWN\"}"
        );

        StatusHistoryEntry result = probe().performHealthCheck();

        assertEquals(SystemStatus.OUTAGE, result.siteStatus());
        assertEquals(SystemStatus.OUTAGE, result.apiStatus());
    }

    private StatusProbeService probe() {
        StatusServiceProperties properties = new StatusServiceProperties();
        properties.setRequestTimeout(Duration.ofSeconds(2));
        properties.setDegradedLatency(Duration.ofSeconds(1));
        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        properties.setTargetSiteUrl(baseUrl + "/site");
        properties.setTargetApiUrl(baseUrl + "/api");
        probeService = new StatusProbeService(properties);
        return probeService;
    }

    private void startServer(String siteType, String siteBody, String apiType, String apiBody) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/site", exchange -> respond(exchange, siteType, siteBody));
        server.createContext("/api", exchange -> respond(exchange, apiType, apiBody));
        server.start();
    }

    private void respond(HttpExchange exchange, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
