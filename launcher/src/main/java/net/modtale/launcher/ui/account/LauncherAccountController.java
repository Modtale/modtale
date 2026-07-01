package net.modtale.launcher.ui.account;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.scene.control.Label;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.api.ModtaleApiException;
import net.modtale.launcher.model.auth.SignInResponse;
import net.modtale.launcher.model.user.CurrentUser;
import net.modtale.launcher.sync.LauncherSettingsSyncService;
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.ui.settings.LauncherSettingsController;

public final class LauncherAccountController {

    private final ModtaleApiClient apiClient;
    private final LauncherSettingsController settingsController;
    private final Label accountStatus = new Label("Signed out");

    private LauncherFeedback feedback;
    private Runnable onAutoCheckUpdates = () -> {
    };
    private Runnable onSignedIn = () -> {
    };
    private Runnable onSignedOut = () -> {
    };
    private final List<Consumer<CurrentUser>> currentUserListeners = new CopyOnWriteArrayList<>();
    private volatile CurrentUser currentUser;
    private LauncherSettingsSyncService settingsSyncService;

    public LauncherAccountController(
            ModtaleApiClient apiClient,
            LauncherSettingsController settingsController
    ) {
        this.apiClient = apiClient;
        this.settingsController = settingsController;
    }

    public void attachFeedback(LauncherFeedback feedback) {
        this.feedback = feedback;
    }

    public void setOnAutoCheckUpdates(Runnable onAutoCheckUpdates) {
        this.onAutoCheckUpdates = onAutoCheckUpdates == null ? () -> {
        } : onAutoCheckUpdates;
    }

    public void setOnSignedIn(Runnable onSignedIn) {
        this.onSignedIn = onSignedIn == null ? () -> {
        } : onSignedIn;
    }

    public void setOnSignedOut(Runnable onSignedOut) {
        this.onSignedOut = onSignedOut == null ? () -> {
        } : onSignedOut;
    }

    public void setSettingsSyncService(LauncherSettingsSyncService settingsSyncService) {
        this.settingsSyncService = settingsSyncService;
    }

    public Label statusLabel() {
        return accountStatus;
    }

    public void addCurrentUserListener(Consumer<CurrentUser> listener) {
        if (listener == null) {
            return;
        }
        currentUserListeners.add(listener);
        Platform.runLater(() -> listener.accept(currentUser));
    }

    public boolean isSignedIn() {
        return currentUser != null;
    }

    public CurrentUser currentUser() {
        return currentUser;
    }

    public String displayName() {
        return currentUser == null ? "Signed out" : currentUser.toString();
    }

    public boolean isProjectLiked(String projectId) {
        return currentUser != null && currentUser.likesProject(projectId);
    }

    public void syncLocalSettings() {
        if (settingsSyncService != null) {
            settingsSyncService.syncAfterLocalChange();
        }
    }

    public String idleStatus() {
        return currentUser == null ? "Signed out" : "";
    }

    public CurrentUser ensureSignedIn() {
        if (currentUser != null) {
            return currentUser;
        }
        throw new ModtaleApiException("Sign in with Modtale before continuing.");
    }

    public void setCurrentUser(CurrentUser user) {
        currentUser = user;
        Platform.runLater(this::refreshStatus);
        if (user != null) {
            Platform.runLater(onSignedIn);
        }
    }

    public void restoreSession() {
        if (!apiClient.hasStoredSession()) {
            Platform.runLater(onSignedOut);
            return;
        }

        feedback.runAsync("Checking Modtale session...",
                apiClient::currentUser,
                user -> completeSignIn(user, true),
                error -> {
                    currentUser = null;
                    apiClient.clearStoredSession();
                    refreshStatus();
                    onSignedOut.run();
                    feedback.log("Please sign in with Modtale to continue.");
                });
    }

    public void signIn() {
        signInWithBrowser();
    }

    public void signIn(String username, char[] password, Consumer<SignInResult> onResult) {
        feedback.runAsync("Signing in with Modtale...",
                () -> signInLocally(username, password),
                result -> {
                    if (result.mfaRequired()) {
                        if (onResult != null) {
                            onResult.accept(result);
                        }
                        feedback.log("Two-factor authentication is required for this account.");
                        return;
                    }
                    completeSignIn(result.user(), true);
                    feedback.log("Signed in as " + result.user() + ". Downloads and updates will use your Modtale session.");
                    feedback.showToast("Signed in", "You're connected as " + result.user() + ".");
                    if (onResult != null) {
                        onResult.accept(result);
                    }
                },
                error -> {
                    currentUser = null;
                    refreshStatus();
                    onSignedOut.run();
                    if (onResult != null) {
                        onResult.accept(SignInResult.failed(error.getMessage()));
                    }
                });
    }

    public void validateMfa(String preAuthToken, String code, Consumer<SignInResult> onResult) {
        feedback.runAsync("Verifying two-factor code...",
                () -> completeMfaSignIn(preAuthToken, code),
                result -> {
                    completeSignIn(result.user(), true);
                    feedback.log("Signed in as " + result.user() + ". Downloads and updates will use your Modtale session.");
                    feedback.showToast("Signed in", "You're connected as " + result.user() + ".");
                    if (onResult != null) {
                        onResult.accept(result);
                    }
                },
                error -> {
                    if (onResult != null) {
                        onResult.accept(SignInResult.failed(error.getMessage()));
                    }
                });
    }

    public void signInWithBrowser() {
        feedback.runAsync("Opening Modtale sign-in in your browser...",
                () -> new LauncherAuthFlow(apiClient).authenticate(),
                user -> {
                    completeSignIn(user, true);
                    feedback.log("Signed in as " + user + ". Downloads and updates will use your Modtale session.");
                    feedback.showToast("Signed in", "You're connected as " + user + ".");
                },
                error -> {
                    currentUser = null;
                    refreshStatus();
                    onSignedOut.run();
                });
    }

    public void signInWithOAuthProvider(String provider, String label, Consumer<SignInResult> onResult) {
        String providerLabel = label == null || label.isBlank() ? "OAuth" : label;
        feedback.runAsync("Opening " + providerLabel + " sign-in...",
                () -> new LauncherAuthFlow(apiClient).authenticateWithOAuthProvider(provider),
                user -> {
                    completeSignIn(user, true);
                    feedback.log("Signed in as " + user + " with " + providerLabel + ".");
                    feedback.showToast("Signed in", "You're connected as " + user + ".");
                    if (onResult != null) {
                        onResult.accept(SignInResult.signedIn(user));
                    }
                },
                error -> {
                    currentUser = null;
                    refreshStatus();
                    onSignedOut.run();
                    if (onResult != null) {
                        onResult.accept(SignInResult.failed(error.getMessage()));
                    }
                });
    }

    public void signOut() {
        feedback.runAsync("Signing out...", () -> {
            apiClient.logout();
            return null;
        }, ignored -> {
            currentUser = null;
            refreshStatus();
            feedback.log("Signed out.");
            onSignedOut.run();
        }, error -> {
            currentUser = null;
            apiClient.clearStoredSession();
            refreshStatus();
            onSignedOut.run();
            feedback.log("Local Modtale session cleared.");
        });
    }

    private void completeSignIn(CurrentUser user, boolean autoCheckUpdates) {
        currentUser = user;
        refreshStatus();
        onSignedIn.run();
        if (settingsSyncService != null) {
            settingsSyncService.checkOnSignIn();
        }
        if (autoCheckUpdates
                && settingsController.settings().isAutoCheckUpdates()
                && !settingsController.settings().getInstalledProjects().isEmpty()) {
            onAutoCheckUpdates.run();
        }
    }

    private SignInResult signInLocally(String username, char[] password) {
        try {
            SignInResponse response = apiClient.signIn(username, password);
            if (response != null && response.mfaRequired()) {
                return SignInResult.mfa(response.preAuthToken());
            }
            return SignInResult.signedIn(apiClient.currentUser());
        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }
    }

    private SignInResult completeMfaSignIn(String preAuthToken, String code) {
        apiClient.validateMfa(preAuthToken, code);
        return SignInResult.signedIn(apiClient.currentUser());
    }

    private void refreshStatus() {
        accountStatus.setText(currentUser == null ? "Signed out" : "Signed in as " + currentUser);
        currentUserListeners.forEach(listener -> listener.accept(currentUser));
    }

    public record SignInResult(CurrentUser user, boolean mfaRequired, String preAuthToken, String errorMessage) {

        public static SignInResult signedIn(CurrentUser user) {
            return new SignInResult(user, false, null, null);
        }

        public static SignInResult mfa(String preAuthToken) {
            return new SignInResult(null, true, preAuthToken, null);
        }

        public static SignInResult failed(String errorMessage) {
            return new SignInResult(null, false, null, errorMessage);
        }

        public boolean success() {
            return user != null;
        }

        public boolean failed() {
            return errorMessage != null && !errorMessage.isBlank();
        }
    }
}
