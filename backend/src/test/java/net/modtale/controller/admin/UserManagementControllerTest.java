package net.modtale.controller.admin;

import net.modtale.model.user.User;
import net.modtale.service.admin.user.UserManagementService;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserManagementControllerTest {

    private UserManagementController controller;
    private AccountService accountService;
    private UserManagementService userManagementService;

    @BeforeEach
    void setUp() {
        accountService = mock(AccountService.class);
        userManagementService = mock(UserManagementService.class);
        controller = new UserManagementController(accountService, userManagementService);
    }

    @Test
    void banEmailUsesTheCurrentAdminContext() {
        User admin = new User();
        admin.setId("admin-1");
        when(accountService.requireCurrentUser("banning email addresses")).thenReturn(admin);

        var request = new net.modtale.model.dto.request.admin.BanEmailRequest();
        request.setEmail("bad@example.com");
        request.setReason("abuse");

        var response = controller.banEmail(request);

        assertEquals(200, response.getStatusCode().value());
        verify(userManagementService).banEmail(admin, "bad@example.com", "abuse");
    }
}
