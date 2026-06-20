package net.modtale.service.auth;

import java.util.Optional;
import net.modtale.exception.InvalidAuthenticationRequestException;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LauncherAuthServiceTest {

    private UserRepository userRepository;
    private LauncherAuthService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        service = new LauncherAuthService(userRepository);
    }

    @Test
    void issueCodeAllowsOnlyLoopbackRedirectsAndConsumesOnce() {
        User user = new User();
        user.setId("user-1");

        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        LauncherAuthService.LauncherAuthGrant grant = service.issueCode(
                user,
                "http://127.0.0.1:49152/callback",
                "state-123"
        );

        assertNotNull(grant.code());
        assertEquals("http://127.0.0.1:49152/callback", grant.redirectUri());
        assertEquals("state-123", grant.state());
        assertTrue(grant.expiresIn() > 0);
        assertEquals(1, service.getActiveCodeCount());
        assertEquals(user, service.consumeCode(grant.code()));
        assertNull(service.consumeCode(grant.code()));
    }

    @Test
    void issueCodeRejectsExternalRedirects() {
        User user = new User();
        user.setId("user-1");

        assertThrows(
                InvalidAuthenticationRequestException.class,
                () -> service.issueCode(user, "https://evil.example/callback", "state")
        );
    }
}
