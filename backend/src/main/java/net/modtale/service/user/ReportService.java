package net.modtale.service.user;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import net.modtale.model.resources.Mod;
import net.modtale.model.user.Report;
import net.modtale.model.resources.Comment;
import net.modtale.model.user.User;
import net.modtale.repository.user.ReportRepository;
import net.modtale.repository.resources.ModRepository;
import net.modtale.repository.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ReportService {

    @Autowired private ReportRepository reportRepository;
    @Autowired private ModRepository modRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private NotificationService notificationService;

    @Value("${app.limits.reports-per-day:10}")
    private int reportsPerDay;

    private final Map<String, Bucket> reportBuckets = new ConcurrentHashMap<>();

    public void createReport(String targetId, Report.TargetType targetType, String reason, String description, User reporter) {
        Bucket bucket = reportBuckets.computeIfAbsent(reporter.getId(),
                k -> Bucket.builder()
                        .addLimit(Bandwidth.classic(reportsPerDay, Refill.greedy(reportsPerDay, Duration.ofDays(1))))
                        .build());

        if (!bucket.tryConsume(1)) {
            throw new IllegalStateException("You have reached the daily limit for filing reports. Please contact support if this is urgent.");
        }

        String targetSummary = "Unknown Target";

        if (targetType == Report.TargetType.PROJECT) {
            Mod mod = modRepository.findById(targetId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found"));
            targetSummary = mod.getTitle();
        }
        else if (targetType == Report.TargetType.USER) {
            User user = userRepository.findById(targetId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            targetSummary = user.getUsername();
        }
        else if (targetType == Report.TargetType.COMMENT) {
            Mod mod = modRepository.findByCommentsId(targetId)
                    .orElseThrow(() -> new IllegalArgumentException("Comment not found (or associated project deleted)"));

            Optional<Comment> commentOpt = mod.getComments().stream()
                    .filter(c -> c.getId().equals(targetId))
                    .findFirst();

            if (commentOpt.isPresent()) {
                String content = commentOpt.get().getContent();
                targetSummary = "Comment by " + commentOpt.get().getUser() + ": " +
                        (content.length() > 50 ? content.substring(0, 47) + "..." : content);
            }
        }

        Report report = new Report();
        report.setReporterId(reporter.getId());
        report.setReporterUsername(reporter.getUsername());

        report.setTargetId(targetId);
        report.setTargetType(targetType);
        report.setTargetSummary(targetSummary);

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

        notificationService.sendNotification(
                List.of(report.getReporterId()),
                "Report Resolved",
                "Your report regarding " + report.getTargetSummary() + " has been resolved.",
                URI.create("/dashboard"),
                null
        );
    }
}