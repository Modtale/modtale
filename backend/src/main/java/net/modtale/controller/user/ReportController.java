package net.modtale.controller.user;

import net.modtale.model.user.Report;
import net.modtale.model.user.User;
import net.modtale.service.user.ReportService;
import net.modtale.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ReportController {

    @Autowired private ReportService reportService;
    @Autowired private UserService userService;

    @PostMapping("/reports")
    public ResponseEntity<?> submitReport(@RequestBody Map<String, String> payload) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        String targetId = payload.get("targetId");
        String targetTypeStr = payload.get("targetType");
        String reason = payload.get("reason");
        String description = payload.get("description");

        if (targetId == null || targetTypeStr == null || reason == null) {
            return ResponseEntity.badRequest().body("Target ID, Type, and Reason are required");
        }

        try {
            Report.TargetType targetType = Report.TargetType.valueOf(targetTypeStr);
            reportService.createReport(targetId, targetType, reason, description, user);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid target type or data: " + e.getMessage());
        }
    }

    @GetMapping("/admin/reports/queue")
    public ResponseEntity<List<Report>> getReportQueue() {
        return ResponseEntity.ok(reportService.getOpenReports());
    }

    @PostMapping("/admin/reports/{id}/resolve")
    public ResponseEntity<?> resolveReport(@PathVariable String id, @RequestBody Map<String, String> payload) {
        User admin = userService.getCurrentUser();
        if (admin == null) return ResponseEntity.status(401).build();

        String statusStr = payload.get("status");
        String note = payload.get("note");

        try {
            Report.ReportStatus status = Report.ReportStatus.valueOf(statusStr);
            reportService.resolveReport(id, status, note, admin);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Invalid status or report ID");
        }
    }
}