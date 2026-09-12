package net.modtale.service.project.catalog;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.modtale.config.properties.AppGameVersionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GameVersionService {
    private static final Logger logger = LoggerFactory.getLogger(GameVersionService.class);
    private static final int MAX_VERSION_LENGTH = 128;
    private static final long UNKNOWN_VERSION_REFRESH_COOLDOWN_NANOS = TimeUnit.MINUTES.toNanos(1);

    private final GameVersionCatalogSourceService catalogSourceService;
    private final GameVersionCatalogOrderingService catalogOrderingService;

    private volatile GameVersionCatalog cachedCatalog = new GameVersionCatalog(List.of(), List.of(), List.of(), List.of());
    private final Object refreshLock = new Object();
    private volatile long lastUnknownVersionRefreshNanos = Long.MIN_VALUE;

    @Autowired
    public GameVersionService(MongoTemplate mongoTemplate, AppGameVersionProperties gameVersionProperties) {
        this(mongoTemplate, gameVersionProperties, createMavenRestTemplate());
    }

    GameVersionService(MongoTemplate mongoTemplate, AppGameVersionProperties gameVersionProperties, RestTemplate restTemplate) {
        this.catalogSourceService = new GameVersionCatalogSourceService(mongoTemplate, gameVersionProperties, restTemplate);
        this.catalogOrderingService = new GameVersionCatalogOrderingService(gameVersionProperties);
    }

    @PostConstruct
    public void initialRefresh() {
        refreshCatalog();
    }

    @Scheduled(fixedDelayString = "${app.hytale.maven.poll-ms:3600000}")
    public void pollCatalog() {
        refreshCatalog();
    }

    public GameVersionCatalog getCatalog() {
        if (!cachedCatalog.allVersions().isEmpty()) {
            return cachedCatalog;
        }
        refreshCatalog();
        return cachedCatalog;
    }

    /**
     * Checks the cached catalog and, for a missing value, performs at most one
     * upstream refresh per cooldown window before failing closed.
     */
    public boolean isVersionSupported(String version) {
        if (version == null || version.isBlank() || version.length() > MAX_VERSION_LENGTH) {
            return false;
        }
        if (containsVersion(cachedCatalog, version)) {
            return true;
        }

        synchronized (refreshLock) {
            // Another request may have refreshed the catalog while this request
            // was waiting for the lock.
            if (containsVersion(cachedCatalog, version)) {
                return true;
            }

            long now = System.nanoTime();
            if (lastUnknownVersionRefreshNanos != Long.MIN_VALUE
                    && now - lastUnknownVersionRefreshNanos < UNKNOWN_VERSION_REFRESH_COOLDOWN_NANOS) {
                return false;
            }

            // Set this before the network call so failures cannot be used to
            // force another refresh on every upload.
            lastUnknownVersionRefreshNanos = now;
            try {
                refreshCatalogLocked();
            } catch (RuntimeException ex) {
                logger.warn("Unable to refresh Hytale game versions while validating an unknown version.", ex);
                return false;
            }
            return containsVersion(cachedCatalog, version);
        }
    }

    private void refreshCatalog() {
        synchronized (refreshLock) {
            refreshCatalogLocked();
        }
    }

    private void refreshCatalogLocked() {
        try {
            GameVersionCatalogSourceService.GameVersionCatalogSource catalogSource =
                    catalogSourceService.fetchCatalogSource();

            if (catalogSource.releaseVersions().isEmpty()
                    && catalogSource.preReleaseVersions().isEmpty()
                    && catalogSource.indexedVersions().isEmpty()) {
                IllegalStateException failure = new IllegalStateException("Fetched an empty game version catalog from both upstream metadata feeds and indexed projects.");
                if (hasCachedCatalog()) {
                    logger.warn("Game version refresh returned no entries; keeping the previous cached catalog.", failure);
                    return;
                }
                throw failure;
            }

            GameVersionCatalog nextCatalog = catalogOrderingService.buildCatalog(catalogSource);
            if (nextCatalog.allVersions().isEmpty()) {
                IllegalStateException failure = new IllegalStateException("Built an empty game version catalog after merging upstream and indexed versions.");
                if (hasCachedCatalog()) {
                    logger.warn("Game version refresh produced an empty merged catalog; keeping the previous cached catalog.", failure);
                    return;
                }
                throw failure;
            }

            cachedCatalog = nextCatalog;
        } catch (RuntimeException e) {
            if (hasCachedCatalog()) {
                logger.warn("Failed to refresh Hytale game versions; keeping the last known good catalog.", e);
                return;
            }
            throw e;
        }
    }

    private boolean containsVersion(GameVersionCatalog catalog, String version) {
        return catalog != null
                && catalog.allVersions() != null
                && catalog.allVersions().contains(version);
    }

    private boolean hasCachedCatalog() {
        return !cachedCatalog.allVersions().isEmpty();
    }

    private static RestTemplate createMavenRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(3_000);
        requestFactory.setReadTimeout(5_000);
        return new RestTemplate(requestFactory);
    }

    public record GameVersionEntry(String version, boolean preRelease, String sourceUrl) {}
    public record GameVersionCatalog(List<String> releaseVersions, List<String> preReleaseVersions, List<String> allVersions, List<GameVersionEntry> versions) {}
}
