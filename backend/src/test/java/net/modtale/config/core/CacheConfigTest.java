package net.modtale.config.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.github.benmanes.caffeine.cache.Policy;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

class CacheConfigTest {

    @Test
    void wikiCachesUseByteWeightedBounds() {
        CacheManager cacheManager = new CacheConfig().cacheManager();

        assertCacheMaximum(cacheManager, "wikiProjectPayload", CacheConfig.WIKI_METADATA_CACHE_MAX_WEIGHT_BYTES);
        assertCacheMaximum(cacheManager, "wikiProjectJson", CacheConfig.WIKI_METADATA_CACHE_MAX_WEIGHT_BYTES);
        assertCacheMaximum(cacheManager, "wikiPagePayload", CacheConfig.WIKI_PAGE_CACHE_MAX_WEIGHT_BYTES);
        assertCacheMaximum(cacheManager, "wikiPageJson", CacheConfig.WIKI_PAGE_CACHE_MAX_WEIGHT_BYTES);
        assertCacheMaximum(cacheManager, "wikiPageBundleJson", CacheConfig.WIKI_PAGE_CACHE_MAX_WEIGHT_BYTES);
    }

    private void assertCacheMaximum(CacheManager cacheManager, String cacheName, long expectedMaximum) {
        Cache cache = cacheManager.getCache(cacheName);

        assertNotNull(cache);
        assertTrue(cache instanceof CaffeineCache);

        Policy.Eviction<Object, Object> eviction = ((CaffeineCache) cache).getNativeCache()
                .policy()
                .eviction()
                .orElseThrow();

        assertTrue(eviction.isWeighted());
        assertEquals(expectedMaximum, eviction.getMaximum());
    }
}
