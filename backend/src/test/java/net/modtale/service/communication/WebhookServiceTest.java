package net.modtale.service.communication;

import net.modtale.config.properties.AppAdminDiscordWebhookProperties;
import net.modtale.config.properties.AppBackendProperties;
import net.modtale.config.properties.AppDiscordWebhookProperties;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.config.properties.AppWebhookProperties;
import net.modtale.model.project.Project;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WebhookServiceTest {

    @Test
    void triggerWebhookBuildsPayloadAndDelegatesDelivery() {
        UserRepository userRepository = mock(UserRepository.class);
        ProjectService projectService = mock(ProjectService.class);
        WebhookDeliveryService webhookDeliveryService = mock(WebhookDeliveryService.class);
        WebhookPayloadFactory webhookPayloadFactory = new WebhookPayloadFactory(
                userRepository,
                projectService,
                new AppWebhookProperties("https://hooks.modtale.test", "secret"),
                new AppDiscordWebhookProperties(""),
                new AppAdminDiscordWebhookProperties(""),
                new AppFrontendProperties("https://modtale.test"),
                new AppBackendProperties("https://api.modtale.test")
        );
        WebhookService service = new WebhookService(webhookPayloadFactory, webhookDeliveryService);
        Project project = new Project();
        project.setId("project-1");
        project.setAuthor("Ada");
        project.setTitle("Sky Tools");
        project.setDescription("A better skyblock.");
        project.setImageUrl("https://cdn.modtale.test/icon.png");

        when(projectService.getProjectLink(project)).thenReturn("/project/sky-tools");

        service.triggerWebhook(project);

        org.mockito.ArgumentCaptor<WebhookDispatchRequest> requestCaptor =
                org.mockito.ArgumentCaptor.forClass(WebhookDispatchRequest.class);
        verify(webhookDeliveryService).deliverAsync(requestCaptor.capture(), eq("Failed to trigger webhook"));
        WebhookDispatchRequest request = requestCaptor.getValue();
        assertEquals("https://hooks.modtale.test", request.url());
        assertEquals("Sky Tools", request.body().get("title"));
        assertEquals("Ada", request.body().get("developerName"));
        assertEquals("https://modtale.test/project/sky-tools", request.body().get("modLink"));
    }

    @Test
    void triggerDiscordWebhookReturnsEarlyWhenNoDiscordWebhookIsConfigured() {
        UserRepository userRepository = mock(UserRepository.class);
        ProjectService projectService = mock(ProjectService.class);
        WebhookDeliveryService webhookDeliveryService = mock(WebhookDeliveryService.class);
        WebhookPayloadFactory webhookPayloadFactory = new WebhookPayloadFactory(
                userRepository,
                projectService,
                new AppWebhookProperties("https://hooks.modtale.test", "secret"),
                new AppDiscordWebhookProperties(""),
                new AppAdminDiscordWebhookProperties(""),
                new AppFrontendProperties("https://modtale.test"),
                new AppBackendProperties("https://api.modtale.test")
        );
        WebhookService service = new WebhookService(webhookPayloadFactory, webhookDeliveryService);

        service.triggerDiscordWebhook(new Project());

        verifyNoInteractions(webhookDeliveryService);
    }
}
