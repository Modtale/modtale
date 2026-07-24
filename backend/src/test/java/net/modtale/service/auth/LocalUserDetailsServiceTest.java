package net.modtale.service.auth;

import java.util.List;
import java.util.Optional;
import net.modtale.exception.ReservedAccountAccessException;
import net.modtale.model.user.AdminPermission;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalUserDetailsServiceTest {

    private UserRepository userRepository;
    private ReservedAccountGuardService reservedAccountGuardService;
    private LocalUserDetailsService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        reservedAccountGuardService = mock(ReservedAccountGuardService.class);
        service = new LocalUserDetailsService(userRepository, reservedAccountGuardService);
    }

    @Test
    void loadUserByUsernameBuildsSpringUserWithRolesAndBlankPasswordFallback() {
        User user = user("user-1", "willow", null, List.of("USER", "ADMIN"));

        when(userRepository.findByUsernameIgnoreCase("willow")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("willow");

        assertEquals("willow", details.getUsername());
        assertEquals("", details.getPassword());
        assertTrue(details.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
        assertFalse(details.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().startsWith("ADMIN_PERMISSION_")));
        verify(reservedAccountGuardService).rejectReservedUserInProduction(user);
    }

    @Test
    void loadUserByUsernameAddsExplicitAdminPermissionAuthorities() {
        User user = user("user-1", "willow", "encoded-password", List.of("USER"));
        user.setAdminPermissions(java.util.Set.of(AdminPermission.PROJECT_MANAGE_READ));

        when(userRepository.findByUsernameIgnoreCase("willow")).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("willow");

        assertTrue(details.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals("ADMIN_PERMISSION_PROJECT_MANAGE_READ")));
    }

    @Test
    void loadUserByUsernameFallsBackToEmailLookup() {
        User user = user("user-1", "willow", "encoded-password", List.of("USER"));

        when(userRepository.findByUsernameIgnoreCase("willow@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("willow@example.com")).thenReturn(Optional.of(user));

        assertEquals("encoded-password", service.loadUserByUsername("willow@example.com").getPassword());
    }

    @Test
    void loadUserByUsernameHidesMissingAndReservedAccounts() {
        User reserved = user("reserved", "admin", "encoded", List.of("USER"));

        when(userRepository.findByUsernameIgnoreCase("missing")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("missing")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("admin")).thenReturn(Optional.of(reserved));
        doThrow(new ReservedAccountAccessException("blocked"))
                .when(reservedAccountGuardService)
                .rejectReservedUserInProduction(reserved);

        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("missing"));
        assertThrows(UsernameNotFoundException.class, () -> service.loadUserByUsername("admin"));
    }

    private static User user(String id, String username, String password, List<String> roles) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(password);
        user.setRoles(roles);
        return user;
    }
}
