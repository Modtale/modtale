package net.modtale.service.user;

import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceTest {

    private UserRepository userRepository;
    private OAuthAvatarHealingService oauthAvatarHealingService;
    private AccountService accountService;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        oauthAvatarHealingService = mock(OAuthAvatarHealingService.class);
        accountService = new AccountService(
                userRepository,
                mock(org.springframework.data.mongodb.core.MongoTemplate.class),
                mock(net.modtale.service.security.SanitizationService.class),
                mock(CurrentUserResolutionService.class),
                oauthAvatarHealingService,
                mock(AccountLifecycleService.class),
                mock(ConnectedAccountMutationService.class)
        );
    }

    @Test
    void getPublicProfileResolvesByUsername() {
        User user = user("user-1", "AzureDoom");
        when(userRepository.findById("AzureDoom")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("AzureDoom")).thenReturn(Optional.of(user));

        User result = accountService.getPublicProfile("AzureDoom");

        assertEquals(user, result);
        verify(oauthAvatarHealingService).maybeHealOAuthAvatar(user);
    }

    @Test
    void getPublicProfileResolvesLegacyHandleSuffixes() {
        User user = user("user-1", "AzureDoom");
        when(userRepository.findById("AzureDoom~user-1")).thenReturn(Optional.empty());
        when(userRepository.findByUsernameIgnoreCase("AzureDoom~user-1")).thenReturn(Optional.empty());
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));

        User result = accountService.getPublicProfile("AzureDoom~user-1");

        assertEquals(user, result);
        verify(oauthAvatarHealingService).maybeHealOAuthAvatar(user);
    }

    @Test
    void getPublicProfileReturnsNullForBlankIdentifiers() {
        assertNull(accountService.getPublicProfile(" "));
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
