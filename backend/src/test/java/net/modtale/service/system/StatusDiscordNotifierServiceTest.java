package net.modtale.service.system;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.config.properties.AppStatusDiscordWebhookProperties;
import net.modtale.model.system.StatusHistory;
import net.modtale.model.system.SystemStatus;
import net.modtale.service.communication.WebhookDeliveryService;
import net.modtale.service.communication.WebhookDispatchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class StatusDiscordNotifierServiceTest {

    @Test
    void notifyStatusChangeReturnsEarlyWhenNoWebhookIsConfigured() {
        WebhookDeliveryService deliveryService = mock(WebhookDeliveryService.class);
        StatusDiscordNotifierService service = new StatusDiscordNotifierService(
                new AppStatusDiscordWebhookProperties(""),
                new AppFrontendProperties("https://modtale.test"),
                deliveryService
        );

        service.notifyStatusChange(null, history(SystemStatus.DEGRADED));

        verifyNoInteractions(deliveryService);
    }

    @Test
    void notifyStatusChangeReturnsEarlyOutsideProduction() {
        WebhookDeliveryService deliveryService = mock(WebhookDeliveryService.class);
        StatusDiscordNotifierService service = new StatusDiscordNotifierService(
                new AppStatusDiscordWebhookProperties("https://discord.test/webhook"),
                new AppFrontendProperties("https://dev.modtale.net"),
                deliveryService
        );

        service.notifyStatusChange(null, history(SystemStatus.DEGRADED));

        verifyNoInteractions(deliveryService);
    }

    @Test
    void notifyStatusChangeBuildsStatusMonitorPayload() {
        WebhookDeliveryService deliveryService = mock(WebhookDeliveryService.class);
        StatusDiscordNotifierService service = new StatusDiscordNotifierService(
                new AppStatusDiscordWebhookProperties("https://discord.test/webhook"),
                new AppFrontendProperties("https://modtale.net/"),
                deliveryService
        );

        StatusHistory previous = history(SystemStatus.OPERATIONAL);
        StatusHistory latest = history(SystemStatus.DEGRADED);
        service.notifyStatusChange(previous, latest, List.of(previous, latest));

        org.mockito.ArgumentCaptor<WebhookDispatchRequest> requestCaptor =
                org.mockito.ArgumentCaptor.forClass(WebhookDispatchRequest.class);
        verify(deliveryService).deliverAsync(requestCaptor.capture(), eq("Failed to trigger Status Discord webhook"));

        WebhookDispatchRequest request = requestCaptor.getValue();
        assertEquals("https://discord.test/webhook", request.url());
        assertEquals("Modtale Status Monitor", request.body().get("username"));
        assertEquals("https://modtale.net/assets/favicon.png", request.body().get("avatar_url"));
        assertFalse(request.body().containsKey("content"));

        List<?> embeds = (List<?>) request.body().get("embeds");
        Map<?, ?> embed = (Map<?, ?>) embeds.getFirst();
        assertTrue(String.valueOf(embed.get("title")).contains("Modtale Status Monitor"));
        assertEquals("https://modtale.net/status", embed.get("url"));

        String description = String.valueOf(embed.get("description"));
        assertTrue(description.contains("Real-time service status and uptime"));
        assertTrue(description.contains("Live monitoring of all Modtale services (last 30 days)"));
        assertTrue(description.contains("API Gateway"));
        assertTrue(description.contains("Database (Atlas)"));
        assertTrue(description.contains("Storage (R2)"));
        assertTrue(description.contains("Degraded"));
    }

    @Test
    void statusWebhookPropertyDoesNotFallBackToAdminWebhook() throws Exception {
        Properties properties = new Properties();
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            assertNotNull(input);
            properties.load(input);
        }

        assertEquals("${STATUS_DISCORD_WEBHOOK_URL:}", properties.getProperty("app.status-discord-webhook.url"));
    }

    private static StatusHistory history(SystemStatus overall) {
        StatusHistory history = new StatusHistory(
                25,
                10,
                20,
                overall,
                overall == SystemStatus.DEGRADED ? SystemStatus.DEGRADED : SystemStatus.OPERATIONAL,
                overall == SystemStatus.DEGRADED ? SystemStatus.OUTAGE : SystemStatus.OPERATIONAL,
                SystemStatus.OPERATIONAL
        );
        ReflectionTestUtils.setField(history, "timestamp", LocalDateTime.now());
        return history;
    }
}
