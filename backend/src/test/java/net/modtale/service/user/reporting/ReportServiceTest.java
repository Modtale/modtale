package net.modtale.service.user.reporting;

import java.util.Optional;
import net.modtale.config.properties.AppLimitProperties;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.user.Report;
import net.modtale.model.user.User;
import net.modtale.model.project.Comment;
import net.modtale.model.project.ExternalProjectDiscussion;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.project.ExternalProjectDiscussionRepository;
import net.modtale.repository.user.ReportRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    private ReportService service;
    private AccountService accountService;
    private ReportRepository reportRepository;
    private ProjectRepository projectRepository;
    private ExternalProjectDiscussionRepository externalDiscussionRepository;
    private UserRepository userRepository;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        reportRepository = mock(ReportRepository.class);
        projectRepository = mock(ProjectRepository.class);
        externalDiscussionRepository = mock(ExternalProjectDiscussionRepository.class);
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        service = new ReportService(
                accountService,
                reportRepository,
                projectRepository,
                externalDiscussionRepository,
                userRepository,
                notificationService,
                new AppLimitProperties(10, 5, 10, 5, 5, 50, 20, 10)
        );
    }

    @Test
    void createReportThrowsWhenTheTargetProjectDoesNotExist() {
        User reporter = user("user-1", "ada");
        when(projectRepository.findById("project-1")).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.createReport("project-1", Report.TargetType.PROJECT, "Spam", "Repeated reposts", reporter)
        );
    }

    @Test
    void resolveReportThrowsWhenTheReportDoesNotExist() {
        when(reportRepository.findById("report-1")).thenReturn(Optional.empty());

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.resolveReport("report-1", Report.ReportStatus.RESOLVED, "Handled", user("admin-1", "mod"))
        );
    }

    @Test
    void externalProjectCommentsCanBeReported() {
        Comment comment = new Comment("author-1", "This external comment can be moderated.");
        ExternalProjectDiscussion discussion = new ExternalProjectDiscussion("CURSEFORGE", "1450386");
        discussion.setComments(java.util.List.of(comment));
        when(projectRepository.findByCommentsId(comment.getId())).thenReturn(Optional.empty());
        when(externalDiscussionRepository.findByCommentsId(comment.getId())).thenReturn(Optional.of(discussion));
        when(accountService.getPublicProfile("author-1")).thenReturn(user("author-1", "bea"));
        when(reportRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Report report = service.createReport(comment.getId(), Report.TargetType.COMMENT,
                "Spam", "External discussion report", user("user-1", "ada"));

        assertEquals("Comment by bea: This external comment can be moderated.", report.getTargetSummary());
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
