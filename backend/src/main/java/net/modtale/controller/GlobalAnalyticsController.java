package net.modtale.controller;

import net.modtale.model.analytics.PlatformAnalyticsSummary;
import net.modtale.model.user.User;
import net.modtale.service.AnalyticsService;
import net.modtale.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/analytics")
public class GlobalAnalyticsController {

    private static final String SUPER_ADMIN_ID = "692620f7c2f3266e23ac0ded";

    @Autowired private AnalyticsService analyticsService;
    @Autowired private UserService userService;

    @GetMapping("/platform")
    public ResponseEntity<PlatformAnalyticsSummary> getPlatformAnalytics(@RequestParam(defaultValue = "30d") String range) {
        User user = userService.getCurrentUser();

        boolean isSuper = user != null && SUPER_ADMIN_ID.equals(user.getId());
        boolean isAdmin = user != null && user.getRoles() != null && user.getRoles().contains("ADMIN");

        if (!isSuper && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        return ResponseEntity.ok(analyticsService.getPlatformAnalytics(range));
    }
}