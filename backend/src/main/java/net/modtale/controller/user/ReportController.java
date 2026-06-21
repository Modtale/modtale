package net.modtale.controller.user;

import jakarta.validation.Valid;
import java.util.List;
import net.modtale.exception.ApiKeyOperationForbiddenException;
import net.modtale.mapper.AdminMapper;
import net.modtale.model.dto.admin.ReportDTO;
import net.modtale.model.dto.request.user.CreateReportRequest;
import net.modtale.model.dto.request.user.ResolveReportRequest;
import net.modtale.model.dto.response.common.IdResponse;
import net.modtale.model.user.Report;
import net.modtale.model.user.User;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.user.account.AccountService;
import net.modtale.service.user.reporting.ReportService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService reportService;
    private final AccountService accountService;
    private final AccessControlService accessControlService;

    public ReportController(
            ReportService reportService,
            AccountService accountService,
            AccessControlService accessControlService
    ) {
        this.reportService = reportService;
        this.accountService = accountService;
        this.accessControlService = accessControlService;
    }

    @PostMapping("/reports")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<IdResponse> submitReport(
            @Valid @RequestBody CreateReportRequest requestPayload,
            Authentication authentication
    ) {
        if (accessControlService.isApiKey(authentication)) {
            throw new ApiKeyOperationForbiddenException("API keys cannot be used for submitting reports.");
        }

        User user = accountService.requireCurrentUser(authentication, "submitting a report");
        Report report = reportService.createReport(
                requestPayload.getTargetId(),
                requestPayload.getTargetType(),
                requestPayload.getReason(),
                requestPayload.getDescription(),
                user
        );
        return ResponseEntity.ok(new IdResponse(report.getId()));
    }

    @GetMapping("/admin/reports/queue")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<List<ReportDTO>> getReportQueue(
            @RequestParam(defaultValue = "OPEN") Report.ReportStatus status
    ) {
        return ResponseEntity.ok(reportService.getReportsByStatus(status).stream()
                .map(AdminMapper::toReportDTO)
                .toList());
    }

    @PostMapping("/admin/reports/{id}/resolve")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<Void> resolveReport(@PathVariable String id, @Valid @RequestBody ResolveReportRequest requestPayload) {
        User admin = accountService.requireCurrentUser("resolving reports");
        reportService.resolveReport(id, requestPayload.getStatus(), requestPayload.getNote(), admin);
        return ResponseEntity.ok().build();
    }
}
