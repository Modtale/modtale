package net.modtale.service.system;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;
import net.modtale.config.properties.AppBackendProperties;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.config.properties.PublicCachePurgeProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

/** Best-effort public-content invalidation, never an authorization or write-success dependency. */
@Service
public class PublicContentCacheInvalidator {
  private static final Logger logger = LoggerFactory.getLogger(PublicContentCacheInvalidator.class);
  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(2);
  private static final long COALESCE_MS = 120_000L;
  private static final String APPROVED_ZONE = "ff20a05c676ca023b19d20e177054945";

  private final PublicCachePurgeProperties properties;
  private final HttpClient http;
  private final ObjectMapper mapper;
  private final Clock clock;
  private final List<String> tags;
  private final AtomicLong requested = new AtomicLong();
  private final ReentrantLock flushLock = new ReentrantLock();
  private long completed;
  private long nextAttemptAt;
  private int failures;

  @Autowired
  public PublicContentCacheInvalidator(
      PublicCachePurgeProperties properties,
      AppFrontendProperties frontend,
      AppBackendProperties backend,
      ObjectMapper mapper) {
    this(
        properties,
        frontend,
        backend,
        mapper,
        HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build(),
        Clock.systemUTC());
  }

  PublicContentCacheInvalidator(
      PublicCachePurgeProperties properties,
      AppFrontendProperties frontend,
      AppBackendProperties backend,
      ObjectMapper mapper,
      HttpClient http,
      Clock clock) {
    this.properties = properties;
    this.http = http;
    this.mapper = mapper;
    this.clock = clock;
    String site = approvedHost(frontend.url());
    String api = approvedHost(backend.url());
    boolean matchingEnvironment =
        ("modtale.net".equals(site) && "api.modtale.net".equals(api))
            || ("dev.modtale.net".equals(site) && "dev.api.modtale.net".equals(api));
    this.tags =
        matchingEnvironment ? List.of("modtale-html-" + site, "modtale-api-" + api) : List.of();
    if (properties.enabled() && !isReady()) {
      logger.warn(
          "Public content cache purging is enabled but its approved environment/credential"
              + " configuration is incomplete; short TTLs remain the fallback");
    }
  }

  private static String approvedHost(String raw) {
    try {
      URI uri = URI.create(raw);
      if (!"https".equals(uri.getScheme())
          || uri.getUserInfo() != null
          || uri.getPort() != -1
          || uri.getRawQuery() != null
          || uri.getRawFragment() != null
          || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) return null;
      return uri.getHost();
    } catch (RuntimeException ex) {
      return null;
    }
  }

  private boolean isReady() {
    return properties.enabled()
        && APPROVED_ZONE.equals(properties.zoneId())
        && properties.token() != null
        && !properties.token().isBlank()
        && !tags.isEmpty();
  }

  public String apiCacheTag() {
    return tags.isEmpty() ? null : tags.get(1);
  }

  /** Call only after a successful content write; rolled-back transactions must not purge. */
  public void contentChanged() {
    if (!isReady()) return;
    if (TransactionSynchronizationManager.isActualTransactionActive()
        && TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              queueAndFlush();
            }
          });
    } else {
      queueAndFlush();
    }
  }

  private void queueAndFlush() {
    requested.incrementAndGet();
    // Cloud Run may suspend idle CPU: the first post-write attempt happens during the request.
    flushPending();
  }

  /** Also invoked by incoming content reads, so retries do not depend on idle background CPU. */
  @Scheduled(fixedDelay = 5000, initialDelay = 5000)
  public void flushPending() {
    if (!isReady() || !flushLock.tryLock()) return;
    try {
      long revision = requested.get();
      long now = clock.millis();
      if (revision <= completed || now < nextAttemptAt) return;
      nextAttemptAt = now + COALESCE_MS;
      try {
        HttpRequest request =
            HttpRequest.newBuilder()
                .uri(
                    URI.create(
                        "https://api.cloudflare.com/client/v4/zones/"
                            + APPROVED_ZONE
                            + "/purge_cache"))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + properties.token())
                .header("Content-Type", "application/json")
                // Fixed, environment-scoped tags cover all query/wiki/renamed-route variants.
                // Never purge everything, immutable files, user URLs, or private responses.
                .POST(
                    HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(Map.of("tags", tags))))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2
            || !mapper.readTree(response.body()).path("success").asBoolean(false)) {
          retryAfterFailure(now, response.headers().firstValue("Retry-After").orElse(null));
          logger.warn(
              "Public content cache purge was not accepted (HTTP {}); pending invalidation"
                  + " retained, short TTLs remain the fallback",
              response.statusCode());
          return;
        }
        completed = revision; // A concurrent content edit remains pending for the next batch.
        failures = 0;
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        retryAfterFailure(now, null);
        logger.warn("Public content cache purge was interrupted; pending invalidation retained");
      } catch (Exception ex) {
        retryAfterFailure(now, null);
        // Do not log exception text, response bodies, request headers, or credentials.
        logger.warn(
            "Public content cache purge failed ({}); pending invalidation retained, short TTLs"
                + " remain the fallback",
            ex.getClass().getSimpleName());
      }
    } finally {
      flushLock.unlock();
    }
  }

  private void retryAfterFailure(long now, String retryAfter) {
    failures = Math.min(failures + 1, 5);
    long backoff = Math.min(600_000L, 60_000L << (failures - 1));
    long serverDelay = 0;
    if (retryAfter != null) {
      try {
        serverDelay = Duration.ofSeconds(Long.parseLong(retryAfter)).toMillis();
      } catch (RuntimeException ex) {
        try {
          serverDelay =
              ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME)
                      .toInstant()
                      .toEpochMilli()
                  - now;
        } catch (RuntimeException ignored) {
          // Invalid header: use the conservative local backoff.
        }
      }
    }
    nextAttemptAt =
        now + Math.max(COALESCE_MS, Math.max(backoff, Math.min(86_400_000L, serverDelay)));
  }
}
