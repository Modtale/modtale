package net.modtale.service.system;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.config.properties.AppStatusDiscordWebhookProperties;
import net.modtale.model.system.StatusHistory;
import net.modtale.model.system.SystemStatus;
import net.modtale.service.communication.WebhookDeliveryService;
import net.modtale.service.communication.WebhookDispatchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void notifyStatusChangeBuildsDiscordPayload() {
        WebhookDeliveryService deliveryService = mock(WebhookDeliveryService.class);
        StatusDiscordNotifierService service = new StatusDiscordNotifierService(
                new AppStatusDiscordWebhookProperties("https://discord.test/webhook"),
                new AppFrontendProperties("https://modtale.test/"),
                deliveryService
        );

        service.notifyStatusChange(history(SystemStatus.OPERATIONAL), history(SystemStatus.DEGRADED));

        org.mockito.ArgumentCaptor<WebhookDispatchRequest> requestCaptor =
                org.mockito.ArgumentCaptor.forClass(WebhookDispatchRequest.class);
        verify(deliveryService).deliverAsync(requestCaptor.capture(), eq("Failed to trigger Status Discord webhook"));

        WebhookDispatchRequest request = requestCaptor.getValue();
        assertEquals("https://discord.test/webhook", request.url());
        assertEquals("Modtale Status", request.body().get("username"));
        assertEquals("https://modtale.test/assets/favicon.png", request.body().get("avatar_url"));
        assertEquals("**Status update:** Modtale is degraded.", request.body().get("content"));

        List<?> embeds = (List<?>) request.body().get("embeds");
        Map<?, ?> embed = (Map<?, ?>) embeds.getFirst();
        assertEquals("Partial system degradation", embed.get("title"));
        assertEquals("https://modtale.test/status", embed.get("url"));
        assertTrue(String.valueOf(embed.get("description")).contains("**Previous:** Operational"));
        assertTrue(String.valueOf(embed.get("description")).contains("**Current:** Degraded"));
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
