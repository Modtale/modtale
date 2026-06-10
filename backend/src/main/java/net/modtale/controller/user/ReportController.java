package net.modtale.controller.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import net.modtale.mapper.AdminMapper;
import net.modtale.model.dto.admin.ReportDTO;
import net.modtale.model.dto.request.user.CreateReportRequest;
import net.modtale.model.dto.request.user.ResolveReportRequest;
import net.modtale.model.dto.response.common.IdResponse;
import net.modtale.model.user.Report;
import net.modtale.model.user.User;
import net.modtale.service.user.ReportService;
import net.modtale.service.user.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

@RestController
@Validated
@RequestMapping("/api/v1")
public class ReportController {

    private final ReportService reportService;
    private final AccountService accountService;

    public ReportController(ReportService reportService, AccountService accountService) {
        this.reportService = reportService;
        this.accountService = accountService;
    }

    @PostMapping("/reports")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<IdResponse> submitReport(@Valid @RequestBody CreateReportRequest requestPayload) {
        User user = accountService.requireCurrentUser("submitting a report");
        Report.TargetType targetType = Report.TargetType.valueOf(requestPayload.getTargetType().toUpperCase(Locale.ROOT));
        Report report = reportService.createReport(
                requestPayload.getTargetId(),
                targetType,
                requestPayload.getReason(),
                requestPayload.getDescription(),
                user
        );
        return ResponseEntity.ok(new IdResponse(report.getId()));
    }

    @GetMapping("/admin/reports/queue")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<List<ReportDTO>> getReportQueue(
            @RequestParam(defaultValue = "OPEN")
            @Pattern(
                    regexp = "(?i)OPEN|RESOLVED|DISMISSED",
                    message = "Report statuses must be OPEN, RESOLVED, or DISMISSED."
            )
            String status
    ) {
        Report.ReportStatus reportStatus = Report.ReportStatus.valueOf(status.toUpperCase(Locale.ROOT));
        return ResponseEntity.ok(reportService.getReportsByStatus(reportStatus).stream()
                .map(AdminMapper::toReportDTO)
                .toList());
    }

    @PostMapping("/admin/reports/{id}/resolve")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<Void> resolveReport(@PathVariable String id, @Valid @RequestBody ResolveReportRequest requestPayload) {
        User admin = accountService.requireCurrentUser("resolving reports");
        Report.ReportStatus status = Report.ReportStatus.valueOf(requestPayload.getStatus().toUpperCase(Locale.ROOT));
        reportService.resolveReport(id, status, requestPayload.getNote(), admin);
        return ResponseEntity.ok().build();
    }
}
