package net.modtale.service.storage;

import net.modtale.exception.RateLimitExceededException;
import net.modtale.model.user.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DownloadRateLimitServiceTest {

    @Test
    void anonymousUsersBypassModpackAndBundleLimits() {
        DownloadRateLimitService service = new DownloadRateLimitService(1);

        service.consumeModpackGeneration(null);
        service.consumeModpackGeneration(null);
        service.consumeBundleGeneration(null);
        service.consumeBundleGeneration(null);
    }

    @Test
    void modpackAndBundleLimitsUseSeparateBucketsPerUser() {
        DownloadRateLimitService service = new DownloadRateLimitService(1);
        User user = new User();
        user.setId("user-1");

        service.consumeModpackGeneration(user);
        service.consumeBundleGeneration(user);

        assertThrows(RateLimitExceededException.class, () -> service.consumeModpackGeneration(user));
        assertThrows(RateLimitExceededException.class, () -> service.consumeBundleGeneration(user));
    }
}
