package net.modtale.service.security.scan;

import net.modtale.config.properties.AppLimitProperties;
import net.modtale.exception.RateLimitExceededException;
import net.modtale.model.user.User;
import net.modtale.service.security.access.AccessControlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScanThrottleServiceTest {

    private AccessControlService accessControlService;
    private ScanThrottleService service;

    @BeforeEach
    void setUp() {
        accessControlService = mock(AccessControlService.class);
        service = new ScanThrottleService(accessControlService, new AppLimitProperties(10, 5, 10, 1, 5, 50, 20, 10));
    }

    @Test
    void nullAndAdminUsersBypassRateLimits() {
        User admin = user("admin-1");
        when(accessControlService.isAdmin(admin)).thenReturn(true);

        service.enforceRescanLimit(null);
        service.enforceRescanLimit(admin);
        service.enforceRescanLimit(admin);

        verify(accessControlService, never()).isAdmin((User) null);
    }

    @Test
    void regularUsersAreLimitedByConfiguredDailyRescanCount() {
        User user = user("user-1");
        when(accessControlService.isAdmin(user)).thenReturn(false);

        service.enforceRescanLimit(user);

        assertThrows(RateLimitExceededException.class, () -> service.enforceRescanLimit(user));
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
