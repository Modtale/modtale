package net.modtale.service.communication;

import net.modtale.config.properties.AppAdminDiscordWebhookProperties;
import net.modtale.config.properties.AppBackendProperties;
import net.modtale.config.properties.AppDiscordWebhookProperties;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.config.properties.AppWebhookProperties;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.project.ScanResult;
import net.modtale.model.project.ScanStatus;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.ProjectService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class WebhookPayloadFactory {

    private final UserRepository userRepository;
    private final ProjectService projectService;
    private final String webhookUrl;
    private final String webhookKey;
    private final String discordWebhookUrl;
    private final String adminDiscordWebhookUrl;
    private final String frontendUrl;
    private final String backendUrl;

    public WebhookPayloadFactory(
            UserRepository userRepository,
            ProjectService projectService,
            AppWebhookProperties webhookProperties,
            AppDiscordWebhookProperties discordWebhookProperties,
            AppAdminDiscordWebhookProperties adminDiscordWebhookProperties,
            AppFrontendProperties frontendProperties,
            AppBackendProperties backendProperties
    ) {
        this.userRepository = userRepository;
        this.projectService = projectService;
        this.webhookUrl = webhookProperties.url();
        this.webhookKey = webhookProperties.key();
        this.discordWebhookUrl = discordWebhookProperties.url();
        this.adminDiscordWebhookUrl = adminDiscordWebhookProperties.url();
        this.frontendUrl = frontendProperties.url();
        this.backendUrl = backendProperties.url();
    }

    public WebhookDispatchRequest buildProjectWebhook(Project project) {
        String authorName = resolveAuthorName(project);

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

        return new WebhookDispatchRequest(webhookUrl, body);
    }

    public Optional<WebhookDispatchRequest> buildProjectDiscordWebhook(Project project) {
        if (discordWebhookUrl == null || discordWebhookUrl.isEmpty()) {
            return Optional.empty();
        }

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
        return Optional.of(new WebhookDispatchRequest(discordWebhookUrl, body));
    }

    public Optional<WebhookDispatchRequest> buildAdminNewProjectWebhook(Project project) {
        if (adminDiscordWebhookUrl == null || adminDiscordWebhookUrl.isEmpty()) {
            return Optional.empty();
        }

        String authorName = resolveAuthorName(project);

        Map<String, Object> embed = new HashMap<>();
        embed.put("title", "New Project Submitted: " + project.getTitle());
        embed.put("url", frontendUrl + projectService.getProjectLink(project));
        embed.put("color", 16753920);
        embed.put("description", "A new project has been submitted and is awaiting verification.\n\n**Author:** "
                + (authorName != null ? authorName : "Unknown")
                + "\n**Classification:** " + project.getClassification());

        Map<String, Object> body = new HashMap<>();
        body.put("username", "Modtale Admin Bot");
        body.put("avatar_url", frontendUrl + "/assets/favicon.png");
        body.put("content", "**Verification Required**");
        body.put("embeds", List.of(embed));
        return Optional.of(new WebhookDispatchRequest(adminDiscordWebhookUrl, body));
    }

    public Optional<WebhookDispatchRequest> buildAdminFlaggedVersionWebhook(
            Project project,
            ProjectVersion version,
            ScanResult scanResult
    ) {
        if (adminDiscordWebhookUrl == null || adminDiscordWebhookUrl.isEmpty()) {
            return Optional.empty();
        }

        String authorName = resolveAuthorName(project);

        Map<String, Object> embed = new HashMap<>();
        embed.put("title", "Flagged Version: " + project.getTitle());
        embed.put("url", frontendUrl + projectService.getProjectLink(project));
        embed.put("color", 16711680);

        String issues = (scanResult != null && scanResult.getIssues() != null && !scanResult.getIssues().isEmpty())
                ? "\n**Issues Detected:** " + scanResult.getIssues().size() : "";

        String versionNumber = version != null ? version.getVersionNumber() : "Unknown";
        ScanStatus status = scanResult != null && scanResult.getStatus() != null
                ? scanResult.getStatus()
                : ScanStatus.FAILED;
        String verdict = scanResult != null && scanResult.getVerdict() != null ? scanResult.getVerdict() : "UNKNOWN";
        String scanState = scanResult != null && scanResult.getScanState() != null
                ? scanResult.getScanState()
                : "UNKNOWN";
        int riskScore = scanResult != null ? scanResult.getRiskScore() : -1;
        int attempt = scanResult != null ? scanResult.getScanAttempt() : 0;
        int newIssues = scanResult != null ? scanResult.getNewIssueCount() : 0;
        int escalatedIssues = scanResult != null ? scanResult.getEscalatedIssueCount() : 0;

        embed.put("description", "**Author:** " + (authorName != null ? authorName : "Unknown")
                + "\n**Version:** " + versionNumber
                + "\n**Status:** " + status
                + "\n**Verdict:** " + verdict
                + "\n**Scan State:** " + scanState
                + "\n**Attempt:** " + attempt
                + "\n**Risk Score:** " + riskScore
                + "\n**New Issues:** " + newIssues
                + "\n**Escalated Issues:** " + escalatedIssues
                + issues
                + "\n\n*This file requires immediate manual review.*");

        Map<String, Object> body = new HashMap<>();
        body.put("username", "Modtale Warden");
        body.put("avatar_url", frontendUrl + "/assets/favicon.png");
        body.put("content", "**Flagged File Detected**");
        body.put("embeds", List.of(embed));
        return Optional.of(new WebhookDispatchRequest(adminDiscordWebhookUrl, body));
    }

    private String resolveAuthorName(Project project) {
        String authorName = project.getAuthor();
        if (authorName == null && project.getAuthorId() != null) {
            User user = userRepository.findById(project.getAuthorId()).orElse(null);
            if (user != null) {
                authorName = user.getUsername();
            }
        }
        return authorName;
    }
}
