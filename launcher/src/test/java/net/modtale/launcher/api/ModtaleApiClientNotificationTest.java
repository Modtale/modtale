package net.modtale.launcher.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import net.modtale.launcher.model.notification.LauncherNotification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ModtaleApiClientNotificationTest {
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void contributorInvitesUseCanonicalProjectMetadata() throws IOException {
        AtomicReference<String> request = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/api/v1/projects/project-123/invite/accept", exchange -> {
            request.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
            exchange.sendResponseHeaders(200, 2);
            exchange.getResponseBody().write("{}".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            exchange.close();
        });
        server.start();

        ModtaleApiClient client = new ModtaleApiClient("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v1");
        LauncherNotification notification = new LauncherNotification(
                "n1", "Invite", "Join", "", "", false, "CONTRIBUTOR_INVITE",
                Map.of("projectId", "project-123"), LocalDateTime.now()
        );

        client.resolveNotificationAction(notification, true);

        assertEquals("POST /api/v1/projects/project-123/invite/accept", request.get());
    }
}
