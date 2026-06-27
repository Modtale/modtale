package net.modtale.config.core;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    static final long WIKI_METADATA_CACHE_MAX_WEIGHT_BYTES = 4L * 1024L * 1024L;
    static final long WIKI_PAGE_CACHE_MAX_WEIGHT_BYTES = 12L * 1024L * 1024L;

    private static final List<String> CACHE_NAMES = List.of(
            "modTags",
            "projectDetails",
            "projectDetailDtos",
            "projectPageDtos",
            "projectVersionDtos",
            "projectCommentDtos",
            "projectGalleryDtos",
            "projectTeamDtos",
            "projectVersionChangelogs",
            "projectMetaDtos",
            "projectPermissionSnapshots",
            "modpackZips",
            "projectSearch",
            "projectSummarySearch",
            "projectMarqueeSearch",
            "projectMarqueeSummarySearch",
            "hytaleVersions",
            "allTags",
            "platformStats",
            "platformAnalytics",
            "creatorAnalytics",
            "projectAnalytics",
            "sitemapData",
            "analyticsDebounce",
            "wikiProjectPayload",
            "wikiPagePayload",
            "wikiProjectJson",
            "wikiPageJson",
            "wikiPageBundleJson"
    );

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(CACHE_NAMES.toArray(String[]::new));

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .maximumSize(10000)
                .recordStats());

        registerWeightedWikiCache(cacheManager, "wikiProjectPayload", WIKI_METADATA_CACHE_MAX_WEIGHT_BYTES, Duration.ofMinutes(30));
        registerWeightedWikiCache(cacheManager, "wikiProjectJson", WIKI_METADATA_CACHE_MAX_WEIGHT_BYTES, Duration.ofMinutes(30));
        registerWeightedWikiCache(cacheManager, "wikiPagePayload", WIKI_PAGE_CACHE_MAX_WEIGHT_BYTES, Duration.ofMinutes(30));
        registerWeightedWikiCache(cacheManager, "wikiPageJson", WIKI_PAGE_CACHE_MAX_WEIGHT_BYTES, Duration.ofMinutes(30));
        registerWeightedWikiCache(cacheManager, "wikiPageBundleJson", WIKI_PAGE_CACHE_MAX_WEIGHT_BYTES, Duration.ofMinutes(30));

        return cacheManager;
    }

    private void registerWeightedWikiCache(
            CaffeineCacheManager cacheManager,
            String cacheName,
            long maxWeightBytes,
            Duration ttl
    ) {
        cacheManager.registerCustomCache(cacheName, Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumWeight(maxWeightBytes)
                .weigher(CacheConfig::estimateCacheEntryWeight)
                .recordStats()
                .build());
    }

    private static int estimateCacheEntryWeight(Object key, Object value) {
        long weight = estimateObjectWeight(key) + estimateObjectWeight(value);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, weight));
    }

    private static long estimateObjectWeight(Object value) {
        if (value == null) return 0L;
        if (value instanceof String stringValue) {
            return 64L + (long) stringValue.length() * 2L;
        }
        if (value instanceof byte[] bytes) {
            return 64L + bytes.length;
        }
        return 256L;
    }
}
