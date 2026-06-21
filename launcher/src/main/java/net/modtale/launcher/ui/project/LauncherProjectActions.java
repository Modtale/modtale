package net.modtale.launcher.ui.project;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.scene.layout.StackPane;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.model.project.GameVersionCatalog;
import net.modtale.launcher.model.project.ProjectDependency;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.model.project.ProjectVersion;
import net.modtale.launcher.model.user.CurrentUser;
import net.modtale.launcher.ui.account.LauncherAccountController;
import net.modtale.launcher.ui.browse.ProjectBrowseController;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.ui.library.LauncherLibraryController;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class LauncherProjectActions {

    private static final Logger LOG = LogManager.getLogger(LauncherProjectActions.class);

    private final ModtaleApiClient apiClient;
    private final LauncherAccountController accountController;
    private final LauncherLibraryController libraryController;
    private final LauncherFeedback feedback;
    private final Supplier<String> gameVersion;
    private final Executor executor;
    private final CachedImageLoader imageLoader;

    private ProjectBrowseController browseController;
    private NativeDownloadModal downloadModal;
    private NativeDependencyModal dependencyModal;
    private Consumer<ProjectDetail> viewHistory = detail -> {
    };

    public LauncherProjectActions(
            ModtaleApiClient apiClient,
            LauncherAccountController accountController,
            LauncherLibraryController libraryController,
            LauncherFeedback feedback,
            Supplier<String> gameVersion,
            Executor executor,
            CachedImageLoader imageLoader
    ) {
        this.apiClient = apiClient;
        this.accountController = accountController;
        this.libraryController = libraryController;
        this.feedback = feedback;
        this.gameVersion = gameVersion == null ? () -> "" : gameVersion;
        this.executor = executor;
        this.imageLoader = imageLoader;
    }

    public void attachBrowse(ProjectBrowseController browseController) {
        this.browseController = browseController;
    }

    public void attachOverlay(Supplier<StackPane> overlayHost) {
        downloadModal = new NativeDownloadModal(
                overlayHost,
                gameVersion,
                this::installSelectedProjectVersion,
                detail -> viewHistory.accept(detail)
        );
        dependencyModal = new NativeDependencyModal(
                overlayHost,
                apiClient,
                executor,
                imageLoader,
                this::installSelectedProjectVersion
        );
    }

    public void setViewHistoryAction(Consumer<ProjectDetail> viewHistory) {
        this.viewHistory = viewHistory == null ? detail -> {
        } : viewHistory;
    }

    public void installSelectedProject(ProjectSummary summary) {
        if (summary == null) {
            return;
        }
        if (downloadModal == null) {
            libraryController.installSelectedProject(summary);
            return;
        }
        ProjectDetail initialProject = detailFromSummary(summary);
        GameVersionCatalog initialCatalog = catalogFromProject(initialProject);
        if (initialProject.versions().isEmpty()) {
            downloadModal.showLoading(initialProject, initialCatalog);
        } else {
            downloadModal.show(initialProject, initialCatalog);
        }
        refreshDownloadOptions(summary, initialProject);
    }

    private void refreshDownloadOptions(ProjectSummary summary, ProjectDetail initialProject) {
        feedback.log("Refreshing download options for " + summary + "...");
        CompletableFuture.supplyAsync(() -> {
            ProjectDetail project = loadProjectWithVersions(summary);
            return new DownloadOptions(project, loadGameVersions(project));
        }, executor).whenComplete((options, error) -> Platform.runLater(() -> {
            if (error != null) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                feedback.log("Could not refresh download options for " + summary + ": " + cause.getMessage());
                if (initialProject.versions().isEmpty() && downloadModal.isShowing(initialProject)) {
                    feedback.showToast("Download options unavailable", cause.getMessage());
                }
                return;
            }
            downloadModal.refresh(options.project(), options.catalog());
        }));
    }

    private void installSelectedProjectVersion(NativeDownloadModal.DownloadSelection selection) {
        if (selection.selectedDependencies() != null) {
            installExactDependencySelection(
                    selection.project(),
                    selection.version(),
                    selection.gameVersion(),
                    selection.selectedDependencies()
            );
            return;
        }
        installSelectedProjectVersion(selection.project(), selection.version(), selection.gameVersion());
    }

    private void installSelectedProjectVersion(NativeDependencyModal.DependencySelection selection) {
        installExactDependencySelection(
                selection.project(),
                selection.version(),
                selection.gameVersion(),
                selection.selectedDependencies()
        );
    }

    public void installSelectedProjectVersion(ProjectDetail project, ProjectVersion version, String gameVersion) {
        List<ProjectDependency> dependencies = NativeDependencyModal.selectableDependencies(version);
        if (dependencyModal != null && !dependencies.isEmpty()) {
            dependencyModal.show(project, version, gameVersion);
            return;
        }
        installExactDependencySelection(project, version, gameVersion, dependencies);
    }

    private void installExactDependencySelection(
            ProjectDetail project,
            ProjectVersion version,
            String gameVersion,
            List<ProjectDependency> selectedDependencies
    ) {
        libraryController.installSelectedProjectVersion(project, version, gameVersion, selectedDependencies);
    }

    public void toggleFavorite(ProjectSummary project) {
        feedback.runAsync((accountController.isSignedIn() ? "Updating like for " : "Signing in to like ") + project + "...", () -> {
            CurrentUser user = accountController.ensureSignedIn();
            boolean wasFavorite = user.likesProject(project.id());
            apiClient.toggleFavorite(project.id());
            CurrentUser updatedUser = apiClient.currentUser();
            boolean isFavorite = updatedUser.likesProject(project.id());
            return new FavoriteToggleResult(project.id(), wasFavorite, isFavorite, updatedUser);
        }, result -> {
            accountController.setCurrentUser(result.user());
            int delta = (result.isFavorite() ? 1 : 0) - (result.wasFavorite() ? 1 : 0);
            if (browseController != null) {
                browseController.applyFavoriteDelta(result.projectId(), delta);
                browseController.renderProjects();
            }
            feedback.showToast(result.isFavorite() ? "Liked project" : "Removed like", "Updated your Modtale favorites.");
        });
    }

    private record FavoriteToggleResult(String projectId, boolean wasFavorite, boolean isFavorite, CurrentUser user) {
    }

    private GameVersionCatalog loadGameVersions(ProjectDetail project) {
        try {
            return apiClient.getGameVersionCatalog();
        } catch (RuntimeException ex) {
            LOG.warn("Could not load game version catalog; falling back to project versions.", ex);
            return catalogFromProject(project);
        }
    }

    private ProjectDetail loadProjectWithVersions(ProjectSummary summary) {
        ProjectDetail project = apiClient.getProject(summary.routeKey());
        return ProjectVersionHydrator.hydrateOrThrow(project, summary, apiClient::getProjectVersions);
    }

    private static ProjectDetail detailFromSummary(ProjectSummary summary) {
        return new ProjectDetail(
                summary.id(),
                summary.slug(),
                summary.title(),
                null,
                summary.description(),
                summary.authorId(),
                summary.author(),
                summary.imageUrl(),
                summary.bannerUrl(),
                summary.classification(),
                summary.downloadCount(),
                summary.favoriteCount(),
                summary.updatedAt(),
                null,
                null,
                Map.of(),
                List.of(),
                List.of(),
                Map.of(),
                null,
                false,
                null,
                summary.versions()
        );
    }

    private static GameVersionCatalog catalogFromProject(ProjectDetail project) {
        List<String> versions = project == null
                ? List.of()
                : project.versions().stream()
                        .flatMap(version -> version.gameVersions().stream())
                        .distinct()
                        .toList();
        return GameVersionCatalog.fromVersions(versions);
    }

    private record DownloadOptions(ProjectDetail project, GameVersionCatalog catalog) {
    }
}
