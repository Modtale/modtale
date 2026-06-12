package net.modtale.service.auth;

import java.util.Optional;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.exception.ReservedAccountAccessException;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReservedAccountGuardServiceTest {

    @Test
    void detectsProductionDeploymentsFromTheFrontendUrl() {
        UserRepository userRepository = mock(UserRepository.class);
        ReservedAccountGuardService service = new ReservedAccountGuardService(userRepository, frontend("http://localhost:5173"));
        assertFalse(service.isProductionDeployment());

        assertFalse(new ReservedAccountGuardService(userRepository, frontend("https://modtale-staging.run.app")).isProductionDeployment());
        assertTrue(new ReservedAccountGuardService(userRepository, frontend("https://modtale.net")).isProductionDeployment());
    }

    @Test
    void recognizesReservedEmailsCaseInsensitively() {
        ReservedAccountGuardService service = new ReservedAccountGuardService(mock(UserRepository.class), frontend("http://localhost:5173"));

        assertTrue(service.isReservedEmail(" Admin@Modtale.Net "));
        assertFalse(service.isReservedEmail("creator@modtale.net"));
        assertFalse(service.isReservedEmail(null));
    }

    @Test
    void rejectReservedEmailInProductionPurgesReservedAccountsAndThrows() {
        UserRepository userRepository = mock(UserRepository.class);
        ReservedAccountGuardService service = new ReservedAccountGuardService(userRepository, frontend("https://modtale.net"));

        User reservedUser = new User();
        reservedUser.setId("u1");
        reservedUser.setEmail("admin@modtale.net");

        when(userRepository.findByEmailIgnoreCase("admin@modtale.net")).thenReturn(Optional.of(reservedUser));
        when(userRepository.findByEmailIgnoreCase("super_admin@modtale.net")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("user@modtale.net")).thenReturn(Optional.empty());

        ReservedAccountAccessException error = assertThrows(
                ReservedAccountAccessException.class,
                () -> service.rejectReservedEmailInProduction("admin@modtale.net")
        );

        verify(userRepository).delete(reservedUser);
        verify(userRepository).findByEmailIgnoreCase("admin@modtale.net");
        assertTrue(error.getMessage().contains("Invalid credentials"));
    }

    @Test
    void rejectReservedUserInProductionDeletesTheAccountByIdAndThrows() {
        UserRepository userRepository = mock(UserRepository.class);
        ReservedAccountGuardService service = new ReservedAccountGuardService(userRepository, frontend("https://modtale.net"));

        User reservedUser = new User();
        reservedUser.setId("u1");
        reservedUser.setEmail("user@modtale.net");

        assertThrows(ReservedAccountAccessException.class, () -> service.rejectReservedUserInProduction(reservedUser));

        verify(userRepository).deleteById("u1");
    }

    @Test
    void skipsReservedChecksOutsideProduction() {
        UserRepository userRepository = mock(UserRepository.class);
        ReservedAccountGuardService service = new ReservedAccountGuardService(userRepository, frontend("http://localhost:5173"));

        service.rejectReservedEmailInProduction("admin@modtale.net");
        service.rejectReservedUserInProduction(new User());

        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
        verify(userRepository, never()).deleteById(anyString());
    }

    private static AppFrontendProperties frontend(String url) {
        return new AppFrontendProperties(url);
    }
}
