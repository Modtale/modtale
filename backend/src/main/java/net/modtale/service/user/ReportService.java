package net.modtale.service.user;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
import net.modtale.config.properties.AppLimitProperties;
import net.modtale.exception.RateLimitExceededException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.model.user.Report;
import net.modtale.model.project.Comment;
import net.modtale.model.user.User;
import net.modtale.repository.user.ReportRepository;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.communication.NotificationService;
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

    private final AccountService accountService;
    private final ReportRepository reportRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final int reportsPerDay;

    private final Map<String, Bucket> reportBuckets = new ConcurrentHashMap<>();

    public ReportService(
            AccountService accountService,
            ReportRepository reportRepository,
            ProjectRepository projectRepository,
            UserRepository userRepository,
            NotificationService notificationService,
            AppLimitProperties limitProperties
    ) {
        this.accountService = accountService;
        this.reportRepository = reportRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
        this.reportsPerDay = limitProperties.reportsPerDay();
    }

    public Report createReport(String targetId, Report.TargetType targetType, String reason, String description, User reporter) {
        Bucket bucket = reportBuckets.computeIfAbsent(reporter.getId(),
                k -> Bucket.builder()
                        .addLimit(Bandwidth.classic(reportsPerDay, Refill.greedy(reportsPerDay, Duration.ofDays(1))))
                        .build());

        if (!bucket.tryConsume(1)) {
            throw new RateLimitExceededException("You have reached the daily limit for filing reports. Please contact support if this is urgent.");
        }

        String targetSummary = "Unknown Target";

        if (targetType == Report.TargetType.PROJECT) {
            Project project = projectRepository.findById(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Project not found."));
            targetSummary = project.getTitle();
        }
        else if (targetType == Report.TargetType.USER) {
            User user = userRepository.findById(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found."));
            targetSummary = user.getUsername();
        }
        else if (targetType == Report.TargetType.COMMENT) {
            Project project = projectRepository.findByCommentsId(targetId)
                    .orElseThrow(() -> new ResourceNotFoundException("Comment not found or the associated project was deleted."));

            Optional<Comment> commentOpt = project.getComments().stream()
                    .filter(c -> c.getId().equals(targetId))
                    .findFirst();

            if (commentOpt.isPresent()) {
                String content = commentOpt.get().getContent();

                User commentAuthor = accountService.getPublicProfile(commentOpt.get().getUserId());
                String authorName = commentAuthor != null ? commentAuthor.getUsername() : "Unknown User";

                targetSummary = "Comment by " + authorName + ": " +
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

        return reportRepository.save(report);
    }

    public List<Report> getOpenReports() {
        return getReportsByStatus(Report.ReportStatus.OPEN);
    }

    public List<Report> getReportsByStatus(Report.ReportStatus status) {
        return reportRepository.findByStatus(status);
    }

    public void resolveReport(String reportId, Report.ReportStatus status, String note, User admin) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report not found."));

        report.setStatus(status);
        report.setResolvedBy(admin.getUsername());
        report.setResolutionNote(note);

        reportRepository.save(report);

        String message = "Your report regarding " + report.getTargetSummary() + " has been " + status.name().toLowerCase() + ".";

        if (note != null && !note.trim().isEmpty()) {
            message += "\n\nModerator response: " + note;
        }

        String title = status == Report.ReportStatus.RESOLVED ? "Report Resolved" : "Report Dismissed";

        notificationService.sendNotifcation(
                List.of(report.getReporterId()),
                title,
                message,
                URI.create("/dashboard"),
                null
        );
    }
}
