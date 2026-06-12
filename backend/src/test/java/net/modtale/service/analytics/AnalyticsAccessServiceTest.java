package net.modtale.service.analytics;

import net.modtale.exception.ForbiddenOperationException;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.User;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsAccessServiceTest {

    private AnalyticsAccessService service;
    private AccountService accountService;
    private AccessControlService accessControlService;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        accessControlService = mock(AccessControlService.class);
        service = new AnalyticsAccessService(accountService, accessControlService);
    }

    @Test
    void resolveCreatorAnalyticsTargetIdFallsBackToTheCurrentUser() {
        User currentUser = user("user-1", User.AccountType.USER);

        assertEquals("user-1", service.resolveCreatorAnalyticsTargetId(currentUser, null));
        assertEquals("user-1", service.resolveCreatorAnalyticsTargetId(currentUser, "user-1"));
    }

    @Test
    void resolveCreatorAnalyticsTargetIdRejectsMissingOrNonOrganizationTargets() {
        User currentUser = user("user-1", User.AccountType.USER);
        User normalUser = user("user-2", User.AccountType.USER);

        when(accountService.getPublicProfile("user-2")).thenReturn(normalUser);

        assertThrows(
                ResourceNotFoundException.class,
                () -> service.resolveCreatorAnalyticsTargetId(currentUser, "user-2")
        );
    }

    @Test
    void assertProjectAnalyticsAccessRejectsDraftProjectsWithoutEditPermission() {
        Project project = new Project();
        project.setStatus(ProjectStatus.DRAFT);
        User currentUser = user("user-1", User.AccountType.USER);

        when(accessControlService.hasEditPermission(project, currentUser)).thenReturn(false);

        assertThrows(ForbiddenOperationException.class, () -> service.assertProjectAnalyticsAccess(project, currentUser));
    }

    @Test
    void assertProjectAnalyticsAccessAllowsPrivateProjectsWithoutEditPermission() {
        Project project = new Project();
        project.setStatus(ProjectStatus.PRIVATE);
        User currentUser = user("user-1", User.AccountType.USER);

        when(accessControlService.hasEditPermission(project, currentUser)).thenReturn(false);

        assertDoesNotThrow(() -> service.assertProjectAnalyticsAccess(project, currentUser));
    }

    private static User user(String id, User.AccountType accountType) {
        User user = new User();
        user.setId(id);
        user.setAccountType(accountType);
        return user;
    }
}
