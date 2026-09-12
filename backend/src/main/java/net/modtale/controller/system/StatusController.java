package net.modtale.controller.system;

import jakarta.validation.constraints.Pattern;
import net.modtale.model.dto.response.system.SystemStatusView;
import net.modtale.service.system.StatusSnapshotService;
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

    private final StatusSnapshotService statusSnapshotService;

    public StatusController(StatusSnapshotService statusSnapshotService) {
        this.statusSnapshotService = statusSnapshotService;
    }

    @GetMapping
    public ResponseEntity<SystemStatusView> getSystemStatus(
            @RequestParam(defaultValue = "24h")
            @Pattern(regexp = "24h|30d", message = "Status ranges must be 24h or 30d.")
            String range
    ) {
        return ResponseEntity.ok(statusSnapshotService.getSystemStatus(range));
    }
}
