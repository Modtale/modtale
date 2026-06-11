package net.modtale.service.communication;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import org.springframework.stereotype.Service;

@Service
public class WebhookService {

    private final WebhookPayloadFactory webhookPayloadFactory;
    private final WebhookDeliveryService webhookDeliveryService;

    public WebhookService(
            WebhookPayloadFactory webhookPayloadFactory,
            WebhookDeliveryService webhookDeliveryService
    ) {
        this.webhookPayloadFactory = webhookPayloadFactory;
        this.webhookDeliveryService = webhookDeliveryService;
    }

    public void triggerWebhook(Project project) {
        webhookDeliveryService.deliverAsync(
                webhookPayloadFactory.buildProjectWebhook(project),
                "Failed to trigger webhook"
        );
    }

    public void triggerDiscordWebhook(Project project) {
        webhookPayloadFactory.buildProjectDiscordWebhook(project).ifPresent(request ->
                webhookDeliveryService.deliverAsync(request, "Failed to trigger Discord webhook"));
    }

    public void triggerAdminNewProjectWebhook(Project project) {
        webhookPayloadFactory.buildAdminNewProjectWebhook(project).ifPresent(request ->
                webhookDeliveryService.deliverAsync(request, "Failed to trigger Admin Discord webhook"));
    }

    public void triggerAdminFlaggedVersionWebhook(Project project, ProjectVersion version, ScanResult scanResult) {
        webhookPayloadFactory.buildAdminFlaggedVersionWebhook(project, version, scanResult).ifPresent(request ->
                webhookDeliveryService.deliverAsync(request, "Failed to trigger Admin Discord webhook for flagged version"));
    }
}
