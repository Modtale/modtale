package net.modtale.config.core;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(
                "modTags",
                "projectDetails",
                "projectDetailDtos",
                "projectVersionChangelogs",
                "projectMetaDtos",
                "projectPermissionSnapshots",
                "modpackZips",
                "projectSearch",
                "projectSummarySearch",
                "hytaleVersions",
                "allTags",
                "platformStats",
                "platformAnalytics",
                "creatorAnalytics",
                "projectAnalytics",
                "sitemapData",
                "analyticsDebounce",
                "wikiModId",
                "wikiSlugToId",
                "wikiModsPayload",
                "wikiProjectPayload",
                "wikiPagePayload"
        );

        cacheManager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.MINUTES)
                .maximumSize(10000)
                .recordStats());

        return cacheManager;
    }
}
