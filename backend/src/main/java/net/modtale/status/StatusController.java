package net.modtale.status;

import jakarta.validation.constraints.Pattern;
import java.util.Map;
import net.modtale.status.StatusModels.SystemStatusView;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/status")
public class StatusController {

    private final DetachedStatusService detachedStatusService;

    public StatusController(DetachedStatusService detachedStatusService) {
        this.detachedStatusService = detachedStatusService;
    }

    @GetMapping
    public ResponseEntity<SystemStatusView> getSystemStatus(
            @RequestParam(defaultValue = "24h")
            @Pattern(regexp = "24h|30d", message = "Status ranges must be 24h or 30d.")
            String range
    ) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(detachedStatusService.getSystemStatus(range));
    }

    @GetMapping("/live")
    public ResponseEntity<Map<String, String>> live() {
        return ResponseEntity.ok(Map.of("status", "up"));
    }
}
