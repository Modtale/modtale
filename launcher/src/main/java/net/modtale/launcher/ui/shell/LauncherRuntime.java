package net.modtale.launcher.ui.shell;

import java.util.Objects;
import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import net.modtale.launcher.ui.account.LauncherAccountController;
import net.modtale.launcher.ui.activity.LauncherFollowingController;
import net.modtale.launcher.ui.activity.LauncherNotificationsController;
import net.modtale.launcher.ui.activity.LauncherNotificationsMenu;
import net.modtale.launcher.ui.browse.ProjectBrowseController;
import net.modtale.launcher.ui.common.LauncherView;
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.ui.feedback.LauncherFeedbackView;
import net.modtale.launcher.ui.library.LauncherLibraryController;
import net.modtale.launcher.ui.play.LauncherPlayController;
import net.modtale.launcher.ui.project.LauncherProjectActions;
import net.modtale.launcher.ui.project.ProjectPageController;
import net.modtale.launcher.ui.settings.LauncherSettingsController;
import net.modtale.launcher.ui.update.LauncherUpdateController;
import net.modtale.launcher.sync.LauncherSettingsSyncService;

public final class LauncherRuntime {

    private final LauncherNavigation navigation = new LauncherNavigation();

    private LauncherServices services;
    private LauncherShell shell;

    private LauncherRuntime() {
    }

    public static LauncherRuntime create() {
        return new LauncherRuntime();
    }

    public void start(Stage stage, Application.Parameters parameters) {
        services = new LauncherServices(this::sceneRoot, this::fallbackProjectAssetUrl);
        services.discordRichPresence().start();
        LauncherFeedbackView feedbackView = new LauncherFeedbackView();
        LauncherSettingsController settingsController = new LauncherSettingsController(
                services.settingsStore(),
                services.apiClient(),
                () -> stage,
                navigation::currentView
        );
        LauncherAccountController accountController = new LauncherAccountController(
                services.apiClient(),
                settingsController
        );
        LauncherFeedback feedback = feedbackView.feedback(services.executor(), accountController::idleStatus);
        settingsController.attachFeedback(feedback);
        settingsController.setClearCacheAction(services::clearCaches);
        accountController.attachFeedback(feedback);
        LauncherSettingsSyncService settingsSyncService = new LauncherSettingsSyncService(
                services.apiClient(),
                services.settingsStore(),
                settingsController,
                services.installer(),
                feedback,
                accountController::isSignedIn,
                () -> sceneRoot() instanceof StackPane stack ? stack : null
        );
        accountController.setSettingsSyncService(settingsSyncService);
        settingsController.addSaveListener(accountController::syncLocalSettings);
        LauncherUpdateController launcherUpdateController = new LauncherUpdateController(
                services.launcherUpdateService(),
                settingsController,
                feedback,
                services.executor(),
                () -> stage
        );
        settingsController.setLauncherUpdateCheckAction(launcherUpdateController::checkManually);

        LauncherLibraryController libraryController = new LauncherLibraryController(
                services.apiClient(),
                services.installer(),
                services.worldModListInstaller(),
                services.updateService(),
                settingsController,
                accountController,
                feedback,
                services.executor(),
                services.projectPageImageLoader(),
                () -> sceneRoot() instanceof StackPane stack ? stack : null
        );
        LauncherNotificationsController notificationsController = new LauncherNotificationsController(
                services.apiClient(),
                accountController,
                feedback,
                services.accountImageLoader()
        );
        LauncherNotificationsMenu notificationsMenu = new LauncherNotificationsMenu(
                services.apiClient(),
                accountController,
                feedback,
                services.accountImageLoader()
        );
        LauncherFollowingController followingController = new LauncherFollowingController(
                services.apiClient(),
                accountController,
                feedback,
                services.accountImageLoader()
        );
        accountController.setOnAutoCheckUpdates(libraryController::checkUpdates);
        LauncherProjectActions projectActions = new LauncherProjectActions(
                services.apiClient(),
                accountController,
                libraryController,
                feedback,
                settingsController::gameVersion,
                services.executor(),
                services.projectPageImageLoader()
        );
        StackPane viewDeck = new StackPane();
        ProjectPageController projectPageController = new ProjectPageController(
                services.apiClient(),
                services.executor(),
                services.projectPageImageLoader(),
                services.projectCardFactory(),
                projectActions::installSelectedProject,
                projectActions::installSelectedProjectVersion,
                () -> navigation.show(LauncherView.DISCOVER),
                () -> navigation.show(LauncherView.PROJECT),
                feedback::showToast,
                settingsController::gameVersion,
                accountController::currentUser,
                accountController::setCurrentUser,
                accountController::signIn,
                accountController::isProjectLiked,
                projectActions::toggleFavorite
        );
        projectActions.attachOverlay(() -> sceneRoot() instanceof StackPane stack ? stack : null);
        projectActions.setViewHistoryAction(projectPageController::openProjectChangelog);
        LauncherPlayController playController = new LauncherPlayController(
                services.apiClient(),
                services.projectCardFactory(),
                services.hytaleAuthService(),
                services.hytaleGameLauncher(),
                services.discordRichPresence(),
                settingsController,
                feedback,
                services.executor(),
                accountController::isSignedIn,
                accountController::isProjectLiked,
                settingsController::gameVersion,
                projectActions::installSelectedProject,
                projectPageController::openProject,
                projectPageController::openCreator,
                projectActions::toggleFavorite
        );
        ProjectBrowseController browseController = new ProjectBrowseController(
                services.apiClient(),
                services.executor(),
                services.projectCardFactory(),
                viewDeck,
                this::contentBody,
                services.scrollSupport(),
                settingsController::applyFromFields,
                feedbackView.statusText()::setText,
                accountController::idleStatus,
                feedback::log,
                feedback::showToast,
                () -> navigation.show(LauncherView.DISCOVER),
                navigation::currentView,
                accountController::isSignedIn,
                accountController::isProjectLiked,
                settingsController::gameVersion,
                projectActions::installSelectedProject,
                projectPageController::openProject,
                projectPageController::openCreator,
                projectActions::toggleFavorite
        );
        playController.setOnBrowseCatalog(sort -> browseController.selectBrowseView(sort.browseView()));
        projectActions.attachBrowse(browseController);
        settingsController.addRefreshListener(browseController::refreshControls);
        settingsController.addRefreshListener(libraryController::refresh);
        settingsController.addRefreshListener(playController::syncMetrics);

        shell = new LauncherShell(
                navigation,
                feedbackView,
                feedback,
                accountController,
                services.hytaleAuthService(),
                browseController,
                projectPageController,
                playController,
                libraryController,
                notificationsController,
                notificationsMenu,
                followingController,
                settingsController,
                services.scrollSupport(),
                services.accountImageLoader(),
                viewDeck
        );
        playController.setOnHytaleAccountsChanged(shell::onHytaleAccountsChanged);
        accountController.setOnSignedIn(shell::onModtaleSignedIn);
        accountController.setOnSignedOut(shell::onModtaleSignedOut);
        navigation.bind(shell::showView);
        shell.start(stage, parameters);
        launcherUpdateController.checkOnStartup();
        accountController.restoreSession();
    }

    public void shutdown() {
        if (services != null) {
            services.shutdown();
        }
    }

    private Node sceneRoot() {
        return shell == null ? null : shell.sceneRoot();
    }

    private javafx.scene.layout.VBox contentBody() {
        return shell == null ? null : shell.contentBody();
    }

    private String fallbackProjectAssetUrl() {
        return Objects.requireNonNull(getClass()
                .getResource("/net/modtale/launcher/ui/nativefx/assets/favicon.png")).toExternalForm();
    }
}
