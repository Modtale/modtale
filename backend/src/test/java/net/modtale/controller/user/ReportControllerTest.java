package net.modtale.controller.user;

import net.modtale.exception.ApiKeyOperationForbiddenException;
import net.modtale.model.dto.request.user.CreateReportRequest;
import net.modtale.model.dto.request.user.ResolveReportRequest;
import net.modtale.model.dto.response.common.IdResponse;
import net.modtale.model.user.Report;
import net.modtale.model.user.User;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.user.AccountService;
import net.modtale.service.user.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ReportControllerTest {

    private ReportController controller;
    private ReportService reportService;
    private AccountService accountService;
    private AccessControlService accessControlService;

    @BeforeEach
    void setUp() {
        reportService = mock(ReportService.class);
        accountService = mock(AccountService.class);
        accessControlService = mock(AccessControlService.class);
        controller = new ReportController(reportService, accountService, accessControlService);
    }

    @Test
    void submitReportReturnsTheCreatedReportId() {
        User reporter = user("user-1", "ada");
        Report report = new Report();
        report.setId("report-1");

        CreateReportRequest request = new CreateReportRequest();
        request.setTargetId("project-1");
        request.setTargetType("PROJECT");
        request.setReason("Spam");
        request.setDescription("Repeated reposts");

        when(accountService.requireCurrentUser((Authentication) null, "submitting a report")).thenReturn(reporter);
        when(reportService.createReport("project-1", Report.TargetType.PROJECT, "Spam", "Repeated reposts", reporter))
                .thenReturn(report);

        var response = controller.submitReport(request, null);

        assertEquals(200, response.getStatusCode().value());
        IdResponse body = response.getBody();
        assertEquals("report-1", body.id());
    }

    @Test
    void submitReportRejectsApiKeyRequests() {
        Authentication authentication = mock(Authentication.class);
        CreateReportRequest request = new CreateReportRequest();
        request.setTargetId("project-1");
        request.setTargetType("PROJECT");
        request.setReason("Spam");

        when(accessControlService.isApiKey(authentication)).thenReturn(true);

        assertThrows(ApiKeyOperationForbiddenException.class,
                () -> controller.submitReport(request, authentication));

        verifyNoInteractions(reportService);
    }

    @Test
    void resolveReportDelegatesUsingTheCurrentAdminUser() {
        User admin = user("admin-1", "mod");
        ResolveReportRequest request = new ResolveReportRequest();
        request.setStatus("RESOLVED");
        request.setNote("Handled");

        when(accountService.requireCurrentUser("resolving reports")).thenReturn(admin);

        var response = controller.resolveReport("report-1", request);

        assertEquals(200, response.getStatusCode().value());
        verify(reportService).resolveReport("report-1", Report.ReportStatus.RESOLVED, "Handled", admin);
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
