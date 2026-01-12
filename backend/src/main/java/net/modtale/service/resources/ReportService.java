package net.modtale.service.resources;

import net.modtale.model.resources.Mod;
import net.modtale.model.resources.Report;
import net.modtale.model.user.User;
import net.modtale.repository.resources.ReportRepository;
import net.modtale.repository.resources.ModRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ReportService {

    @Autowired private ReportRepository reportRepository;
    @Autowired private ModRepository modRepository;

    public void createReport(String projectId, String reason, String description, User reporter) {
        Mod mod = modRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        Report report = new Report();
        report.setReporterId(reporter.getId());
        report.setReporterUsername(reporter.getUsername());
        report.setProjectId(mod.getId());
        report.setProjectTitle(mod.getTitle());
        report.setReason(reason);
        report.setDescription(description);
        report.setStatus(Report.ReportStatus.OPEN);
        report.setCreatedAt(LocalDateTime.now());

        reportRepository.save(report);
    }

    public List<Report> getOpenReports() {
        return reportRepository.findByStatus(Report.ReportStatus.OPEN);
    }

    public void resolveReport(String reportId, Report.ReportStatus status, String note, User admin) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new IllegalArgumentException("Report not found"));

        report.setStatus(status);
        report.setResolvedBy(admin.getUsername());
        report.setResolutionNote(note);

        reportRepository.save(report);
    }
}