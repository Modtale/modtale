package net.modtale.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.modtale.status.StatusModels.ServiceStatusView;
import net.modtale.status.StatusModels.StatusHistoryPointView;
import net.modtale.status.StatusModels.SystemStatus;
import net.modtale.status.StatusModels.SystemStatusView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StatusDiscordNotifierTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<CapturedRequest> requests = new ArrayList<>();
    private HttpServer server;
    private int patchStatus = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhooks/1/token", this::handleWebhook);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void createsOnceThenContinuallyEditsTheSameStatusEmbed() throws Exception {
        MongoStatusStore store = mock(MongoStatusStore.class);
        when(store.findDiscordStatusMessageId()).thenReturn(Optional.empty());
        StatusDiscordNotifier notifier = notifier(store);

        notifier.publishStatus(status());
        notifier.publishStatus(status());

        assertEquals(2, requests.size());
        assertEquals("POST", requests.get(0).method());
        assertEquals("wait=true", requests.get(0).query());
        assertEquals("PATCH", requests.get(1).method());
        assertEquals("/webhooks/1/token/messages/123456", requests.get(1).path());
        verify(store).saveDiscordStatusMessageId("123456");

        JsonNode createBody = objectMapper.readTree(requests.get(0).body());
        assertEquals("Modtale Status", createBody.path("username").asText());
        assertEquals("https://modtale.net/assets/favicon.png", createBody.path("avatar_url").asText());
        assertEquals("Modtale Status", createBody.path("embeds").get(0).path("title").asText());
        String description = createBody.path("embeds").get(0).path("description").asText();
        assertTrue(description.contains("Main Site"));
        assertTrue(description.contains("API Gateway"));
        assertTrue(description.contains("Database (Atlas)"));
        assertTrue(description.contains("Storage (R2)"));
        assertTrue(description.contains("■"));
        assertTrue(description.contains("▲"));
        assertFalse(description.contains("🌐"));
        assertFalse(description.contains("⚡"));
        assertTrue(description.contains("Operational"));
        assertTrue(description.contains("Degraded"));
        assertFalse(createBody.path("allowed_mentions").path("parse").elements().hasNext());

        JsonNode editBody = objectMapper.readTree(requests.get(1).body());
        assertFalse(editBody.has("username"));
        assertTrue(editBody.path("content").isNull());
    }

    @Test
    void resumesEditingThePersistedMessageAfterRestart() {
        MongoStatusStore store = mock(MongoStatusStore.class);
        when(store.findDiscordStatusMessageId()).thenReturn(Optional.of("existing-message"));

        notifier(store).publishStatus(status());

        assertEquals(1, requests.size());
        assertEquals("PATCH", requests.getFirst().method());
        assertEquals("/webhooks/1/token/messages/existing-message", requests.getFirst().path());
        verify(store).findDiscordStatusMessageId();
        verifyNoMoreInteractions(store);
    }

    @Test
    void replacesADeletedStatusMessageWithoutStartingAStream() {
        patchStatus = 404;
        MongoStatusStore store = mock(MongoStatusStore.class);
        when(store.findDiscordStatusMessageId()).thenReturn(Optional.of("deleted-message"));

        notifier(store).publishStatus(status());

        assertEquals(List.of("PATCH", "POST"), requests.stream().map(CapturedRequest::method).toList());
        verify(store).saveDiscordStatusMessageId("123456");
    }

    @Test
    void doesNotCreateADuplicateWhenDiscordTemporarilyRejectsAnEdit() {
        patchStatus = 500;
        MongoStatusStore store = mock(MongoStatusStore.class);
        when(store.findDiscordStatusMessageId()).thenReturn(Optional.of("existing-message"));

        notifier(store).publishStatus(status());

        assertEquals(1, requests.size());
        assertEquals("PATCH", requests.getFirst().method());
        assertEquals("/webhooks/1/token/messages/existing-message", requests.getFirst().path());
    }

    private StatusDiscordNotifier notifier(MongoStatusStore store) {
        StatusServiceProperties properties = new StatusServiceProperties();
        properties.setDiscordWebhookUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/webhooks/1/token");
        properties.setPublicStatusUrl("https://status.modtale.net");
        return new StatusDiscordNotifier(properties, objectMapper, store, HttpClient.newHttpClient());
    }

    private SystemStatusView status() {
        long now = Instant.parse("2026-09-04T00:00:00Z").toEpochMilli();
        List<StatusHistoryPointView> history = new ArrayList<>();
        for (int index = 0; index < 10; index++) {
            long time = now - (9L - index) * 2L * 60L * 60L * 1000L;
            history.add(new StatusHistoryPointView(
                    time,
                    21,
                    43,
                    7,
                    65,
                    SystemStatus.OPERATIONAL,
                    index == 9 ? SystemStatus.DEGRADED : SystemStatus.OPERATIONAL,
                    SystemStatus.OPERATIONAL,
                    SystemStatus.OPERATIONAL
            ));
        }
        return new SystemStatusView(
                SystemStatus.DEGRADED,
                List.of(
                        new ServiceStatusView("site", "Main Site", SystemStatus.OPERATIONAL, 21),
                        new ServiceStatusView("api", "API Gateway", SystemStatus.DEGRADED, 43),
                        new ServiceStatusView("database", "Database (Atlas)", SystemStatus.OPERATIONAL, 7),
                        new ServiceStatusView("storage", "Storage (R2)", SystemStatus.OPERATIONAL, 65)
                ),
                now,
                false,
                history.size(),
                1_440,
                history,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private void handleWebhook(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requests.add(new CapturedRequest(
                exchange.getRequestMethod(),
                exchange.getRequestURI().getPath(),
                exchange.getRequestURI().getQuery(),
                body
        ));

        boolean patch = "PATCH".equals(exchange.getRequestMethod());
        int status = patch ? patchStatus : 200;
        byte[] response = (patch ? "{}" : "{\"id\":\"123456\"}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }

    private record CapturedRequest(String method, String path, String query, String body) {
    }
}
