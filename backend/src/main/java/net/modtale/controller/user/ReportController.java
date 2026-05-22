package net.modtale.controller.user;

import net.modtale.mapper.AdminMapper;
import net.modtale.model.dto.admin.ReportDTO;
import net.modtale.model.dto.request.user.CreateReportRequest;
import net.modtale.model.dto.request.user.ResolveReportRequest;
import net.modtale.model.user.Report;
import net.modtale.model.user.User;
import net.modtale.service.user.ReportService;
import net.modtale.service.user.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ReportController {

    @Autowired private ReportService reportService;
    @Autowired private AccountService accountService;

    @PostMapping("/reports")
    public ResponseEntity<?> submitReport(@RequestBody CreateReportRequest requestPayload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        String targetId = requestPayload.getTargetId();
        String targetTypeStr = requestPayload.getTargetType();
        String reason = requestPayload.getReason();
        String description = requestPayload.getDescription();

        if (targetId == null || targetTypeStr == null || reason == null) {
            return ResponseEntity.badRequest().body("Target ID, Type, and Reason are required");
        }

        try {
            Report.TargetType targetType = Report.TargetType.valueOf(targetTypeStr);
            Report report = reportService.createReport(targetId, targetType, reason, description, user);
            return ResponseEntity.ok(Map.of("id", report.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid target type or data: " + e.getMessage());
        }
    }

    @GetMapping("/admin/reports/queue")
    public ResponseEntity<List<ReportDTO>> getReportQueue(@RequestParam(defaultValue = "OPEN") String status) {
        try {
            Report.ReportStatus reportStatus = Report.ReportStatus.valueOf(status);
            return ResponseEntity.ok(reportService.getReportsByStatus(reportStatus).stream()
                    .map(AdminMapper::toReportDTO)
                    .toList());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/admin/reports/{id}/resolve")
    public ResponseEntity<?> resolveReport(@PathVariable String id, @RequestBody ResolveReportRequest requestPayload) {
        User admin = accountService.getCurrentUser();
        if (admin == null) return ResponseEntity.status(401).build();

        String statusStr = requestPayload.getStatus();
        String note = requestPayload.getNote();

        try {
            Report.ReportStatus status = Report.ReportStatus.valueOf(statusStr);
            reportService.resolveReport(id, status, note, admin);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status or report ID");
        }
    }
}
