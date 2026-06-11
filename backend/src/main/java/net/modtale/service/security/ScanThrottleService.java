package net.modtale.service.security;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import net.modtale.config.properties.AppLimitProperties;
import net.modtale.exception.RateLimitExceededException;
import net.modtale.model.user.User;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ScanThrottleService {

    private final AccessControlService accessControlService;
    private final int rescanLimitPerDay;
    private final Map<String, Bucket> rescanBuckets = new ConcurrentHashMap<>();

    public ScanThrottleService(AccessControlService accessControlService, AppLimitProperties limitProperties) {
        this.accessControlService = accessControlService;
        this.rescanLimitPerDay = limitProperties.rescansPerDay();
    }

    public void enforceRescanLimit(User user) {
        if (user == null || accessControlService.isAdmin(user)) {
            return;
        }

        Bucket bucket = rescanBuckets.computeIfAbsent(user.getId(),
                key -> Bucket.builder()
                        .addLimit(Bandwidth.classic(
                                rescanLimitPerDay,
                                Refill.greedy(rescanLimitPerDay, Duration.ofDays(1))
                        ))
                        .build());
        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException(
                    "You've reached the daily rescan limit. Please wait 24 hours before requesting another rescan."
            );
        }
    }
}
