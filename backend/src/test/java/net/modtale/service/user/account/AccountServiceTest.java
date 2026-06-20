package net.modtale.service.user.account;

import java.util.Optional;
import net.modtale.model.user.LauncherSettingsSnapshot;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.security.validation.SanitizationService;
import net.modtale.service.user.connection.ConnectedAccountMutationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
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
                mock(net.modtale.service.security.validation.SanitizationService.class),
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

    @Test
    void launcherSettingsAreNormalizedBeforeSaving() {
        User user = user("user-1", "ada");
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LauncherSettingsSnapshot snapshot = new LauncherSettingsSnapshot();
        snapshot.setSettingsHash(" hash ");
        LauncherSettingsSnapshot.Preferences preferences = new LauncherSettingsSnapshot.Preferences();
        preferences.setGameVersion(" 1.0 ");
        snapshot.setPreferences(preferences);
        LauncherSettingsSnapshot.InstalledProject installed = new LauncherSettingsSnapshot.InstalledProject();
        installed.setProjectId(" project-1 ");
        installed.setSlug(" slug-one ");
        installed.setTitle(" Project One ");
        installed.setClassification(" MODPACK ");
        installed.setInstalledVersion(" 2.0 ");
        installed.setSource("");
        installed.setInstallType("");
        installed.setModpackUnlocked(true);
        installed.setDependencyProjectIds(java.util.List.of("dep-1", "dep-1", " "));
        LauncherSettingsSnapshot.InstalledProjectReference bundled =
                new LauncherSettingsSnapshot.InstalledProjectReference();
        bundled.setProjectId(" bundled-1 ");
        bundled.setSlug(" bundled-slug ");
        bundled.setVersionNumber(" 1.5 ");
        bundled.setSource(" MODTALE ");
        bundled.setExternalId(" external-one ");
        installed.setBundledProjects(java.util.List.of(bundled));
        snapshot.setInstalledProjects(java.util.List.of(installed));

        LauncherSettingsSnapshot saved = accountService.updateLauncherSettings("user-1", snapshot);

        assertEquals("hash", saved.getSettingsHash());
        assertEquals("1.0", saved.getPreferences().getGameVersion());
        assertEquals(1, saved.getInstalledProjects().size());
        assertEquals("project-1", saved.getInstalledProjects().getFirst().getProjectId());
        assertEquals("slug-one", saved.getInstalledProjects().getFirst().getSlug());
        assertEquals("Project One", saved.getInstalledProjects().getFirst().getTitle());
        assertEquals("MODPACK", saved.getInstalledProjects().getFirst().getClassification());
        assertEquals("MODTALE", saved.getInstalledProjects().getFirst().getSource());
        assertEquals("DIRECT", saved.getInstalledProjects().getFirst().getInstallType());
        assertTrue(saved.getInstalledProjects().getFirst().isModpackUnlocked());
        assertEquals(java.util.List.of("dep-1"), saved.getInstalledProjects().getFirst().getDependencyProjectIds());
        assertEquals("bundled-1",
                saved.getInstalledProjects().getFirst().getBundledProjects().getFirst().getProjectId());
        assertEquals("bundled-slug",
                saved.getInstalledProjects().getFirst().getBundledProjects().getFirst().getSlug());
        assertEquals("1.5",
                saved.getInstalledProjects().getFirst().getBundledProjects().getFirst().getVersionNumber());
        assertEquals("MODTALE",
                saved.getInstalledProjects().getFirst().getBundledProjects().getFirst().getSource());
        assertEquals("external-one",
                saved.getInstalledProjects().getFirst().getBundledProjects().getFirst().getExternalId());
        assertNotNull(saved.getUpdatedAt());
        verify(userRepository).save(argThat(savedUser -> savedUser.getLauncherSettings() == saved));
    }

    @Test
    void launcherPreferenceUpdatePreservesStoredInstalledProjects() {
        User user = user("user-1", "ada");
        LauncherSettingsSnapshot stored = new LauncherSettingsSnapshot();
        LauncherSettingsSnapshot.InstalledProject installed = new LauncherSettingsSnapshot.InstalledProject();
        installed.setProjectId("project-1");
        installed.setInstalledVersion("2.0");
        stored.setInstalledProjects(java.util.List.of(installed));
        user.setLauncherSettings(stored);
        when(userRepository.findById("user-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LauncherSettingsSnapshot update = new LauncherSettingsSnapshot();
        update.setSettingsHash(" full-local-hash ");
        LauncherSettingsSnapshot.Preferences preferences = new LauncherSettingsSnapshot.Preferences();
        preferences.setGameVersion(" 2.1 ");
        update.setPreferences(preferences);

        LauncherSettingsSnapshot saved = accountService.updateLauncherSettingsPreferences("user-1", update);

        assertEquals("full-local-hash", saved.getSettingsHash());
        assertEquals("2.1", saved.getPreferences().getGameVersion());
        assertEquals(1, saved.getInstalledProjects().size());
        assertEquals("project-1", saved.getInstalledProjects().getFirst().getProjectId());
        assertEquals("2.0", saved.getInstalledProjects().getFirst().getInstalledVersion());
        assertNotNull(saved.getUpdatedAt());
        verify(userRepository).save(argThat(savedUser -> savedUser.getLauncherSettings() == saved));
    }

    private static User user(String id, String username) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}
