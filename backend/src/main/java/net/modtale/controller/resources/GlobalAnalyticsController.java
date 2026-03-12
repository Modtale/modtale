package net.modtale.controller.resources;

import net.modtale.model.analytics.PlatformAnalyticsSummary;
import net.modtale.model.user.User;
import net.modtale.service.AnalyticsService;
import net.modtale.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/admin/analytics")
public class GlobalAnalyticsController {

    private static final String SUPER_ADMIN_ID = "692620f7c2f3266e23ac0ded";

    @Autowired private AnalyticsService analyticsService;
    @Autowired private UserService userService;

    private long getSecondsUntilMidnight() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime midnight = now.toLocalDate().plusDays(1).atStartOfDay();
        return Duration.between(now, midnight).getSeconds();
    }

    @GetMapping("/platform")
    public ResponseEntity<PlatformAnalyticsSummary> getPlatformAnalytics(@RequestParam(defaultValue = "30d") String range) {
        User user = userService.getCurrentUser();

        boolean isSuper = user != null && SUPER_ADMIN_ID.equals(user.getId());
        boolean isAdmin = user != null && user.getRoles() != null && user.getRoles().contains("ADMIN");

        if (!isSuper && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(getSecondsUntilMidnight(), TimeUnit.SECONDS).cachePrivate())
                .body(analyticsService.getPlatformAnalytics(range));
    }
}