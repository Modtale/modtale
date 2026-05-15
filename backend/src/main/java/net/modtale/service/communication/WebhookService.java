package net.modtale.service.communication;

import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.ProjectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Service
public class WebhookService {

    private static final Logger logger = LoggerFactory.getLogger(WebhookService.class);

    @Autowired private UserRepository userRepository;
    @Autowired private ProjectService projectService;

    @Qualifier("taskExecutor")
    @Autowired private Executor taskExecutor;

    @Value("${app.webhook.url}") private String webhookUrl;
    @Value("${app.webhook.key}") private String webhookKey;
    @Value("${app.discord-webhook.url}") private String discordWebhookUrl;
    @Value("${app.admin-discord-webhook.url}") private String adminDiscordWebhookUrl;
    @Value("${app.frontend.url}") private String frontendUrl;
    @Value("${app.backend.url}") private String backendUrl;

    public void triggerWebhook(Project project) {
        taskExecutor.execute(() -> {
            try {
                String authorName = project.getAuthor();
                if (authorName == null && project.getAuthorId() != null) {
                    User u = userRepository.findById(project.getAuthorId()).orElse(null);
                    if (u != null) authorName = u.getUsername();
                }

                RestTemplate restTemplate = new RestTemplate();
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, Object> body = new HashMap<>();
                body.put("apiKey", webhookKey);
                body.put("type", "New");
                body.put("title", project.getTitle());
                body.put("description", project.getDescription());
                body.put("iconLink", project.getImageUrl());
                body.put("modLink", frontendUrl + projectService.getProjectLink(project));
                body.put("developerName", authorName != null ? authorName : "Unknown");

                String apiUrl = frontendUrl.replaceFirst("^(https?://)", "$1api.");
                String ogUrl = apiUrl + "/api/v1/og/project/" + project.getId() + ".jpg";
                body.put("ogImageLink", ogUrl);

                restTemplate.postForEntity(webhookUrl, new HttpEntity<>(body, headers), String.class);
            } catch (Exception e) { logger.error("Failed to trigger webhook", e); }
        });
    }

    public void triggerDiscordWebhook(Project project) {
        if (discordWebhookUrl == null || discordWebhookUrl.isEmpty()) return;
        taskExecutor.execute(() -> {
            try {
                RestTemplate restTemplate = new RestTemplate();
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, Object> embed = new HashMap<>();
                embed.put("title", project.getTitle());
                embed.put("url", frontendUrl + projectService.getProjectLink(project));
                embed.put("color", 3447003);

                Map<String, String> imageMap = new HashMap<>();
                String cleanBackendUrl = backendUrl.endsWith("/") ? backendUrl.substring(0, backendUrl.length() - 1) : backendUrl;
                imageMap.put("url", cleanBackendUrl + "/api/v1/og/project/" + project.getId() + ".jpg");
                embed.put("image", imageMap);

                Map<String, Object> body = new HashMap<>();
                body.put("username", "Modtale");
                body.put("avatar_url", frontendUrl + "/assets/favicon.png");
                body.put("embeds", List.of(embed));

                restTemplate.postForEntity(discordWebhookUrl, new HttpEntity<>(body, headers), String.class);
            } catch (Exception e) { logger.error("Failed to trigger Discord webhook", e); }
        });
    }

    public void triggerAdminNewProjectWebhook(Project project) {
        if (adminDiscordWebhookUrl == null || adminDiscordWebhookUrl.isEmpty()) return;
        taskExecutor.execute(() -> {
            try {
                String authorName = project.getAuthor();
                if (authorName == null && project.getAuthorId() != null) {
                    User u = userRepository.findById(project.getAuthorId()).orElse(null);
                    if (u != null) authorName = u.getUsername();
                }

                RestTemplate restTemplate = new RestTemplate();
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, Object> embed = new HashMap<>();
                embed.put("title", "New Project Submitted: " + project.getTitle());
                embed.put("url", frontendUrl + projectService.getProjectLink(project));
                embed.put("color", 16753920);
                embed.put("description", "A new project has been submitted and is awaiting verification.\n\n**Author:** " + (authorName != null ? authorName : "Unknown") + "\n**Classification:** " + project.getClassification());

                Map<String, Object> body = new HashMap<>();
                body.put("username", "Modtale Admin Bot");
                body.put("avatar_url", frontendUrl + "/assets/favicon.png");
                body.put("content", "**Verification Required**");
                body.put("embeds", List.of(embed));

                restTemplate.postForEntity(adminDiscordWebhookUrl, new HttpEntity<>(body, headers), String.class);
            } catch (Exception e) { logger.error("Failed to trigger Admin Discord webhook", e); }
        });
    }

    public void triggerAdminFlaggedVersionWebhook(Project project, ProjectVersion version, ScanResult scanResult) {
        if (adminDiscordWebhookUrl == null || adminDiscordWebhookUrl.isEmpty()) return;
        taskExecutor.execute(() -> {
            try {
                String authorName = project.getAuthor();
                if (authorName == null && project.getAuthorId() != null) {
                    User u = userRepository.findById(project.getAuthorId()).orElse(null);
                    if (u != null) authorName = u.getUsername();
                }

                RestTemplate restTemplate = new RestTemplate();
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, Object> embed = new HashMap<>();
                embed.put("title", "Flagged Version: " + project.getTitle());
                embed.put("url", frontendUrl + projectService.getProjectLink(project));
                embed.put("color", 16711680);

                String issues = (scanResult != null && scanResult.getIssues() != null && !scanResult.getIssues().isEmpty())
                        ? "\n**Issues Detected:** " + scanResult.getIssues().size() : "";

                String vNum = version != null ? version.getVersionNumber() : "Unknown";
                ScanStatus sStatus = scanResult != null && scanResult.getStatus() != null ? scanResult.getStatus() : ScanStatus.FAILED;

                embed.put("description", "**Author:** " + (authorName != null ? authorName : "Unknown") +
                        "\n**Version:** " + vNum + "\n**Status:** " + sStatus + issues +
                        "\n\n*This file requires immediate manual review.*");

                Map<String, Object> body = new HashMap<>();
                body.put("username", "Modtale Warden");
                body.put("avatar_url", frontendUrl + "/assets/favicon.png");
                body.put("content", "**Flagged File Detected**");
                body.put("embeds", List.of(embed));

                restTemplate.postForEntity(adminDiscordWebhookUrl, new HttpEntity<>(body, headers), String.class);
            } catch (Exception e) { logger.error("Failed to trigger Admin Discord webhook for flagged version", e); }
        });
    }
}