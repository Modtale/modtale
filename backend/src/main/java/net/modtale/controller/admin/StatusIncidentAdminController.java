package net.modtale.controller.admin;

import jakarta.validation.Valid;
import java.util.List;
import net.modtale.model.dto.request.admin.CreateStatusIncidentRequest;
import net.modtale.model.dto.request.admin.UpdateStatusIncidentRequest;
import net.modtale.model.dto.response.system.StatusIncidentView;
import net.modtale.model.user.User;
import net.modtale.service.system.StatusIncidentService;
import net.modtale.service.user.account.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/status/incidents")
public class StatusIncidentAdminController {

    private final AccountService accountService;
    private final StatusIncidentService statusIncidentService;

    public StatusIncidentAdminController(
            AccountService accountService,
            StatusIncidentService statusIncidentService
    ) {
        this.accountService = accountService;
        this.statusIncidentService = statusIncidentService;
    }

    @GetMapping
    @PreAuthorize("@apiSecurity.hasAdminPermission('STATUS_INCIDENT_READ', authentication)")
    public ResponseEntity<List<StatusIncidentView>> getIncidents() {
        return ResponseEntity.ok(statusIncidentService.getAdminIncidents());
    }

    @PostMapping
    @PreAuthorize("@apiSecurity.hasAdminPermission('STATUS_INCIDENT_MANAGE', authentication)")
    public ResponseEntity<StatusIncidentView> createIncident(@Valid @RequestBody CreateStatusIncidentRequest request) {
        User currentUser = accountService.requireCurrentUser("creating status incidents");
        return ResponseEntity.ok(statusIncidentService.createIncident(currentUser, request));
    }

    @PostMapping("/{id}/updates")
    @PreAuthorize("@apiSecurity.hasAdminPermission('STATUS_INCIDENT_MANAGE', authentication)")
    public ResponseEntity<StatusIncidentView> appendUpdate(
            @PathVariable String id,
            @Valid @RequestBody UpdateStatusIncidentRequest request
    ) {
        User currentUser = accountService.requireCurrentUser("updating status incidents");
        return ResponseEntity.ok(statusIncidentService.appendUpdate(currentUser, id, request));
    }
}
