package net.modtale.service.user;

import net.modtale.config.properties.AppLimitProperties;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.user.Report;
import net.modtale.model.user.User;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.ReportRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.communication.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportServiceTest {

    private ReportService service;
    private AccountService accountService;
    private ReportRepository reportRepository;
    private ProjectRepository projectRepository;
    private UserRepository userRepository;
    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        reportRepository = mock(ReportRepository.class);
        projectRepository = mock(ProjectRepository.class);
        userRepository = mock(UserRepository.class);
        notificationService = mock(NotificationService.class);
        service = new ReportService(
                accountService,
                reportRepository,
                projectRepository,
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

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
