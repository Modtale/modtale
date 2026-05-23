package net.modtale.controller.finance;

import net.modtale.model.finance.PlatformFinanceSettings;
import net.modtale.service.finance.EarningsAccountService;
import net.modtale.service.finance.RevenueReportingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/finance")
public class RevenuePublicController {

    @Autowired private EarningsAccountService financeAccountService;
    @Autowired private RevenueReportingService financeReportingService;

    @GetMapping("/public/daily-revenue")
    public ResponseEntity<?> getPublicDailyRevenue(@RequestParam(defaultValue = "90") int days) {
        long secondsToMidnight = Duration.between(LocalDateTime.now(), LocalDateTime.now().toLocalDate().plusDays(1).atStartOfDay()).getSeconds();
        PlatformFinanceSettings settings = financeAccountService.getSettings();

        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(secondsToMidnight, TimeUnit.SECONDS).cachePublic())
                .body(Map.of(
                        "currency", settings.getCurrency(),
                        "days", Math.max(1, Math.min(365, days)),
                        "data", financeReportingService.getPublicDailyRevenue(days)
                ));
    }

    @GetMapping("/public/settings")
    public ResponseEntity<?> getPublicMonetizationSettings() {
        PlatformFinanceSettings settings = financeAccountService.getSettings();
        return ResponseEntity.ok(Map.of(
                "adCreatorSplitPercent", settings.getAdCreatorSplitBps() / 100.0,
                "donationPlatformCutPercent", settings.getDonationPlatformCutBps() / 100.0,
                "fundExpiryDays", settings.getFundExpiryDays()
        ));
    }
}
