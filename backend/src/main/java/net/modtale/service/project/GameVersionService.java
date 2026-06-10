package net.modtale.service.project;

import jakarta.annotation.PostConstruct;
import net.modtale.config.properties.AppGameVersionProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class GameVersionService {
    private static final Logger logger = LoggerFactory.getLogger(GameVersionService.class);

    private final GameVersionCatalogSourceService catalogSourceService;
    private final GameVersionCatalogOrderingService catalogOrderingService;

    private volatile GameVersionCatalog cachedCatalog = new GameVersionCatalog(List.of(), List.of(), List.of(), List.of());
    private final Object refreshLock = new Object();

    public GameVersionService(MongoTemplate mongoTemplate, AppGameVersionProperties gameVersionProperties) {
        this(mongoTemplate, gameVersionProperties, new RestTemplate());
    }

    GameVersionService(MongoTemplate mongoTemplate, AppGameVersionProperties gameVersionProperties, RestTemplate restTemplate) {
        this.catalogSourceService = new GameVersionCatalogSourceService(mongoTemplate, gameVersionProperties, restTemplate);
        this.catalogOrderingService = new GameVersionCatalogOrderingService(gameVersionProperties);
    }

    @PostConstruct
    public void initialRefresh() {
        refreshCatalog();
    }

    @Scheduled(fixedDelayString = "#{@appGameVersionProperties.pollMs()}")
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

    private void refreshCatalog() {
        synchronized (refreshLock) {
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
    }

    private boolean hasCachedCatalog() {
        return !cachedCatalog.allVersions().isEmpty();
    }

    public record GameVersionEntry(String version, boolean preRelease, String sourceUrl) {}
    public record GameVersionCatalog(List<String> releaseVersions, List<String> preReleaseVersions, List<String> allVersions, List<GameVersionEntry> versions) {}
}
