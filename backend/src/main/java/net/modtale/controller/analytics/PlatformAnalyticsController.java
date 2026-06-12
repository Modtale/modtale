package net.modtale.controller.analytics;

import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import net.modtale.model.dto.response.analytics.PlatformAnalyticsSummaryView;
import net.modtale.model.dto.response.analytics.PlatformStatsView;
import net.modtale.service.analytics.PlatformStatsService;
import net.modtale.service.analytics.QueryService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/analytics/platform")
public class PlatformAnalyticsController {

    private final QueryService queryService;
    private final PlatformStatsService platformStatsService;

    public PlatformAnalyticsController(QueryService queryService, PlatformStatsService platformStatsService) {
        this.queryService = queryService;
        this.platformStatsService = platformStatsService;
    }

    private long getSecondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, midnight).getSeconds();
    }

    @GetMapping("/stats")
    public ResponseEntity<PlatformStatsView> getPublicStats() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(getSecondsUntilMidnight(), TimeUnit.SECONDS).cachePublic())
                .body(platformStatsService.getPublicStats());
    }

    @GetMapping("/full")
    @PreAuthorize("@apiSecurity.isSuperAdmin(authentication)")
    public ResponseEntity<PlatformAnalyticsSummaryView> getPlatformAnalytics(
            @RequestParam(defaultValue = "30d")
            @Pattern(regexp = "7d|30d|90d|1y", message = "Analytics ranges must be 7d, 30d, 90d, or 1y.")
            String range
    ) {
        var summary = queryService.getPlatformAnalytics(range);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(getSecondsUntilMidnight(), TimeUnit.SECONDS).cachePrivate())
                .body(PlatformAnalyticsSummaryView.from(summary));
    }
}
