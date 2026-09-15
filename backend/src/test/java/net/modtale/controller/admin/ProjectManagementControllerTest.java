package net.modtale.controller.admin;

import net.modtale.model.user.User;
import net.modtale.service.admin.project.ProjectAdminOperationsService;
import net.modtale.service.admin.review.ProjectReviewAdminService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectManagementControllerTest {

    private ProjectManagementController controller;
    private AccountService accountService;
    private ProjectReviewAdminService projectReviewAdminService;
    private ProjectAdminOperationsService projectAdminOperationsService;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        projectReviewAdminService = mock(ProjectReviewAdminService.class);
        projectAdminOperationsService = mock(ProjectAdminOperationsService.class);
        controller = new ProjectManagementController(accountService, projectReviewAdminService, projectAdminOperationsService);
    }

    @Test
    void publishProjectDelegatesWithTheResolvedCurrentUser() {
        User admin = new User();
        admin.setId("admin-1");
        when(accountService.requireCurrentUser("publishing projects")).thenReturn(admin);

        var response = controller.publishProject("project-1");

        assertEquals(200, response.getStatusCode().value());
        verify(projectReviewAdminService).publishProject(admin, "project-1");
    }
}
