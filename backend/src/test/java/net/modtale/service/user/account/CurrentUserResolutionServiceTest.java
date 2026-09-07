package net.modtale.service.user.account;

import java.util.List;
import java.util.Optional;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CurrentUserResolutionServiceTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final CurrentUserResolutionService service = new CurrentUserResolutionService(repository);

    @Test
    void missingStableIdDoesNotAuthenticateReusedUsername() {
        User oldPrincipal = new User();
        oldPrincipal.setId("deleted-account");
        oldPrincipal.setUsername("reused-name");
        when(repository.findById("deleted-account")).thenReturn(Optional.empty());
        var authentication = new UsernamePasswordAuthenticationToken(oldPrincipal, null, List.of());
        assertNull(service.resolveCurrentUser(authentication));
        verify(repository, never()).findByUsernameIgnoreCase(anyString());
    }

    @Test
    void currentAccountIsResolvedByIdAfterRename() {
        User principal = new User();
        principal.setId("account");
        principal.setUsername("previous-name");
        User current = new User();
        current.setId("account");
        current.setUsername("new-name");
        when(repository.findById("account")).thenReturn(Optional.of(current));
        assertSame(current, service.resolveCurrentUser(new UsernamePasswordAuthenticationToken(principal, null, List.of())));
        verify(repository, never()).findByUsernameIgnoreCase(anyString());
    }
}
