package net.modtale.status;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "status")
public class StatusServiceProperties {

    private Duration requestTimeout = Duration.ofSeconds(5);
    private Duration historyRetention = Duration.ofDays(30);
    private String snapshotPath = "/tmp/modtale-status-snapshot.json";
    private String targetSiteUrl = "https://modtale.net";
    private String targetApiUrl = "https://api.modtale.net/actuator/health/readiness";
    private String mongoUri = "";
    private String mongoDatabase = "modtale";
    private String publicStatusUrl = "https://status.modtale.net";
    private String discordWebhookUrl = "";
    private List<String> corsAllowedOrigins = new ArrayList<>(List.of("*"));
    private R2 r2 = new R2();

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public Duration getHistoryRetention() {
        return historyRetention;
    }

    public void setHistoryRetention(Duration historyRetention) {
        this.historyRetention = historyRetention;
    }

    public String getSnapshotPath() {
        return snapshotPath;
    }

    public void setSnapshotPath(String snapshotPath) {
        this.snapshotPath = snapshotPath;
    }

    public String getTargetSiteUrl() {
        return targetSiteUrl;
    }

    public void setTargetSiteUrl(String targetSiteUrl) {
        this.targetSiteUrl = targetSiteUrl;
    }

    public String getTargetApiUrl() {
        return targetApiUrl;
    }

    public void setTargetApiUrl(String targetApiUrl) {
        this.targetApiUrl = targetApiUrl;
    }

    public String getMongoUri() {
        return mongoUri;
    }

    public void setMongoUri(String mongoUri) {
        this.mongoUri = mongoUri;
    }

    public String getMongoDatabase() {
        return mongoDatabase;
    }

    public void setMongoDatabase(String mongoDatabase) {
        this.mongoDatabase = mongoDatabase;
    }

    public String getPublicStatusUrl() {
        return publicStatusUrl;
    }

    public void setPublicStatusUrl(String publicStatusUrl) {
        this.publicStatusUrl = publicStatusUrl;
    }

    public String getDiscordWebhookUrl() {
        return discordWebhookUrl;
    }

    public void setDiscordWebhookUrl(String discordWebhookUrl) {
        this.discordWebhookUrl = discordWebhookUrl;
    }

    public List<String> getCorsAllowedOrigins() {
        return corsAllowedOrigins;
    }

    public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
        this.corsAllowedOrigins = corsAllowedOrigins;
    }

    public R2 getR2() {
        return r2;
    }

    public void setR2(R2 r2) {
        this.r2 = r2;
    }

    public static class R2 {
        private String bucket = "";
        private String accessKey = "";
        private String secretKey = "";
        private String endpoint = "";

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public boolean isConfigured() {
            return hasText(bucket) && hasText(accessKey) && hasText(secretKey) && hasText(endpoint);
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
