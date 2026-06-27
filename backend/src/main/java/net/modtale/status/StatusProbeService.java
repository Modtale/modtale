package net.modtale.status;

import com.mongodb.MongoException;
import com.mongodb.MongoClientSettings;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.modtale.status.StatusModels.ProbeResult;
import net.modtale.status.StatusModels.StatusHistoryEntry;
import net.modtale.status.StatusModels.SystemStatus;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;

@Service
public class StatusProbeService {

    private static final Logger logger = LoggerFactory.getLogger(StatusProbeService.class);

    private final StatusServiceProperties properties;
    private final HttpClient httpClient;
    private volatile S3Client s3Client;

    public StatusProbeService(StatusServiceProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getRequestTimeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    public StatusHistoryEntry performHealthCheck() {
        ProbeResult site = checkHttp("site", "Main Site", properties.getTargetSiteUrl());
        ProbeResult api = checkHttp("api", "API Gateway", properties.getTargetApiUrl());
        ProbeResult database = checkMongo();
        ProbeResult storage = checkStorage();
        SystemStatus overall = summarize(List.of(site, api, database, storage));

        return new StatusHistoryEntry(
                Instant.now(),
                site.latency(),
                api.latency(),
                database.latency(),
                storage.latency(),
                overall,
                site.status(),
                api.status(),
                database.status(),
                storage.status()
        );
    }

    private ProbeResult checkHttp(String id, String name, String url) {
        long start = System.currentTimeMillis();
        if (!hasText(url)) {
            return new ProbeResult(id, name, SystemStatus.OUTAGE, 0);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(properties.getRequestTimeout())
                    .GET()
                    .header("Accept", "application/json,text/html;q=0.9,*/*;q=0.8")
                    .header("User-Agent", "ModtaleStatusService/1.0")
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int latency = elapsedMillis(start);
            int status = response.statusCode();
            SystemStatus health = status >= 200 && status < 400 ? statusFromLatency(latency) : SystemStatus.OUTAGE;
            return new ProbeResult(id, name, health, latency);
        } catch (Exception e) {
            logger.warn("Status probe failed for {} at {}: {}", id, url, e.toString());
            return new ProbeResult(id, name, SystemStatus.OUTAGE, elapsedMillis(start));
        }
    }

    private ProbeResult checkMongo() {
        long start = System.currentTimeMillis();
        if (!hasText(properties.getMongoUri())) {
            return new ProbeResult("database", "Database (Atlas)", SystemStatus.OUTAGE, 0);
        }

        try (MongoClient client = MongoClients.create(mongoSettings())) {
            client.getDatabase(properties.getMongoDatabase()).runCommand(new Document("ping", 1));
            int latency = elapsedMillis(start);
            return new ProbeResult("database", "Database (Atlas)", statusFromLatency(latency), latency);
        } catch (MongoException | IllegalArgumentException e) {
            logger.warn("Status probe failed for MongoDB: {}", e.toString());
            return new ProbeResult("database", "Database (Atlas)", SystemStatus.OUTAGE, elapsedMillis(start));
        }
    }

    private ProbeResult checkStorage() {
        long start = System.currentTimeMillis();
        StatusServiceProperties.R2 r2 = properties.getR2();
        if (r2 == null || !r2.isConfigured()) {
            return new ProbeResult("storage", "Storage (R2)", SystemStatus.OUTAGE, 0);
        }

        try {
            s3().headBucket(HeadBucketRequest.builder().bucket(r2.getBucket()).build());
            int latency = elapsedMillis(start);
            return new ProbeResult("storage", "Storage (R2)", statusFromLatency(latency), latency);
        } catch (SdkException | IllegalArgumentException e) {
            logger.warn("Status probe failed for R2: {}", e.toString());
            return new ProbeResult("storage", "Storage (R2)", SystemStatus.OUTAGE, elapsedMillis(start));
        }
    }

    private S3Client s3() {
        S3Client current = s3Client;
        if (current != null) {
            return current;
        }

        synchronized (this) {
            if (s3Client != null) {
                return s3Client;
            }

            StatusServiceProperties.R2 r2 = properties.getR2();
            URI endpoint = URI.create(r2.getEndpoint());
            URI cleanEndpoint = URI.create(endpoint.getScheme() + "://" + endpoint.getAuthority());
            Duration timeout = properties.getRequestTimeout();
            s3Client = S3Client.builder()
                    .endpointOverride(cleanEndpoint)
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(r2.getAccessKey(), r2.getSecretKey())
                    ))
                    .overrideConfiguration(ClientOverrideConfiguration.builder()
                            .apiCallAttemptTimeout(timeout)
                            .apiCallTimeout(timeout)
                            .build())
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build())
                    .build();
            return s3Client;
        }
    }

    private SystemStatus statusFromLatency(int latency) {
        return latency > 5000 ? SystemStatus.DEGRADED : SystemStatus.OPERATIONAL;
    }

    private SystemStatus summarize(List<ProbeResult> probes) {
        long outages = probes.stream().filter(probe -> probe.status() == SystemStatus.OUTAGE).count();
        if (outages == probes.size()) {
            return SystemStatus.OUTAGE;
        }
        if (outages > 0 || probes.stream().anyMatch(probe -> probe.status() == SystemStatus.DEGRADED)) {
            return SystemStatus.DEGRADED;
        }
        return SystemStatus.OPERATIONAL;
    }

    private int elapsedMillis(long start) {
        return Math.max(1, (int) (System.currentTimeMillis() - start));
    }

    private MongoClientSettings mongoSettings() {
        Duration timeout = properties.getRequestTimeout();
        long timeoutMillis = Math.max(1, timeout.toMillis());
        return MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(properties.getMongoUri()))
                .applyToClusterSettings(settings -> settings.serverSelectionTimeout(timeoutMillis, TimeUnit.MILLISECONDS))
                .applyToSocketSettings(settings -> {
                    settings.connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
                    settings.readTimeout(timeoutMillis, TimeUnit.MILLISECONDS);
                })
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
