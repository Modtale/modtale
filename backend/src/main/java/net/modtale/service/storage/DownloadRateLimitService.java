package net.modtale.service.storage;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.modtale.exception.RateLimitExceededException;
import net.modtale.model.user.User;

final class DownloadRateLimitService {

    private final int modpackGenLimitPerHour;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    DownloadRateLimitService(int modpackGenLimitPerHour) {
        this.modpackGenLimitPerHour = modpackGenLimitPerHour;
    }

    void consumeModpackGeneration(User user) {
        consume(user, user == null ? null : user.getId(),
                "Modpack generation limit reached. Please wait a while before trying again.");
    }

    void consumeBundleGeneration(User user) {
        consume(user, user == null ? null : user.getId() + "_bundle", "Bundle limit reached. Wait 1 hour.");
    }

    private void consume(User user, String bucketKey, String message) {
        if (user == null) {
            return;
        }

        Bucket bucket = buckets.computeIfAbsent(bucketKey, key -> Bucket.builder()
                .addLimit(Bandwidth.classic(modpackGenLimitPerHour, Refill.greedy(modpackGenLimitPerHour, Duration.ofHours(1))))
                .build());
        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException(message);
        }
    }
}
