package net.modtale.launcher.ui.project;

import static net.modtale.launcher.ui.browse.card.ProjectCardFormatter.number;
import static net.modtale.launcher.ui.browse.card.ProjectCardFormatter.timeAgo;
import static net.modtale.launcher.ui.common.LauncherUi.primaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.secondaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.setVisibleManaged;
import static net.modtale.launcher.ui.common.LauncherUi.value;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.value.ChangeListener;
import javafx.css.PseudoClass;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.model.project.ProjectClassification;
import net.modtale.launcher.model.project.ProjectComment;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectDependency;
import net.modtale.launcher.model.project.ProjectGallery;
import net.modtale.launcher.model.project.ProjectMeta;
import net.modtale.launcher.model.project.ProjectPage;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.model.project.ProjectVersion;
import net.modtale.launcher.model.project.ProjectVersionChangelog;
import net.modtale.launcher.model.user.CreatorProfile;
import net.modtale.launcher.model.user.CurrentUser;
import net.modtale.launcher.model.user.UserSummary;
import net.modtale.launcher.settings.LauncherConfig;
import net.modtale.launcher.ui.browse.card.ProjectCardFactory;
import net.modtale.launcher.ui.browse.controls.BrowseOptions;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.common.LauncherLayout;
import net.modtale.launcher.ui.common.LauncherView;

public final class ProjectPageController {

    private static final double CONTENT_MAX_WIDTH = 1568;
    private static final double HEADER_ICON_SIZE = 224;
    private static final double HEADER_ICON_BORDER = 8;
    private static final double BANNER_MIN_HEIGHT = 180;
    private static final double BANNER_MAX_HEIGHT = 640;
    private static final double BANNER_FALLBACK_HEIGHT = 360;
    private static final double BANNER_FADE_BASE_HEIGHT = 128;
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final Pattern GALLERY_CAROUSEL_MARKER = Pattern.compile("\\{\\{\\s*gallery-carousel\\s*}}", Pattern.CASE_INSENSITIVE);

    @FunctionalInterface
    public interface VersionInstallAction {
        void install(ProjectDetail project, ProjectVersion version, String gameVersion);
    }

    private final ModtaleApiClient apiClient;
    private final Executor executor;
    private final CachedImageLoader imageLoader;
    private final ProjectCardFactory projectCardFactory;
    private final Consumer<ProjectSummary> installProject;
    private final VersionInstallAction installProjectVersion;
    private final Runnable showDiscover;
    private final Runnable showProject;
    private final BiConsumer<String, String> toast;
    private final Supplier<String> gameVersion;
    private final Supplier<CurrentUser> currentUserSupplier;
    private final Consumer<CurrentUser> currentUserUpdater;
    private final Runnable signIn;
    private final Function<String, Boolean> favoriteResolver;
    private final Consumer<ProjectSummary> toggleFavorite;
    private final NativeMarkdownRenderer markdownRenderer;
    private final NativeGalleryCarousel galleryCarousel;
    private final NativeCommentSection commentSection;
    private final NativeCreatorProfileView creatorProfileView;
    private final NativeShareModal shareModal;
    private final NativeReportModal reportModal;
    private final Map<String, List<ProjectVersionChangelog>> changelogCache = new ConcurrentHashMap<>();
    private final Set<String> expandedChangelogIds = new HashSet<>();
    private final Map<Node, Effect> overlayBackdropEffects = new IdentityHashMap<>();
    private final DoubleProperty scrollPixels = new SimpleDoubleProperty();
    private final ChangeListener<Bounds> scrollContentBoundsListener = (observable, previous, value) -> updateScrollPixels();
    private final VBox content = new VBox(0);

    private Node view;
    private ScrollPane attachedScrollPane;
    private Node observedScrollContent;
    private ProjectSummary currentProject;
    private ProjectDetail currentDetail;
    private List<String> currentGalleryImages = List.of();
    private Map<String, String> currentGalleryCaptions = Map.of();
    private List<ProjectComment> currentComments = List.of();
    private Map<String, UserSummary> commentUserProfiles = Map.of();
    private final Map<String, ProjectMeta> dependencyMetaCache = new ConcurrentHashMap<>();
    private final Set<String> requestedDependencyMetaIds = ConcurrentHashMap.newKeySet();
    private String currentCreatorHandle;
    private CreatorProfile currentCreator;
    private ProjectPage currentCreatorProjects;
    private List<CreatorProfile> currentCreatorRelations = List.of();
    private String currentUrl;
    private long detailRequestId;
    private long changelogRequestId;
    private long creatorRequestId;
    private long galleryRequestId;
    private long commentsRequestId;
    private boolean compactLayout;
    private boolean creatorLoading;
    private boolean showExperimentalChangelogs;
    private boolean galleryLoading;
    private boolean commentsLoading;
    private boolean commentSubmitting;
    private double syncedDocumentHeight = -1;
    private StackPane changelogOverlay;
    private StackPane galleryOverlay;
    private StackPane commentDeleteOverlay;

    public ProjectPageController(
            ModtaleApiClient apiClient,
            Executor executor,
            CachedImageLoader imageLoader,
            ProjectCardFactory projectCardFactory,
            Consumer<ProjectSummary> installProject,
            VersionInstallAction installProjectVersion,
            Runnable showDiscover,
            Runnable showProject,
            BiConsumer<String, String> toast,
            Supplier<String> gameVersion,
            Supplier<CurrentUser> currentUserSupplier,
            Consumer<CurrentUser> currentUserUpdater,
            Runnable signIn,
            Function<String, Boolean> favoriteResolver,
            Consumer<ProjectSummary> toggleFavorite
    ) {
        this.apiClient = apiClient;
        this.executor = executor;
        this.imageLoader = imageLoader;
        this.projectCardFactory = projectCardFactory;
        this.installProject = installProject == null ? project -> {
        } : installProject;
        this.installProjectVersion = installProjectVersion == null ? (project, version, selectedGameVersion) -> {
            if (project != null) {
                this.installProject.accept(summaryFromDetail(project));
            }
        } : installProjectVersion;
        this.showDiscover = showDiscover;
        this.showProject = showProject;
        this.toast = toast;
        this.gameVersion = gameVersion;
        this.currentUserSupplier = currentUserSupplier == null ? () -> null : currentUserSupplier;
        this.currentUserUpdater = currentUserUpdater == null ? user -> {
        } : currentUserUpdater;
        this.signIn = signIn == null ? () -> {
        } : signIn;
        this.favoriteResolver = favoriteResolver;
        this.toggleFavorite = toggleFavorite;
        this.galleryCarousel = new NativeGalleryCarousel(imageLoader, this::openUrlInBrowser);
        this.markdownRenderer = new NativeMarkdownRenderer(imageLoader, this::openUrlInBrowser);
        this.shareModal = new NativeShareModal(this::overlayHost, message -> showBrowserError(message));
        this.reportModal = new NativeReportModal(this::overlayHost, apiClient, executor, toast);
        this.commentSection = new NativeCommentSection(
                imageLoader,
                markdownRenderer,
                this.currentUserSupplier,
                this.signIn,
                this::openCommentProfile,
                new NativeCommentSection.Actions() {
                    @Override
                    public void submitComment(String editingCommentId, String content) {
                        ProjectPageController.this.submitComment(editingCommentId, content);
                    }

                    @Override
                    public void deleteComment(ProjectComment comment) {
                        ProjectPageController.this.showDeleteCommentModal(comment);
                    }

                    @Override
                    public void submitReply(String commentId, String content) {
                        ProjectPageController.this.submitReply(commentId, content);
                    }

                    @Override
                    public void vote(String commentId, boolean reply, boolean upvote) {
                        ProjectPageController.this.voteComment(commentId, reply, upvote);
                    }

                    @Override
                    public void report(String commentId) {
                        ProjectPageController.this.reportComment(commentId);
                    }
                },
                () -> {
                    if (currentProject != null) {
                        renderProject(false);
                    }
                }
        );
        this.creatorProfileView = new NativeCreatorProfileView(
                imageLoader,
                projectCardFactory,
                gameVersion,
                this.currentUserSupplier,
                favoriteResolver,
                installProject,
                this::openProject,
                this::openCreator,
                toggleFavorite,
                showDiscover,
                this::toggleCreatorFollow,
                this::copyCreatorId,
                this::showCreatorReportModal,
                this::openUrlInBrowser,
                scrollPixels
        );
    }

    public Node view() {
        if (view == null) {
            view = buildView();
        }
        return view;
    }

    public void attachScrollPane(ScrollPane scrollPane) {
        attachedScrollPane = scrollPane;
        if (scrollPane == null) {
            scrollPixels.set(0);
            return;
        }
        scrollPane.vvalueProperty().addListener((observable, previous, value) -> updateScrollPixels());
        scrollPane.viewportBoundsProperty().addListener((observable, previous, value) -> updateScrollPixels());
        scrollPane.contentProperty().addListener((observable, previous, value) -> observeScrollContent(value));
        observeScrollContent(scrollPane.getContent());
        updateScrollPixels();
    }

    public void openProject(ProjectSummary project) {
        hideChangelogOverlay();
        hideGalleryOverlay();
        hideCommentDeleteOverlay();
        hideActionModals();
        expandedChangelogIds.clear();
        showExperimentalChangelogs = false;
        resetCommentsState();
        resetGalleryState();
        clearCreatorState();
        currentProject = project;
        currentDetail = null;
        currentUrl = projectPageUrl(project);
        long requestId = ++detailRequestId;
        fetchGalleryForCurrentProject();
        renderProject(true);
        showProject.run();
        fetchProjectDetail(project, requestId);
    }

    public void openProjectChangelog(ProjectDetail detail) {
        if (detail == null) {
            return;
        }
        hideChangelogOverlay();
        hideGalleryOverlay();
        hideCommentDeleteOverlay();
        hideActionModals();
        expandedChangelogIds.clear();
        showExperimentalChangelogs = false;
        resetCommentsState();
        resetGalleryState();
        clearCreatorState();
        currentProject = summaryFromDetail(detail);
        currentDetail = detail;
        currentUrl = projectPageUrl(currentProject);
        ++detailRequestId;
        fetchGalleryForCurrentProject();
        fetchCommentsForCurrentProject();
        renderProject(false);
        showProject.run();
        Platform.runLater(this::showChangelogModal);
    }

    public void openCreator(ProjectSummary project) {
        hideChangelogOverlay();
        hideGalleryOverlay();
        hideCommentDeleteOverlay();
        hideActionModals();
        expandedChangelogIds.clear();
        showExperimentalChangelogs = false;
        resetCommentsState();
        resetGalleryState();
        currentProject = null;
        currentDetail = null;
        currentCreatorHandle = creatorHandle(project);
        currentCreator = null;
        currentCreatorProjects = null;
        currentCreatorRelations = List.of();
        currentUrl = creatorPageUrl(project);
        long requestId = ++creatorRequestId;
        ++detailRequestId;
        creatorLoading = true;
        renderCreator(true);
        showProject.run();
        fetchCreatorProfile(currentCreatorHandle, requestId);
    }

    private void clearCreatorState() {
        currentCreatorHandle = null;
        currentCreator = null;
        currentCreatorProjects = null;
        currentCreatorRelations = List.of();
        creatorLoading = false;
        ++creatorRequestId;
    }

    private void fetchCreatorProfile(String handle, long requestId) {
        CompletableFuture.supplyAsync(() -> {
            CreatorProfile profile = apiClient.getUserProfile(handle);
            ProjectPage projects;
            try {
                projects = apiClient.getCreatorProjects(profile.id(), 0, 12);
            } catch (RuntimeException ex) {
                projects = new ProjectPage(List.of(), 0, 0, 0, true);
            }
            List<CreatorProfile> relations = List.of();
            try {
                relations = profile.organization()
                        ? apiClient.getOrganizationMembers(profile.id())
                        : apiClient.getUserOrganizations(profile.id());
            } catch (RuntimeException ignored) {
                relations = List.of();
            }
            return new CreatorProfilePayload(profile, projects, relations);
        }, executor).whenComplete((payload, error) -> Platform.runLater(() -> {
            if (requestId != creatorRequestId || !value(handle, "").equals(value(currentCreatorHandle, ""))) {
                return;
            }
            creatorLoading = false;
            if (error != null) {
                if (toast != null) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    toast.accept("Creator unavailable", value(cause.getMessage(), "Could not load this creator profile."));
                }
                content.getChildren().setAll(externalState(
                        "Creator unavailable",
                        "The launcher could not load this creator profile.",
                        "Open Creator"
                ));
                return;
            }
            currentCreator = payload.profile();
            currentCreatorProjects = payload.projects();
            currentCreatorRelations = payload.relations();
            renderCreator(false);
        }));
    }

    private void renderCreator(boolean loading) {
        if (currentCreatorHandle == null) {
            return;
        }
        compactLayout = isCompactWidth(content.getWidth());
        content.minHeightProperty().unbind();
        content.prefHeightProperty().unbind();
        syncedDocumentHeight = -1;
        Node page = creatorProfileView.render(currentCreator, currentCreatorProjects, currentCreatorRelations, loading, compactLayout);
        if (page instanceof Region pageRegion) {
            syncProjectDocumentHeight(pageRegion);
            pageRegion.prefHeightProperty().addListener((observable, previous, height) ->
                    Platform.runLater(() -> syncProjectDocumentHeight(pageRegion)));
        }
        content.getChildren().setAll(page);
        if (page instanceof Region pageRegion) {
            Platform.runLater(() -> syncProjectDocumentHeight(pageRegion));
        }
        Platform.runLater(this::updateScrollPixels);
    }

    private void toggleCreatorFollow() {
        if (currentCreator == null) {
            return;
        }
        CurrentUser user = currentUserSupplier.get();
        if (user == null) {
            signIn.run();
            return;
        }
        String creatorId = currentCreator.id();
        boolean wasFollowing = user.followsUser(creatorId);
        CompletableFuture.supplyAsync(() -> {
            if (wasFollowing) {
                apiClient.unfollowUser(creatorId);
            } else {
                apiClient.followUser(creatorId);
            }
            return apiClient.currentUser();
        }, executor).whenComplete((updatedUser, error) -> Platform.runLater(() -> {
            if (error != null) {
                if (toast != null) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    toast.accept("Follow failed", value(cause.getMessage(), "Could not update this creator."));
                }
                return;
            }
            currentUserUpdater.accept(updatedUser);
            renderCreator(false);
        }));
    }

    private void copyCreatorId() {
        if (currentCreator == null || isBlank(currentCreator.id())) {
            return;
        }
        ClipboardContent content = new ClipboardContent();
        content.putString(currentCreator.id());
        Clipboard.getSystemClipboard().setContent(content);
        if (toast != null) {
            toast.accept("Copied", "Creator ID copied.");
        }
    }

    private record CreatorProfilePayload(
            CreatorProfile profile,
            ProjectPage projects,
            List<CreatorProfile> relations
    ) {
    }

    private Node buildView() {
        content.setUserData(LauncherView.PROJECT);
        content.getStyleClass().addAll("view", "project-detail-view");
        content.setAlignment(Pos.TOP_CENTER);
        content.setFillWidth(true);
        content.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        content.widthProperty().addListener((observable, previous, width) -> {
            boolean nextCompact = isCompactWidth(width.doubleValue());
            if (nextCompact != compactLayout && currentProject != null) {
                compactLayout = nextCompact;
                renderProject(false);
            } else if (nextCompact != compactLayout && currentCreatorHandle != null) {
                compactLayout = nextCompact;
                renderCreator(creatorLoading);
            }
        });
        content.getChildren().setAll(externalState(
                "Project Page",
                "Choose a project from Discover to view its full details.",
                "Open Modtale"
        ));
        return content;
    }

    private void fetchProjectDetail(ProjectSummary project, long requestId) {
        CompletableFuture.supplyAsync(() -> {
                    ProjectDetail detail = apiClient.getProject(project.routeKey());
                    return ProjectVersionHydrator.hydrate(detail, project, apiClient::getProjectVersions);
                }, executor)
                .whenComplete((detail, error) -> Platform.runLater(() -> {
                    if (requestId != detailRequestId || currentProject == null
                            || !sameProject(currentProject, project)) {
                        return;
                    }
                    if (error != null) {
                        if (toast != null) {
                            Throwable cause = error.getCause() == null ? error : error.getCause();
                            toast.accept("Project detail unavailable", value(cause.getMessage(), "Showing the browse summary."));
                        }
                        renderProject(false);
                        return;
                    }
                    currentDetail = detail;
                    fetchCommentsForCurrentProject();
                    renderProject(false);
                }));
    }

    private void resetCommentsState() {
        ++commentsRequestId;
        commentsLoading = false;
        commentSubmitting = false;
        currentComments = List.of();
        commentUserProfiles = Map.of();
        commentSection.clearState();
    }

    private void resetGalleryState() {
        ++galleryRequestId;
        galleryLoading = false;
        currentGalleryImages = List.of();
        currentGalleryCaptions = Map.of();
    }

    private void fetchGalleryForCurrentProject() {
        String key = galleryProjectKey();
        if (isBlank(key)) {
            galleryLoading = false;
            currentGalleryImages = List.of();
            currentGalleryCaptions = Map.of();
            return;
        }
        long requestId = ++galleryRequestId;
        galleryLoading = true;
        CompletableFuture.supplyAsync(() -> apiClient.getProjectGallery(key), executor)
                .whenComplete((gallery, error) -> Platform.runLater(() -> {
                    if (requestId != galleryRequestId || !value(key, "").equals(value(galleryProjectKey(), ""))) {
                        return;
                    }
                    galleryLoading = false;
                    if (error != null) {
                        currentGalleryImages = List.of();
                        currentGalleryCaptions = Map.of();
                        renderProject(false);
                        return;
                    }
                    ProjectGallery safeGallery = gallery == null ? new ProjectGallery(List.of(), Map.of()) : gallery;
                    currentGalleryImages = safeGallery.galleryImages();
                    currentGalleryCaptions = safeGallery.galleryImageCaptions();
                    renderProject(false);
                }));
    }

    private void fetchCommentsForCurrentProject() {
        String key = commentProjectKey();
        if (isBlank(key) || !shouldShowComments(currentProject, currentDetail)) {
            commentsLoading = false;
            currentComments = List.of();
            commentUserProfiles = Map.of();
            return;
        }
        long requestId = ++commentsRequestId;
        commentsLoading = true;
        CompletableFuture.supplyAsync(() -> fetchCommentsPayload(key), executor)
                .whenComplete((payload, error) -> Platform.runLater(() -> {
                    if (requestId != commentsRequestId || !value(key, "").equals(value(commentProjectKey(), ""))) {
                        return;
                    }
                    commentsLoading = false;
                    if (error != null) {
                        if (toast != null) {
                            Throwable cause = error.getCause() == null ? error : error.getCause();
                            toast.accept("Comments unavailable", value(cause.getMessage(), "Could not load comments."));
                        }
                        renderProject(false);
                        return;
                    }
                    currentComments = payload.comments();
                    commentUserProfiles = payload.userProfiles();
                    renderProject(false);
                }));
    }

    private CommentsPayload fetchCommentsPayload(String projectKey) {
        List<ProjectComment> comments = apiClient.getComments(projectKey);
        List<String> userIds = collectCommentUserIds(comments);
        Map<String, UserSummary> profiles = new LinkedHashMap<>();
        if (!userIds.isEmpty()) {
            for (UserSummary profile : apiClient.getUsersBatch(userIds)) {
                if (profile != null && !isBlank(profile.id())) {
                    profiles.put(profile.id(), profile);
                }
            }
        }
        return new CommentsPayload(comments, profiles);
    }

    private List<String> collectCommentUserIds(List<ProjectComment> comments) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (ProjectComment comment : comments == null ? List.<ProjectComment>of() : comments) {
            if (!isBlank(comment.userId())) {
                ids.add(comment.userId());
            }
            ProjectComment.Reply reply = comment.developerReply();
            if (reply != null && !isBlank(reply.userId())) {
                ids.add(reply.userId());
            }
        }
        return List.copyOf(ids);
    }

    private void submitComment(String editingCommentId, String content) {
        if (currentUserSupplier.get() == null) {
            signIn.run();
            return;
        }
        String text = value(content, "").trim();
        if (text.isBlank()) {
            return;
        }
        String projectKey = commentProjectKey();
        if (isBlank(projectKey)) {
            return;
        }
        runCommentMutation(
                editingCommentId == null
                        ? () -> apiClient.postComment(projectKey, text)
                        : () -> apiClient.updateComment(projectKey, editingCommentId, text),
                editingCommentId == null ? "Comment posted!" : "Comment updated!",
                editingCommentId == null ? "Could not post your comment." : "Could not save your edited comment.",
                () -> {
                    commentSection.clearComposer();
                    fetchCommentsForCurrentProject();
                }
        );
    }

    private void submitReply(String commentId, String content) {
        if (currentUserSupplier.get() == null) {
            signIn.run();
            return;
        }
        String text = value(content, "").trim();
        String projectKey = commentProjectKey();
        if (text.isBlank() || isBlank(commentId) || isBlank(projectKey)) {
            return;
        }
        runCommentMutation(
                () -> apiClient.replyToComment(projectKey, commentId, text),
                "Reply posted!",
                "Could not post that reply.",
                () -> {
                    commentSection.clearReply();
                    fetchCommentsForCurrentProject();
                }
        );
    }

    private void confirmDeleteComment(ProjectComment comment) {
        if (comment == null || isBlank(comment.id())) {
            return;
        }
        String projectKey = commentProjectKey();
        if (isBlank(projectKey)) {
            return;
        }
        hideCommentDeleteOverlay();
        runCommentMutation(
                () -> apiClient.deleteComment(projectKey, comment.id()),
                "Comment deleted.",
                "Could not delete this comment.",
                this::fetchCommentsForCurrentProject
        );
    }

    private void voteComment(String commentId, boolean reply, boolean upvote) {
        if (currentUserSupplier.get() == null) {
            signIn.run();
            return;
        }
        String projectKey = commentProjectKey();
        if (isBlank(commentId) || isBlank(projectKey)) {
            return;
        }
        runCommentMutation(
                () -> apiClient.voteComment(projectKey, commentId, upvote, reply),
                null,
                "Could not register your vote.",
                this::fetchCommentsForCurrentProject
        );
    }

    private void runCommentMutation(Runnable mutation, String successMessage, String errorMessage, Runnable afterSuccess) {
        String projectKey = commentProjectKey();
        if (isBlank(projectKey)) {
            return;
        }
        commentSubmitting = true;
        renderProject(false);
        CompletableFuture.runAsync(mutation, executor).whenComplete((ignored, error) -> Platform.runLater(() -> {
            commentSubmitting = false;
            if (error != null) {
                if (toast != null) {
                    Throwable cause = error.getCause() == null ? error : error.getCause();
                    toast.accept("Comment action failed", value(cause.getMessage(), errorMessage));
                }
                renderProject(false);
                return;
            }
            if (toast != null && successMessage != null && !successMessage.isBlank()) {
                toast.accept("Comments", successMessage);
            }
            if (afterSuccess != null) {
                afterSuccess.run();
            } else {
                renderProject(false);
            }
        }));
    }

    private void reportComment(String commentId) {
        showCommentReportModal(commentId);
    }

    private void showShareModal() {
        if (currentProject == null) {
            return;
        }
        hideChangelogOverlay();
        hideGalleryOverlay();
        hideCommentDeleteOverlay();
        reportModal.hide();
        shareModal.show(projectUrl(), textTitle(currentProject, currentDetail), textAuthor(currentProject, currentDetail));
    }

    private void showProjectReportModal() {
        if (currentProject == null) {
            return;
        }
        hideChangelogOverlay();
        hideGalleryOverlay();
        hideCommentDeleteOverlay();
        shareModal.hide();
        reportModal.show(
                NativeReportModal.TargetType.PROJECT,
                textId(currentProject, currentDetail),
                textTitle(currentProject, currentDetail)
        );
    }

    private void showCommentReportModal(String commentId) {
        if (isBlank(commentId)) {
            return;
        }
        hideChangelogOverlay();
        hideGalleryOverlay();
        hideCommentDeleteOverlay();
        shareModal.hide();
        ProjectComment comment = findComment(commentId);
        reportModal.show(
                NativeReportModal.TargetType.COMMENT,
                commentId,
                commentReportTitle(comment)
        );
    }

    private void showCreatorReportModal(CreatorProfile profile) {
        if (profile == null) {
            return;
        }
        hideChangelogOverlay();
        hideGalleryOverlay();
        hideCommentDeleteOverlay();
        shareModal.hide();
        reportModal.show(
                NativeReportModal.TargetType.USER,
                profile.id(),
                value(profile.username(), "User Profile")
        );
    }

    private void hideActionModals() {
        shareModal.hide();
        reportModal.hide();
    }

    private ProjectComment findComment(String commentId) {
        if (isBlank(commentId)) {
            return null;
        }
        return currentComments.stream()
                .filter(comment -> comment != null && commentId.equals(comment.id()))
                .findFirst()
                .orElse(null);
    }

    private String commentReportTitle(ProjectComment comment) {
        if (comment == null) {
            return "Comment";
        }
        return "Comment by " + first(
                comment.author() == null ? null : comment.author().username(),
                comment.user(),
                "Unknown User"
        );
    }

    private String commentProjectKey() {
        if (currentDetail != null && !isBlank(currentDetail.routeKey())) {
            return currentDetail.routeKey();
        }
        return currentProject == null ? "" : currentProject.routeKey();
    }

    private String galleryProjectKey() {
        return commentProjectKey();
    }

    private boolean shouldShowComments(ProjectSummary summary, ProjectDetail detail) {
        boolean disabled = detail != null && Boolean.FALSE.equals(detail.allowComments());
        return !disabled || isCurrentProjectCreator(summary, detail);
    }

    private boolean isCurrentProjectCreator(ProjectSummary summary, ProjectDetail detail) {
        CurrentUser user = currentUserSupplier.get();
        if (user == null) {
            return false;
        }
        String userId = value(user.id(), "");
        String username = value(user.username(), "");
        String authorId = first(detail == null ? null : detail.authorId(), summary == null ? null : summary.authorId());
        String authorName = first(detail == null ? null : detail.author(), summary == null ? null : summary.author());
        return (!userId.isBlank() && userId.equals(value(authorId, "")))
                || (!username.isBlank() && username.equalsIgnoreCase(value(authorName, "")));
    }

    private record CommentsPayload(
            List<ProjectComment> comments,
            Map<String, UserSummary> userProfiles
    ) {
        private CommentsPayload {
            comments = comments == null ? List.of() : List.copyOf(comments);
            userProfiles = userProfiles == null ? Map.of() : Map.copyOf(userProfiles);
        }
    }

    private void renderProject(boolean loading) {
        if (currentProject == null) {
            return;
        }
        compactLayout = isCompactWidth(content.getWidth());
        requestDependencyMetadata(currentProject, currentDetail);
        content.minHeightProperty().unbind();
        content.prefHeightProperty().unbind();
        syncedDocumentHeight = -1;
        Node page = projectPage(currentProject, currentDetail, loading);
        if (page instanceof Region pageRegion) {
            syncProjectDocumentHeight(pageRegion);
            pageRegion.prefHeightProperty().addListener((observable, previous, height) ->
                    Platform.runLater(() -> syncProjectDocumentHeight(pageRegion)));
        }
        content.getChildren().setAll(page);
        if (page instanceof Region pageRegion) {
            Platform.runLater(() -> syncProjectDocumentHeight(pageRegion));
        }
        Platform.runLater(this::updateScrollPixels);
    }

    private void observeScrollContent(Node node) {
        if (observedScrollContent != null) {
            observedScrollContent.layoutBoundsProperty().removeListener(scrollContentBoundsListener);
        }
        observedScrollContent = node;
        if (observedScrollContent != null) {
            observedScrollContent.layoutBoundsProperty().addListener(scrollContentBoundsListener);
        }
        updateScrollPixels();
    }

    private void updateScrollPixels() {
        if (attachedScrollPane == null || attachedScrollPane.getContent() == null) {
            scrollPixels.set(0);
            return;
        }
        Bounds contentBounds = attachedScrollPane.getContent().getLayoutBounds();
        Bounds viewportBounds = attachedScrollPane.getViewportBounds();
        double scrollable = contentBounds.getHeight() - viewportBounds.getHeight();
        if (!Double.isFinite(scrollable) || scrollable <= 0) {
            scrollPixels.set(0);
            return;
        }
        double valueRange = attachedScrollPane.getVmax() - attachedScrollPane.getVmin();
        double normalized = valueRange <= 0
                ? 0
                : (attachedScrollPane.getVvalue() - attachedScrollPane.getVmin()) / valueRange;
        scrollPixels.set(Math.max(0, Math.min(1, normalized)) * scrollable);
    }

    private void syncProjectDocumentHeight(Region page) {
        double height = page.getPrefHeight();
        if (!Double.isFinite(height) || height <= 0) {
            height = page.prefHeight(content.getWidth() > 0 ? content.getWidth() : -1);
        }
        if (!Double.isFinite(height) || height <= 0) {
            return;
        }
        if (Math.abs(height - syncedDocumentHeight) <= 0.5
                && Math.abs(content.getPrefHeight() - height) <= 0.5
                && Math.abs(content.getMinHeight() - height) <= 0.5) {
            return;
        }
        syncedDocumentHeight = height;
        content.setMinHeight(height);
        content.setPrefHeight(height);
        content.resize(Math.max(content.getWidth(), page.getWidth()), height);
        page.resize(Math.max(page.getWidth(), content.getWidth()), height);
        for (javafx.scene.Parent parent = content.getParent(); parent != null; parent = parent.getParent()) {
            parent.requestLayout();
        }
    }

    private Node projectPage(ProjectSummary summary, ProjectDetail detail, boolean loading) {
        VBox page = new VBox(0);
        page.getStyleClass().add("project-detail-page");
        page.setFillWidth(true);
        page.setAlignment(Pos.TOP_CENTER);
        page.setMinWidth(0);
        page.setMaxWidth(Double.MAX_VALUE);
        page.setMaxHeight(Double.MAX_VALUE);

        StackPane banner = banner(summary, detail);
        VBox panel = new VBox(0);
        panel.getStyleClass().add("project-detail-shell");
        panel.setMinWidth(0);
        panel.setMaxWidth(Double.MAX_VALUE);
        panel.setMaxHeight(Double.MAX_VALUE);
        VBox.setMargin(panel, compactLayout
                ? LauncherLayout.launcherPageInsets(-8, 56)
                : LauncherLayout.launcherPageInsets(-128, 56));

        Node header = header(summary, detail, loading);
        Node body = body(summary, detail);
        panel.getChildren().addAll(header, body);
        if (!compactLayout && body instanceof Region bodyRegion) {
            panel.minHeightProperty().bind(Bindings.createDoubleBinding(
                    () -> header.getLayoutBounds().getHeight() + bodyRegion.prefHeight(-1),
                    header.layoutBoundsProperty(),
                    bodyRegion.minHeightProperty(),
                    bodyRegion.prefHeightProperty()
            ));
            panel.prefHeightProperty().bind(panel.minHeightProperty());
        }
        page.getChildren().addAll(banner, panel);
        double panelTopMargin = compactLayout ? -8 : -128;
        double panelBottomMargin = 56;
        double pageBottomPadding = 20;
        page.minHeightProperty().bind(Bindings.createDoubleBinding(
                () -> banner.getPrefHeight() + panel.prefHeight(-1) + panelTopMargin + panelBottomMargin + pageBottomPadding,
                banner.prefHeightProperty(),
                panel.prefHeightProperty()
        ));
        page.prefHeightProperty().bind(page.minHeightProperty());
        return page;
    }

    private StackPane banner(ProjectSummary summary, ProjectDetail detail) {
        StackPane banner = new StackPane();
        banner.getStyleClass().add("project-detail-banner");
        banner.setMinWidth(0);
        banner.setMaxWidth(Double.MAX_VALUE);
        banner.setPrefHeight(BANNER_FALLBACK_HEIGHT);
        banner.setMaxHeight(BANNER_MAX_HEIGHT);
        banner.prefHeightProperty().bind(Bindings.createDoubleBinding(
                () -> bannerHeight(content.getWidth()),
                content.widthProperty()
        ));
        banner.minHeightProperty().bind(banner.prefHeightProperty());

        StackPane media = new StackPane();
        media.getStyleClass().add("project-detail-banner-media");
        media.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        String bannerUrl = first(detail == null ? null : detail.bannerUrl(), summary.bannerUrl());
        if (isBlank(bannerUrl)) {
            Region fallback = new Region();
            fallback.getStyleClass().add("project-detail-banner-fallback");
            fallback.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            media.getChildren().add(fallback);
        } else {
            media.getStyleClass().add("letterboxed");
            ImageView image = new ImageView();
            image.getStyleClass().add("project-detail-banner-image");
            image.setPreserveRatio(true);
            image.setSmooth(true);
            image.fitWidthProperty().bind(banner.widthProperty());
            image.fitHeightProperty().bind(banner.heightProperty());
            imageLoader.loadInto(image, bannerUrl, 1920, 640, true);
            media.getChildren().add(image);
        }
        banner.getChildren().add(media);

        Region fade = new Region();
        fade.getStyleClass().add("project-detail-banner-fade");
        fade.setMouseTransparent(true);
        NativeBannerScrollEffect.bind(media, fade, scrollPixels, BANNER_FADE_BASE_HEIGHT);
        banner.getChildren().add(fade);

        HBox backLayer = new HBox();
        backLayer.setAlignment(Pos.TOP_LEFT);
        backLayer.setMaxWidth(Double.MAX_VALUE);
        backLayer.setMouseTransparent(false);
        StackPane.setAlignment(backLayer, Pos.TOP_CENTER);
        StackPane.setMargin(backLayer, LauncherLayout.launcherPageInsets(25, 0));
        Button back = new Button("Back", LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_LEFT, 16));
        back.getStyleClass().add("project-detail-back");
        back.setOnAction(event -> showDiscover.run());
        backLayer.getChildren().add(back);
        banner.getChildren().add(backLayer);
        return banner;
    }

    private static double bannerHeight(double width) {
        double measured = Double.isFinite(width) && width > 0 ? width / 3.0 : BANNER_FALLBACK_HEIGHT;
        return Math.max(BANNER_MIN_HEIGHT, Math.min(BANNER_MAX_HEIGHT, measured));
    }

    private Node header(ProjectSummary summary, ProjectDetail detail, boolean loading) {
        VBox header = new VBox(0);
        header.getStyleClass().add("project-detail-header");
        if (compactLayout) {
            header.getStyleClass().add("project-detail-header-compact");
        }

        if (compactLayout) {
            HBox mobileTop = new HBox(16);
            mobileTop.setAlignment(Pos.BOTTOM_LEFT);
            VBox.setMargin(mobileTop, new Insets(-64, 0, 24, 0));
            mobileTop.getChildren().add(projectIcon(summary, detail, 128, 4));
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox actions = headerActions(summary);
            actions.setAlignment(Pos.BOTTOM_RIGHT);
            mobileTop.getChildren().addAll(spacer, actions);

            VBox copy = new VBox(0);
            copy.getStyleClass().add("project-detail-compact-copy");
            copy.getChildren().addAll(titleRow(summary, detail), byline(summary, detail, loading), description(summary, detail),
                    actionBar(summary, detail));
            header.getChildren().addAll(mobileTop, copy);
            return header;
        }

        HBox row = new HBox(32);
        row.setAlignment(Pos.TOP_LEFT);
        StackPane icon = projectIcon(summary, detail, HEADER_ICON_SIZE, HEADER_ICON_BORDER);
        HBox.setMargin(icon, new Insets(-96, 0, 0, 8));
        row.getChildren().add(icon);

        VBox copy = new VBox(0);
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);

        HBox top = new HBox(16);
        top.setAlignment(Pos.TOP_LEFT);
        VBox titleBlock = new VBox(10);
        titleBlock.setMinWidth(0);
        HBox.setHgrow(titleBlock, Priority.ALWAYS);
        titleBlock.getChildren().addAll(titleRow(summary, detail), byline(summary, detail, loading), description(summary, detail));

        HBox actions = headerActions(summary);
        actions.setAlignment(Pos.TOP_RIGHT);
        top.getChildren().addAll(titleBlock, actions);
        copy.getChildren().addAll(top, actionBar(summary, detail));
        row.getChildren().add(copy);
        header.getChildren().add(row);
        return header;
    }

    private HBox headerActions(ProjectSummary summary) {
        HBox actions = new HBox(8);
        actions.getStyleClass().add("project-detail-header-actions");
        actions.getChildren().addAll(
                headerIconButton(LauncherIcons.Glyph.HEART, "Favorite", this::toggleFavorite, isFavorite(summary)),
                headerIconButton(LauncherIcons.Glyph.SHARE_2, "Share", this::showShareModal, false),
                headerIconButton(LauncherIcons.Glyph.FLAG, "Report", this::showProjectReportModal, false)
        );
        return actions;
    }

    private Node titleRow(ProjectSummary summary, ProjectDetail detail) {
        HBox row = new HBox(12);
        row.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(textTitle(summary, detail));
        title.getStyleClass().add("project-detail-title");
        title.setWrapText(true);
        title.setMinWidth(0);
        HBox.setHgrow(title, Priority.ALWAYS);
        row.getChildren().addAll(title, classificationBadge(textClassification(summary, detail), 14));
        return row;
    }

    private Node byline(ProjectSummary summary, ProjectDetail detail, boolean loading) {
        HBox row = new HBox(14);
        row.getStyleClass().add("project-detail-byline");
        row.setAlignment(Pos.CENTER_LEFT);

        Button author = authorLink(summary, detail);
        Label dot = new Label("•");
        dot.getStyleClass().add("project-detail-muted");
        Label updated = new Label(("Updated " + timeAgo(textUpdatedAt(summary, detail))).toUpperCase(Locale.ROOT));
        updated.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.CALENDAR, 13));
        updated.getStyleClass().add("project-detail-updated");
        row.getChildren().addAll(author, dot, updated);
        if (loading) {
            row.getChildren().add(NativeSpinner.inline(16));
        }
        return row;
    }

    private Button authorLink(ProjectSummary summary, ProjectDetail detail) {
        ProjectSummary creatorTarget = creatorTarget(summary, detail);
        Button author = new Button("by " + value(textAuthor(summary, detail), "Unknown"));
        author.getStyleClass().addAll("project-detail-author", "project-detail-author-link");
        author.setMnemonicParsing(false);
        author.setAccessibleText("Open creator profile");
        author.setDisable(!hasCreatorHandle(creatorTarget));
        author.setOnAction(event -> openCreator(creatorTarget));
        return author;
    }

    private ProjectSummary creatorTarget(ProjectSummary summary, ProjectDetail detail) {
        if (detail == null) {
            return summary;
        }
        ProjectSummary detailSummary = summaryFromDetail(detail);
        return hasCreatorHandle(detailSummary) ? detailSummary : summary;
    }

    private Node description(ProjectSummary summary, ProjectDetail detail) {
        String description = textDescription(summary, detail);
        if (isBlank(description)) {
            Region spacer = new Region();
            spacer.setMinHeight(0);
            return spacer;
        }
        Label label = new Label(description);
        label.getStyleClass().add("project-detail-summary");
        label.setWrapText(true);
        label.setMaxWidth(900);
        return label;
    }

    private Node actionBar(ProjectSummary summary, ProjectDetail detail) {
        if (compactLayout) {
            VBox bar = new VBox(10);
            bar.getStyleClass().addAll("project-detail-action-bar", "project-detail-action-bar-compact");
            Button download = downloadButton(summary, detail);
            download.setMaxWidth(Double.MAX_VALUE);

            GridPane secondary = new GridPane();
            secondary.getStyleClass().add("project-detail-compact-action-grid");
            secondary.setHgap(8);
            secondary.setVgap(8);
            ColumnConstraints left = new ColumnConstraints();
            left.setPercentWidth(50);
            left.setFillWidth(true);
            ColumnConstraints right = new ColumnConstraints();
            right.setPercentWidth(50);
            right.setFillWidth(true);
            secondary.getColumnConstraints().addAll(left, right);
            List<Button> buttons = compactActionButtons(detail);
            for (int i = 0; i < buttons.size(); i++) {
                Button button = buttons.get(i);
                button.setMaxWidth(Double.MAX_VALUE);
                GridPane.setHgrow(button, Priority.ALWAYS);
                secondary.add(button, i % 2, i / 2);
            }
            bar.getChildren().addAll(download, secondary);
            Node mobileLinks = mobileExternalLinks(detail);
            if (mobileLinks != null) {
                bar.getChildren().add(mobileLinks);
            }
            return bar;
        }

        HBox bar = new HBox(16);
        bar.getStyleClass().add("project-detail-action-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(bar, new Insets(32, 0, 0, 0));

        Button download = downloadButton(summary, detail);

        Region divider = new Region();
        divider.getStyleClass().add("project-detail-action-divider");

        HBox secondary = new HBox(8);
        secondary.getStyleClass().add("project-detail-action-buttons");
        secondary.setAlignment(Pos.CENTER_LEFT);
        secondary.getChildren().addAll(compactActionButtons(detail));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox links = externalLinks(detail);
        bar.getChildren().addAll(download, divider, secondary, spacer, links);
        return bar;
    }

    private Button downloadButton(ProjectSummary summary, ProjectDetail detail) {
        Button download = primaryButton("Download");
        download.getStyleClass().add("project-detail-download-button");
        download.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.DOWNLOAD, 18));
        download.setOnAction(event -> installProject.accept(summary));
        return download;
    }

    private Node body(ProjectSummary summary, ProjectDetail detail) {
        if (compactLayout) {
            VBox body = new VBox(0);
            body.getStyleClass().addAll("project-detail-body", "project-detail-body-compact");
            VBox sidebar = mobileSidebar(summary, detail);
            sidebar.getStyleClass().add("project-detail-sidebar-compact");
            sidebar.setMaxWidth(Double.MAX_VALUE);
            VBox main = mainContent(summary, detail);
            main.getStyleClass().add("project-detail-main-compact");
            main.setMaxWidth(Double.MAX_VALUE);
            body.getChildren().addAll(sidebar, main);
            return body;
        }

        HBox body = new HBox(0);
        body.getStyleClass().add("project-detail-body");
        body.setFillHeight(false);
        body.setMaxHeight(Double.MAX_VALUE);

        VBox main = mainContent(summary, detail);
        main.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(main, Priority.ALWAYS);

        VBox sidebar = sidebar(summary, detail);
        sidebar.setMaxHeight(Double.MAX_VALUE);
        body.getChildren().addAll(main, sidebar);
        body.minHeightProperty().bind(Bindings.createDoubleBinding(
                () -> Math.max(500, Math.max(main.getLayoutBounds().getHeight(), sidebar.getLayoutBounds().getHeight())),
                main.layoutBoundsProperty(),
                sidebar.layoutBoundsProperty()
        ));
        body.prefHeightProperty().bind(body.minHeightProperty());
        return body;
    }

    private VBox mainContent(ProjectSummary summary, ProjectDetail detail) {
        VBox main = new VBox(18);
        main.getStyleClass().add("project-detail-main");
        main.setMinWidth(0);
        addDescriptionParts(main, textAbout(summary, detail));
        Node comments = commentSection.render(
                summary,
                detail,
                currentComments,
                commentUserProfiles,
                detail == null || commentsLoading,
                commentSubmitting
        );
        if (comments != null) {
            VBox.setMargin(comments, new Insets(22, 0, 0, 0));
            main.getChildren().add(comments);
        }
        return main;
    }

    private void addDescriptionParts(VBox main, String about) {
        String normalized = value(about, "*No description.*").replace("\r\n", "\n").trim();
        java.util.regex.Matcher matcher = GALLERY_CAROUSEL_MARKER.matcher(normalized);
        int cursor = 0;
        boolean added = false;
        boolean insertedGallery = false;
        while (matcher.find()) {
            String before = normalized.substring(cursor, matcher.start()).trim();
            if (!before.isBlank()) {
                main.getChildren().add(markdownRenderer.render(before));
                added = true;
            }
            if (!insertedGallery) {
                Node carousel = inlineGalleryCarousel(main);
                if (carousel != null) {
                    main.getChildren().add(carousel);
                    added = true;
                }
                insertedGallery = true;
            }
            cursor = matcher.end();
        }
        String tail = normalized.substring(cursor).trim();
        if (!tail.isBlank()) {
            main.getChildren().add(markdownRenderer.render(tail));
            added = true;
        }
        if (!added) {
            main.getChildren().add(markdownRenderer.render(normalized));
        }
    }

    private Node inlineGalleryCarousel(VBox owner) {
        List<NativeGalleryCarousel.ImageItem> images = galleryItems();
        if (images.isEmpty()) {
            return null;
        }
        Node carousel = galleryCarousel.render(images, 0, NativeGalleryCarousel.Variant.INLINE);
        if (carousel instanceof Region region) {
            region.prefWidthProperty().bind(Bindings.createDoubleBinding(
                    () -> {
                        double width = owner.getWidth();
                        return Double.isFinite(width) && width > 1 ? width : 1080;
                    },
                    owner.widthProperty()
            ));
            region.maxWidthProperty().bind(region.prefWidthProperty());
        }
        return carousel;
    }

    private Node prose(String content) {
        VBox prose = new VBox(14);
        prose.getStyleClass().add("project-detail-prose");
        String normalized = value(content, "*No description.*").replace("\r\n", "\n").trim();
        String[] blocks = normalized.split("\\n\\s*\\n");
        for (String block : blocks) {
            List<Node> blockNodes = markdownBlock(block.trim());
            prose.getChildren().addAll(blockNodes);
        }
        return prose;
    }

    private List<Node> markdownBlock(String block) {
        if (block.isBlank()) {
            return List.of();
        }
        List<String> lines = block.lines().toList();
        String first = lines.getFirst().trim();
        if (first.startsWith("### ")) {
            return List.of(proseHeading(first.substring(4), "project-detail-prose-h3"));
        }
        if (first.startsWith("## ")) {
            return List.of(proseHeading(first.substring(3), "project-detail-prose-h2"));
        }
        if (first.startsWith("# ")) {
            return List.of(proseHeading(first.substring(2), "project-detail-prose-h1"));
        }
        if (lines.stream().allMatch(line -> line.trim().startsWith("- ") || line.trim().startsWith("* "))) {
            VBox list = new VBox(8);
            list.getStyleClass().add("project-detail-prose-list");
            for (String line : lines) {
                HBox item = new HBox(10);
                item.setAlignment(Pos.TOP_LEFT);
                Label bullet = new Label("•");
                bullet.getStyleClass().add("project-detail-prose-bullet");
                Label text = proseParagraph(line.trim().substring(2));
                item.getChildren().addAll(bullet, text);
                list.getChildren().add(item);
            }
            return List.of(list);
        }
        return List.of(proseParagraph(stripMarkdown(block.replace('\n', ' '))));
    }

    private Label proseHeading(String value, String styleClass) {
        Label heading = new Label(stripMarkdown(value));
        heading.getStyleClass().add(styleClass);
        heading.setWrapText(true);
        return heading;
    }

    private Label proseParagraph(String value) {
        Label paragraph = new Label(stripMarkdown(value));
        paragraph.getStyleClass().add("project-detail-prose-p");
        paragraph.setWrapText(true);
        paragraph.setMaxWidth(Double.MAX_VALUE);
        return paragraph;
    }

    private VBox sidebar(ProjectSummary summary, ProjectDetail detail) {
        VBox sidebar = new VBox(24);
        sidebar.getStyleClass().add("project-detail-sidebar");
        sidebar.getChildren().add(stats(summary, detail));
        Node versions = supportedVersions(summary, detail);
        if (versions != null) {
            sidebar.getChildren().add(versions);
        }
        sidebar.getChildren().add(tagsSection(detail));
        Node dependencies = dependenciesSection(summary, detail);
        if (dependencies != null) {
            sidebar.getChildren().add(dependencies);
        }
        Node incompatible = incompatibleSection(summary, detail);
        if (incompatible != null) {
            sidebar.getChildren().add(incompatible);
        }
        if (!isBlank(textLicense(detail))) {
            sidebar.getChildren().add(simpleSection("License", LauncherIcons.Glyph.SCALE, textLicense(detail)));
        }
        sidebar.getChildren().add(simpleSection("Project ID", LauncherIcons.Glyph.HASH, value(textId(summary, detail), "Unknown")));
        return sidebar;
    }

    private VBox mobileSidebar(ProjectSummary summary, ProjectDetail detail) {
        VBox sidebar = new VBox(0);
        sidebar.getStyleClass().add("project-detail-sidebar");
        sidebar.getChildren().add(stats(summary, detail));
        return sidebar;
    }

    private Node stats(ProjectSummary summary, ProjectDetail detail) {
        GridPane stats = new GridPane();
        stats.getStyleClass().add("project-detail-stat-grid");
        stats.setMaxWidth(Double.MAX_VALUE);
        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(50);
        left.setFillWidth(true);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(50);
        right.setFillWidth(true);
        stats.getColumnConstraints().addAll(left, right);
        Node favorites = statNumber(number(favorites(summary, detail)), "Favorites", LauncherIcons.Glyph.HEART);
        Node downloads = statNumber(number(downloads(summary, detail)), "Downloads", LauncherIcons.Glyph.DOWNLOAD);
        GridPane.setHgrow(favorites, Priority.ALWAYS);
        GridPane.setHgrow(downloads, Priority.ALWAYS);
        stats.add(favorites, 0, 0);
        stats.add(downloads, 1, 0);
        return stats;
    }

    private Node statNumber(String value, String label, LauncherIcons.Glyph glyph) {
        VBox box = new VBox(6);
        box.getStyleClass().add("project-detail-stat-box");
        box.setAlignment(Pos.TOP_CENTER);
        box.setMaxWidth(Double.MAX_VALUE);
        Label number = new Label(value);
        number.getStyleClass().add("project-detail-stat-number");
        HBox caption = new HBox(6, LauncherIcons.icon(glyph, 14), new Label(label.toUpperCase(Locale.ROOT)));
        caption.getStyleClass().add("project-detail-stat-caption");
        caption.setAlignment(Pos.CENTER);
        box.getChildren().addAll(number, caption);
        return box;
    }

    private Node supportedVersions(ProjectSummary summary, ProjectDetail detail) {
        Set<String> versions = new LinkedHashSet<>();
        versions(summary, detail).forEach(version -> versions.addAll(version.gameVersions()));
        if (versions.isEmpty()) {
            return null;
        }
        List<String> sorted = versions.stream()
                .sorted(Comparator.reverseOrder())
                .toList();
        FlowPane chips = new FlowPane(8, 8);
        chips.getStyleClass().add("project-detail-chip-flow");
        sorted.forEach(version -> chips.getChildren().add(chip(version, "project-detail-version-chip")));
        return section("Supported Versions", LauncherIcons.Glyph.ZAP, chips);
    }

    private Node tagsSection(ProjectDetail detail) {
        FlowPane chips = new FlowPane(8, 8);
        chips.getStyleClass().add("project-detail-chip-flow");
        List<String> tags = detail == null ? List.of() : detail.tags();
        if (tags.isEmpty()) {
            Label empty = new Label("No tags.");
            empty.getStyleClass().add("project-detail-empty-copy");
            chips.getChildren().add(empty);
        } else {
            tags.forEach(tag -> chips.getChildren().add(chip(tag, "project-detail-tag-chip")));
        }
        return section("Tags", LauncherIcons.Glyph.TAG, chips);
    }

    private Node dependenciesSection(ProjectSummary summary, ProjectDetail detail) {
        List<ProjectDependency> dependencies = latestDependencies(summary, detail);
        if (dependencies.isEmpty()) {
            return null;
        }
        boolean modpack = isModpack(summary, detail);
        VBox list = new VBox(8);
        list.getStyleClass().add("project-detail-dependency-list");
        List<ProjectDependency> external = dependencies.stream()
                .filter(ProjectDependency::isExternal)
                .toList();
        if (!external.isEmpty()) {
            list.getChildren().add(externalDependencyNotice(external, modpack));
        }
        for (ProjectDependency dependency : dependencies) {
            list.getChildren().add(dependencyCard(dependency, modpack));
        }
        return section(modpack ? "Included Projects" : "Dependencies",
                modpack ? LauncherIcons.Glyph.BOX : LauncherIcons.Glyph.EXTERNAL_LINK,
                list);
    }

    private void requestDependencyMetadata(ProjectSummary summary, ProjectDetail detail) {
        List<String> missing = latestDependencies(summary, detail).stream()
                .filter(dependency -> dependency != null && !dependency.isExternal())
                .map(ProjectDependency::projectId)
                .filter(id -> !isBlank(id))
                .filter(id -> !dependencyMetaCache.containsKey(id) && requestedDependencyMetaIds.add(id))
                .distinct()
                .toList();
        if (missing.isEmpty()) {
            return;
        }
        CompletableFuture.supplyAsync(() -> apiClient.getProjectMetaBatch(missing), executor)
                .whenComplete((result, error) -> Platform.runLater(() -> {
                    boolean changed = false;
                    if (error == null && result != null) {
                        for (String id : missing) {
                            ProjectMeta meta = result.get(id);
                            if (meta != null) {
                                dependencyMetaCache.put(id, meta);
                                changed = true;
                            }
                        }
                    }
                    for (String id : missing) {
                        if (!dependencyMetaCache.containsKey(id)) {
                            dependencyMetaCache.put(id, dependencyFallbackMeta(id));
                            changed = true;
                        }
                    }
                    if (changed && currentProject != null && sameProject(currentProject, summary)) {
                        renderProject(false);
                    }
                }));
    }

    private Node incompatibleSection(ProjectSummary summary, ProjectDetail detail) {
        List<String> incompatibleProjectIds = latestIncompatibleProjectIds(summary, detail);
        if (incompatibleProjectIds.isEmpty()) {
            return null;
        }
        VBox list = new VBox(8);
        list.getStyleClass().add("project-detail-dependency-list");
        for (String projectId : incompatibleProjectIds) {
            if (!isBlank(projectId)) {
                list.getChildren().add(incompatibleCard(projectId));
            }
        }
        return list.getChildren().isEmpty()
                ? null
                : section("Incompatible With", LauncherIcons.Glyph.ALERT_CIRCLE, list);
    }

    private Node externalDependencyNotice(List<ProjectDependency> dependencies, boolean modpack) {
        VBox notice = new VBox(7);
        notice.getStyleClass().add("project-detail-dependency-notice");
        HBox header = new HBox(7, LauncherIcons.icon(LauncherIcons.Glyph.ALERT_CIRCLE, 13), new Label("External service references"));
        header.getStyleClass().add("project-detail-dependency-notice-heading");
        header.setAlignment(Pos.CENTER_LEFT);
        notice.getChildren().add(header);
        for (ProjectDependency dependency : dependencies) {
            Label copy = new Label("%s is an external %s from %s%s".formatted(
                    dependencyNoticeTitle(dependency),
                    modpack ? "modpack entry" : "dependency",
                    sourceLabel(dependency.source()),
                    isBlank(dependency.externalFileUrl())
                            ? "."
                            : "; its file is also sourced from " + sourceLabel(dependency.source()) + "."
            ));
            copy.getStyleClass().add("project-detail-dependency-notice-copy");
            copy.setWrapText(true);
            notice.getChildren().add(copy);
        }
        return notice;
    }

    private Node dependencyCard(ProjectDependency dependency, boolean modpack) {
        HBox card = new HBox(12);
        card.getStyleClass().add("project-detail-dependency-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setCursor(Cursor.HAND);
        card.setMaxWidth(Double.MAX_VALUE);

        StackPane icon = dependencyIcon(dependency);
        VBox copy = new VBox(4);
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);
        Label title = new Label(dependencyTitle(dependency));
        title.getStyleClass().add("project-detail-dependency-title");
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.setMaxWidth(Double.MAX_VALUE);

        HBox meta = new HBox(8);
        meta.getStyleClass().add("project-detail-dependency-meta");
        meta.setAlignment(Pos.CENTER_LEFT);
        if (!modpack) {
            Label type = new Label(dependencyTypeLabel(dependency));
            type.getStyleClass().add(dependency.isOptional()
                    ? "project-detail-dependency-type"
                    : "project-detail-dependency-type-required");
            meta.getChildren().add(type);
        }
        if (dependency.isExternal()) {
            Label source = new Label(sourceLabel(dependency.source()));
            source.getStyleClass().add("project-detail-dependency-source");
            meta.getChildren().add(source);
        }
        if (!modpack && dependency.isEmbedded() && dependency.isExternal()) {
            Label bundled = new Label("Bundled");
            bundled.getStyleClass().add("project-detail-dependency-bundled");
            meta.getChildren().add(bundled);
        }
        if (!isBlank(dependency.versionNumber())) {
            Label version = new Label("v" + dependency.versionNumber());
            version.getStyleClass().add("project-detail-dependency-version");
            meta.getChildren().add(version);
        }
        copy.getChildren().addAll(title, meta);
        card.getChildren().addAll(icon, copy);

        if (dependency.isExternal()) {
            card.setOnMouseClicked(event -> openUrlInBrowser(absoluteExternalUrl(first(
                    dependency.externalUrl(),
                    dependency.externalFileUrl()
            ))));
        } else {
            card.setOnMouseClicked(event -> openDependencyProject(dependency));
        }
        return card;
    }

    private Node incompatibleCard(String projectId) {
        HBox card = new HBox(12);
        card.getStyleClass().addAll("project-detail-dependency-card", "incompatible");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setCursor(Cursor.HAND);
        StackPane icon = new StackPane(LauncherIcons.icon(LauncherIcons.Glyph.ALERT_CIRCLE, 16));
        icon.getStyleClass().add("project-detail-dependency-icon");
        icon.getStyleClass().add("incompatible");
        Label title = new Label(projectId);
        title.getStyleClass().add("project-detail-dependency-title");
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        HBox.setHgrow(title, Priority.ALWAYS);
        VBox copy = new VBox(4);
        copy.setMinWidth(0);
        Label status = new Label("Incompatible");
        status.getStyleClass().add("project-detail-dependency-incompatible");
        copy.getChildren().addAll(title, status);
        HBox.setHgrow(copy, Priority.ALWAYS);
        card.getChildren().addAll(icon, copy);
        card.setOnMouseClicked(event -> openProject(new ProjectSummary(
                projectId,
                projectId,
                projectId,
                "",
                "",
                "",
                "",
                "",
                "PLUGIN",
                0,
                0,
                "",
                List.of()
        )));
        return card;
    }

    private StackPane dependencyIcon(ProjectDependency dependency) {
        StackPane icon = new StackPane();
        icon.getStyleClass().add("project-detail-dependency-icon");
        icon.setClip(roundedClip(icon, 8));
        String iconUrl = dependencyIconUrl(dependency);
        if (!isBlank(iconUrl)) {
            ImageView image = new ImageView();
            image.setPreserveRatio(false);
            image.setSmooth(true);
            image.setFitWidth(32);
            image.setFitHeight(32);
            imageLoader.loadInto(image, iconUrl, 64, 64);
            icon.getChildren().add(image);
        } else {
            icon.getChildren().add(LauncherIcons.icon(
                    dependency.isExternal() ? LauncherIcons.Glyph.EXTERNAL_LINK : LauncherIcons.Glyph.BOX,
                    16
            ));
        }
        return icon;
    }

    private void openDependencyProject(ProjectDependency dependency) {
        ProjectMeta meta = dependencyMeta(dependency);
        String id = first(meta == null ? null : meta.slug(), dependency.slug(), dependency.projectId(), dependency.id());
        if (isBlank(id)) {
            return;
        }
        openProject(new ProjectSummary(
                value(dependency.projectId(), id),
                value(first(dependency.slug(), meta == null ? null : meta.slug()), ""),
                dependencyTitle(dependency),
                "",
                "",
                "",
                value(dependencyIconUrl(dependency), ""),
                "",
                value(first(dependency.classification(), meta == null ? null : meta.classification()), "PLUGIN"),
                0,
                0,
                "",
                List.of()
        ));
    }

    private Node simpleSection(String title, LauncherIcons.Glyph icon, String value) {
        Label label = new Label(value);
        label.getStyleClass().add("project-detail-simple-value");
        label.setWrapText(true);
        return section(title, icon, label);
    }

    private Node section(String title, LauncherIcons.Glyph icon, Node content) {
        VBox section = new VBox(10);
        section.getStyleClass().add("project-detail-sidebar-section");
        HBox header = new HBox(8, LauncherIcons.icon(icon, 13), new Label(title.toUpperCase(Locale.ROOT)));
        header.getStyleClass().add("project-detail-sidebar-heading");
        header.setAlignment(Pos.CENTER_LEFT);
        section.getChildren().addAll(header, content);
        return section;
    }

    private Label chip(String value, String styleClass) {
        Label chip = new Label(value);
        chip.getStyleClass().add(styleClass);
        return chip;
    }

    private List<ProjectDependency> latestDependencies(ProjectSummary summary, ProjectDetail detail) {
        return latestVersion(summary, detail)
                .map(ProjectVersion::dependencies)
                .orElse(List.of());
    }

    private List<String> latestIncompatibleProjectIds(ProjectSummary summary, ProjectDetail detail) {
        return latestVersion(summary, detail)
                .map(ProjectVersion::incompatibleProjectIds)
                .orElse(List.of());
    }

    private java.util.Optional<ProjectVersion> latestVersion(ProjectSummary summary, ProjectDetail detail) {
        return versions(summary, detail).stream()
                .max(ProjectPageController::compareVersionDateAscending);
    }

    private String dependencyTitle(ProjectDependency dependency) {
        ProjectMeta meta = dependencyMeta(dependency);
        return first(
                meta == null ? null : meta.title(),
                dependency.title(),
                dependency.projectTitle(),
                dependency.projectId(),
                dependency.externalId(),
                dependency.id(),
                "External dependency"
        );
    }

    private String dependencyIconUrl(ProjectDependency dependency) {
        if (dependency == null || dependency.isExternal()) {
            return "";
        }
        ProjectMeta meta = dependencyMeta(dependency);
        return first(meta == null ? null : meta.icon(), dependency.icon());
    }

    private ProjectMeta dependencyMeta(ProjectDependency dependency) {
        if (dependency == null || isBlank(dependency.projectId())) {
            return null;
        }
        return dependencyMetaCache.get(dependency.projectId());
    }

    private static ProjectMeta dependencyFallbackMeta(String projectId) {
        return new ProjectMeta("", "", "", "", "", 0, "", "");
    }

    private String dependencyNoticeTitle(ProjectDependency dependency) {
        return first(
                dependency.projectTitle(),
                dependency.projectId(),
                dependency.title(),
                dependency.externalId(),
                dependency.id(),
                "External dependency"
        );
    }

    private String dependencyTypeLabel(ProjectDependency dependency) {
        if (dependency.isEmbedded()) {
            return "Embedded";
        }
        if (dependency.isOptional()) {
            return "Optional";
        }
        return "Required";
    }

    private String sourceLabel(String source) {
        return switch (value(source, "").toUpperCase(Locale.ROOT)) {
            case "CURSEFORGE" -> "CurseForge";
            case "GITHUB" -> "GitHub";
            case "WEBSITE" -> "Website";
            case "MODTALE" -> "Modtale";
            case "OTHER" -> "External";
            default -> "External";
        };
    }

    private StackPane projectIcon(ProjectSummary summary, ProjectDetail detail, double size, double borderWidth) {
        StackPane icon = new StackPane();
        icon.getStyleClass().add("project-detail-icon");
        icon.setMinSize(size, size);
        icon.setPrefSize(size, size);
        icon.setMaxSize(size, size);
        icon.setPadding(new Insets(borderWidth));

        double mediaSize = size - borderWidth * 2;
        StackPane media = new StackPane();
        media.getStyleClass().add("project-detail-icon-media");
        media.setMinSize(mediaSize, mediaSize);
        media.setPrefSize(mediaSize, mediaSize);
        media.setMaxSize(mediaSize, mediaSize);
        media.setClip(roundedClip(media, 24));

        String imageUrl = first(detail == null ? null : detail.imageUrl(), summary.imageUrl());
        if (isBlank(imageUrl)) {
            Label initial = new Label(initialFor(textTitle(summary, detail)));
            initial.getStyleClass().add("project-detail-icon-initial");
            media.getStyleClass().add("project-detail-icon-fallback");
            media.getChildren().add(initial);
        } else {
            Region backdrop = new Region();
            backdrop.getStyleClass().add("project-detail-icon-backdrop");
            backdrop.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            ImageView image = new ImageView();
            image.setPreserveRatio(false);
            image.setSmooth(true);
            image.setFitWidth(mediaSize);
            image.setFitHeight(mediaSize);
            imageLoader.loadInto(image, imageUrl, mediaSize * 2, mediaSize * 2);
            media.getChildren().addAll(backdrop, image);
        }
        icon.getChildren().add(media);
        return icon;
    }

    private HBox classificationBadge(String classification, double iconSize) {
        HBox badge = new HBox(7);
        badge.getStyleClass().add("project-detail-classification-badge");
        badge.setAlignment(Pos.CENTER);
        badge.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        Label label = new Label(ProjectClassification.labelFor(classification).toUpperCase(Locale.ROOT));
        label.getStyleClass().add("project-detail-classification-label");
        badge.getChildren().addAll(
                LauncherIcons.icon(BrowseOptions.classification(classification).icon(), iconSize),
                label
        );
        return badge;
    }

    private Button headerIconButton(LauncherIcons.Glyph glyph, String accessibleText, Runnable action, boolean selected) {
        Button button = new Button(null, LauncherIcons.icon(glyph, 18));
        button.getStyleClass().add("project-detail-icon-button");
        button.setAccessibleText(accessibleText);
        button.pseudoClassStateChanged(SELECTED, selected);
        button.setOnAction(event -> action.run());
        return button;
    }

    private Button actionButton(String label, LauncherIcons.Glyph glyph, String url, boolean enabled) {
        return actionButton(label, glyph, () -> openUrlInBrowser(url), enabled);
    }

    private Button actionButton(String label, LauncherIcons.Glyph glyph, Runnable action, boolean enabled) {
        Button button = secondaryButton(label);
        button.getStyleClass().add("project-detail-secondary-action");
        button.setGraphic(LauncherIcons.icon(glyph, 15));
        button.setDisable(!enabled);
        button.setOnAction(event -> action.run());
        return button;
    }

    private List<Button> compactActionButtons(ProjectDetail detail) {
        List<Button> buttons = new ArrayList<>();
        if (hasWiki(detail)) {
            buttons.add(actionButton("Wiki", LauncherIcons.Glyph.BOOK_OPEN, projectUrl() + "/wiki", true));
        }
        if (hasGallery() && !hasInlineGalleryCarousel(detail)) {
            buttons.add(actionButton("Gallery", LauncherIcons.Glyph.IMAGE, () -> showGalleryModal(0), true));
        }
        buttons.add(actionButton("Changelog", LauncherIcons.Glyph.LIST, this::showChangelogModal, true));
        buttons.add(actionButton("Comments", LauncherIcons.Glyph.MESSAGE_SQUARE, this::scrollToComments, true));
        return buttons;
    }

    private void showGalleryModal(int initialIndex) {
        StackPane host = overlayHost();
        if (host == null) {
            if (toast != null) {
                toast.accept("Gallery unavailable", "The launcher window is not ready yet.");
            }
            return;
        }

        hideChangelogOverlay();
        hideGalleryOverlay();
        hideCommentDeleteOverlay();
        hideActionModals();

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("project-gallery-overlay");
        overlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        overlay.setFocusTraversable(true);
        overlay.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                hideGalleryOverlay();
                event.consume();
            }
        });
        overlay.setOnMouseClicked(event -> {
            if (event.getTarget() == overlay) {
                hideGalleryOverlay();
            }
        });

        StackPane modal = galleryModal(initialIndex, host);
        modal.setOnMouseClicked(event -> event.consume());
        overlay.getChildren().add(modal);
        blurOverlayBackdrop(host);
        host.getChildren().add(overlay);
        galleryOverlay = overlay;
        Platform.runLater(overlay::requestFocus);
    }

    private StackPane galleryModal(int initialIndex, StackPane host) {
        StackPane modal = new StackPane();
        modal.getStyleClass().add("project-gallery-modal");
        modal.setMaxWidth(1152);
        modal.setMaxHeight(Region.USE_PREF_SIZE);
        modal.setMinWidth(0);
        List<NativeGalleryCarousel.ImageItem> images = galleryItems();
        if (images.isEmpty()) {
            Label empty = new Label("No images in this gallery.");
            empty.getStyleClass().add("project-gallery-empty");
            StackPane.setAlignment(empty, Pos.CENTER);
            modal.getChildren().add(empty);
        } else {
            Node carousel = galleryCarousel.render(images, initialIndex, NativeGalleryCarousel.Variant.MODAL);
            if (carousel instanceof Region region) {
                region.prefWidthProperty().bind(Bindings.createDoubleBinding(
                        () -> {
                            double availableWidth = Math.max(320, host.getWidth() - 64);
                            double availableHeight = Math.max(260, host.getHeight() * 0.90 - 128);
                            return Math.min(Math.min(1152, availableWidth), availableHeight * 16.0 / 9.0);
                        },
                        host.widthProperty(),
                        host.heightProperty()
                ));
                region.setMaxWidth(Region.USE_PREF_SIZE);
                region.setMaxHeight(Region.USE_PREF_SIZE);
            }
            modal.getChildren().add(carousel);
        }

        Button close = new Button(null, LauncherIcons.icon(LauncherIcons.Glyph.X, 20));
        close.getStyleClass().add("project-gallery-floating-close");
        close.setAccessibleText("Close gallery");
        close.setOnAction(event -> hideGalleryOverlay());
        StackPane.setAlignment(close, Pos.TOP_RIGHT);
        StackPane.setMargin(close, new Insets(12, 12, 0, 0));
        modal.getChildren().add(close);
        return modal;
    }

    private void showDeleteCommentModal(ProjectComment comment) {
        if (comment == null) {
            return;
        }
        StackPane host = overlayHost();
        if (host == null) {
            if (toast != null) {
                toast.accept("Delete unavailable", "The launcher window is not ready yet.");
            }
            return;
        }
        hideChangelogOverlay();
        hideGalleryOverlay();
        hideCommentDeleteOverlay();
        hideActionModals();

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("project-comment-delete-overlay");
        overlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        overlay.setFocusTraversable(true);
        overlay.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                hideCommentDeleteOverlay();
                event.consume();
            }
        });
        overlay.setOnMouseClicked(event -> {
            if (event.getTarget() == overlay) {
                hideCommentDeleteOverlay();
            }
        });

        VBox modal = new VBox(18);
        modal.getStyleClass().add("project-comment-delete-modal");
        modal.setMaxWidth(420);
        modal.setOnMouseClicked(event -> event.consume());
        HBox title = new HBox(10);
        title.getStyleClass().add("project-comment-delete-title-row");
        title.setAlignment(Pos.CENTER_LEFT);
        Label titleText = new Label("Delete Comment?");
        titleText.getStyleClass().add("project-comment-delete-title");
        title.getChildren().addAll(LauncherIcons.icon(LauncherIcons.Glyph.TRASH, 20), titleText);
        Label message = new Label("This will permanently remove the selected comment from the discussion.");
        message.getStyleClass().add("project-comment-delete-message");
        message.setWrapText(true);
        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button cancel = secondaryButton("Cancel");
        cancel.getStyleClass().add("project-comment-delete-cancel");
        cancel.setOnAction(event -> hideCommentDeleteOverlay());
        Button delete = primaryButton("Delete Comment");
        delete.getStyleClass().add("project-comment-delete-confirm");
        delete.setOnAction(event -> confirmDeleteComment(comment));
        actions.getChildren().addAll(cancel, delete);
        modal.getChildren().addAll(title, message, actions);

        overlay.getChildren().add(modal);
        blurOverlayBackdrop(host);
        host.getChildren().add(overlay);
        commentDeleteOverlay = overlay;
        Platform.runLater(overlay::requestFocus);
    }

    private void hideCommentDeleteOverlay() {
        if (commentDeleteOverlay == null) {
            return;
        }
        Parent parent = commentDeleteOverlay.getParent();
        if (parent instanceof StackPane stack) {
            stack.getChildren().remove(commentDeleteOverlay);
        }
        commentDeleteOverlay = null;
        restoreOverlayBackdrop();
    }

    private void scrollToComments() {
        if (attachedScrollPane == null) {
            return;
        }
        Platform.runLater(() -> {
            Node target = content.lookup("#comments");
            Node scrollContent = attachedScrollPane.getContent();
            if (target == null || scrollContent == null) {
                return;
            }
            Bounds targetBounds = target.localToScene(target.getBoundsInLocal());
            Bounds contentBounds = scrollContent.localToScene(scrollContent.getBoundsInLocal());
            double viewportHeight = attachedScrollPane.getViewportBounds().getHeight();
            double scrollable = scrollContent.getLayoutBounds().getHeight() - viewportHeight;
            if (!Double.isFinite(scrollable) || scrollable <= 0) {
                return;
            }
            double targetY = targetBounds.getMinY() - contentBounds.getMinY() - 100;
            double normalized = Math.max(0, Math.min(1, targetY / scrollable));
            double min = attachedScrollPane.getVmin();
            double max = attachedScrollPane.getVmax();
            attachedScrollPane.setVvalue(min + normalized * (max - min));
        });
    }

    private void openCommentProfile(String userId, String username) {
        String handle = first(username, userId);
        if (isBlank(handle)) {
            return;
        }
        openCreator(new ProjectSummary(
                value(userId, handle),
                value(userId, handle),
                "Creator",
                "",
                value(userId, handle),
                handle,
                "",
                "",
                "PLUGIN",
                0,
                0,
                "",
                List.of()
        ));
    }

    private void showChangelogModal() {
        if (currentProject == null) {
            return;
        }
        boolean loading = needsChangelogHydration();
        rebuildChangelogOverlay(loading);
        if (loading) {
            hydrateChangelogs();
        }
    }

    private void rebuildChangelogOverlay(boolean loading) {
        StackPane host = overlayHost();
        if (host == null) {
            if (toast != null) {
                toast.accept("Changelog unavailable", "The launcher window is not ready yet.");
            }
            return;
        }

        hideChangelogOverlay();
        hideGalleryOverlay();
        hideCommentDeleteOverlay();
        hideActionModals();
        List<ChangelogEntry> entries = changelogEntries();

        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("project-changelog-overlay");
        overlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        overlay.setFocusTraversable(true);
        overlay.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                hideChangelogOverlay();
                event.consume();
            }
        });
        overlay.setOnMouseClicked(event -> {
            if (event.getTarget() == overlay) {
                hideChangelogOverlay();
            }
        });

        VBox modal = changelogModal(entries, loading);
        modal.setOnMouseClicked(event -> event.consume());
        modal.setMaxHeight(Region.USE_PREF_SIZE);
        overlay.getChildren().add(modal);
        blurOverlayBackdrop(host);
        host.getChildren().add(overlay);
        StackPane.setAlignment(overlay, Pos.CENTER);
        changelogOverlay = overlay;
        Platform.runLater(overlay::requestFocus);
    }

    private VBox changelogModal(List<ChangelogEntry> entries, boolean loading) {
        VBox modal = new VBox(0);
        modal.getStyleClass().add("project-changelog-modal");
        modal.setMaxWidth(768);
        modal.setMinWidth(0);

        HBox header = changelogHeader(entries);
        ScrollPane scroll = new ScrollPane();
        scroll.getStyleClass().add("project-changelog-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        VBox list = new VBox(24);
        list.getStyleClass().add("project-changelog-list");
        List<ChangelogEntry> visibleEntries = visibleChangelogEntries(entries);
        if (visibleEntries.isEmpty()) {
            VBox empty = new VBox(8);
            empty.setAlignment(Pos.CENTER);
            empty.getStyleClass().add("project-changelog-empty");
            Label subtitle = new Label(loading
                    ? "Fetching version notes from Modtale."
                    : "This project does not have a public changelog yet.");
            subtitle.getStyleClass().add("project-changelog-empty-subtitle");
            if (loading) {
                empty.getChildren().add(NativeSpinner.centered());
            } else {
                Label title = new Label("No versions to show.");
                title.getStyleClass().add("project-changelog-empty-title");
                empty.getChildren().add(title);
            }
            empty.getChildren().add(subtitle);
            list.getChildren().add(empty);
        } else {
            visibleEntries.forEach(entry -> list.getChildren().add(changelogCard(entry)));
            if (loading) {
                list.getChildren().add(NativeSpinner.inline(16));
            }
        }

        scroll.setContent(list);
        scroll.setPrefViewportHeight(preferredChangelogViewportHeight(visibleEntries));
        modal.getChildren().addAll(header, scroll);
        return modal;
    }

    private HBox changelogHeader(List<ChangelogEntry> entries) {
        HBox header = new HBox(16);
        header.getStyleClass().add("project-changelog-header");
        header.setAlignment(Pos.CENTER_LEFT);

        VBox copy = new VBox(5);
        copy.setMinWidth(0);
        HBox titleRow = new HBox(8);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Changelog");
        title.getStyleClass().add("project-changelog-title");
        titleRow.getChildren().addAll(LauncherIcons.icon(LauncherIcons.Glyph.LIST, 20), title);
        copy.getChildren().add(titleRow);

        if (hasExperimentalChangelogs(entries) && hasStableChangelogs(entries)) {
            copy.getChildren().add(experimentalToggle());
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button close = new Button(null, LauncherIcons.icon(LauncherIcons.Glyph.X, 20));
        close.getStyleClass().add("project-changelog-close");
        close.setAccessibleText("Close Changelog");
        close.setOnAction(event -> hideChangelogOverlay());

        header.getChildren().addAll(copy, spacer, close);
        return header;
    }

    private HBox experimentalToggle() {
        HBox row = new HBox(8);
        row.getStyleClass().add("project-changelog-experimental-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setCursor(Cursor.HAND);

        StackPane track = new StackPane();
        track.getStyleClass().add("project-changelog-toggle");
        track.pseudoClassStateChanged(SELECTED, showExperimentalChangelogs);
        Region thumb = new Region();
        thumb.getStyleClass().add("project-changelog-toggle-thumb");
        thumb.setTranslateX(showExperimentalChangelogs ? 8 : -8);
        track.getChildren().add(thumb);

        Label label = new Label("Show Beta/Alpha");
        label.getStyleClass().add("project-changelog-toggle-label");
        row.getChildren().addAll(track, label);
        row.setOnMouseClicked(event -> {
            showExperimentalChangelogs = !showExperimentalChangelogs;
            rebuildChangelogOverlay(false);
        });
        return row;
    }

    private Node changelogCard(ChangelogEntry entry) {
        VBox card = new VBox(0);
        card.getStyleClass().add("project-changelog-card");
        card.setMinHeight(preferredChangelogCardHeight(entry));

        HBox header = new HBox(16);
        header.getStyleClass().add("project-changelog-card-header");
        header.setAlignment(Pos.CENTER_LEFT);

        VBox meta = new VBox(7);
        meta.setMinWidth(0);
        HBox.setHgrow(meta, Priority.ALWAYS);
        HBox versionLine = new HBox(10);
        versionLine.setAlignment(Pos.CENTER_LEFT);
        Label version = new Label("v" + value(entry.versionNumber(), "Unknown"));
        version.getStyleClass().add("project-changelog-version");
        versionLine.getChildren().add(version);
        if (!isRelease(entry.channel())) {
            versionLine.getChildren().add(channelBadge(entry.channel()));
        }
        meta.getChildren().add(versionLine);

        HBox detailLine = new HBox(10);
        detailLine.getStyleClass().add("project-changelog-meta");
        detailLine.setAlignment(Pos.CENTER_LEFT);
        addMeta(detailLine, timeAgo(entry.releaseDate()));
        addMeta(detailLine, String.join(", ", entry.gameVersions()));
        addDownloadMeta(detailLine, entry.downloadCount());
        meta.getChildren().add(detailLine);

        if (isModpack() && entry.dependencies().stream().anyMatch(ProjectDependency::isExternal)) {
            HBox external = new HBox(4);
            external.getStyleClass().add("project-changelog-external-badge");
            external.setAlignment(Pos.CENTER_LEFT);
            external.getChildren().addAll(LauncherIcons.icon(LauncherIcons.Glyph.ALERT_CIRCLE, 12), new Label("External mods"));
            meta.getChildren().add(external);
        }

        Button download = secondaryButton("Download");
        download.getStyleClass().add("project-changelog-download");
        download.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.DOWNLOAD, 15));
        download.setOnAction(event -> {
            hideChangelogOverlay();
            installChangelogEntry(entry);
        });

        header.getChildren().addAll(meta, download);
        card.getChildren().add(header);

        Node body = changelogBody(entry);
        if (body != null) {
            VBox.setMargin(body, new Insets(14, 0, 0, 0));
            card.getChildren().add(body);
        }
        return card;
    }

    private void installChangelogEntry(ChangelogEntry entry) {
        if (entry == null) {
            return;
        }
        ProjectDetail selectedProject = currentDetail;
        if (selectedProject == null) {
            if (currentProject != null) {
                installProject.accept(currentProject);
            }
            return;
        }
        installProjectVersion.install(selectedProject, entry.toProjectVersion(), changelogGameVersion(entry));
    }

    private String changelogGameVersion(ChangelogEntry entry) {
        if (entry != null && !entry.gameVersions().isEmpty()) {
            return entry.gameVersions().getFirst();
        }
        return value(gameVersion.get(), "");
    }

    private List<NativeGalleryCarousel.ImageItem> galleryItems() {
        String title = textTitle(currentProject, currentDetail);
        List<NativeGalleryCarousel.ImageItem> items = new ArrayList<>();
        for (int i = 0; i < currentGalleryImages.size(); i++) {
            String url = currentGalleryImages.get(i);
            if (!isBlank(url)) {
                items.add(new NativeGalleryCarousel.ImageItem(
                        url,
                        title + " gallery image " + (i + 1),
                        "",
                        value(currentGalleryCaptions.get(url), "")
                ));
            }
        }
        return items;
    }

    private Node changelogBody(ChangelogEntry entry) {
        String changelog = value(entry.changelog(), "");
        if (changelog.isBlank()) {
            Label empty = new Label("No changelog provided.");
            empty.getStyleClass().add("project-changelog-no-notes");
            return empty;
        }

        boolean isLong = changelog.length() > 300;
        boolean isExpanded = expandedChangelogIds.contains(entry.stableId());
        VBox body = new VBox(0);
        body.getStyleClass().add("project-changelog-markdown-wrap");
        Node markdown = markdownRenderer.render(changelog);
        if (isLong && !isExpanded) {
            StackPane clamp = new StackPane(markdown);
            clamp.getStyleClass().add("project-changelog-markdown-clamp");
            Rectangle clip = new Rectangle();
            clip.widthProperty().bind(clamp.widthProperty());
            clip.heightProperty().bind(clamp.heightProperty());
            clamp.setClip(clip);
            StackPane.setAlignment(markdown, Pos.TOP_LEFT);
            body.getChildren().add(clamp);
        } else {
            body.getChildren().add(markdown);
        }

        if (isLong) {
            Button toggle = new Button(isExpanded ? "Show Less" : "Read More");
            toggle.getStyleClass().add("project-changelog-read-more");
            toggle.setGraphic(LauncherIcons.icon(isExpanded ? LauncherIcons.Glyph.CHEVRON_UP : LauncherIcons.Glyph.CHEVRON_DOWN, 12));
            toggle.setOnAction(event -> {
                if (isExpanded) {
                    expandedChangelogIds.remove(entry.stableId());
                } else {
                    expandedChangelogIds.add(entry.stableId());
                }
                rebuildChangelogOverlay(false);
            });
            body.getChildren().add(toggle);
        }
        return body;
    }

    private double preferredChangelogViewportHeight(List<ChangelogEntry> entries) {
        if (entries.isEmpty()) {
            return 48;
        }
        double height = 48 + Math.max(0, entries.size() - 1) * 24;
        for (ChangelogEntry entry : entries) {
            height += preferredChangelogCardHeight(entry);
        }
        return Math.min(height, 780);
    }

    private double preferredChangelogCardHeight(ChangelogEntry entry) {
        String changelog = value(entry.changelog(), "");
        if (changelog.isBlank()) {
            return 122;
        }
        return changelog.length() > 72 ? 198 : 171;
    }

    private Label channelBadge(String channel) {
        Label badge = new Label(value(channel, "RELEASE").toUpperCase(Locale.ROOT));
        badge.getStyleClass().addAll("project-changelog-channel", channelStyle(channel));
        return badge;
    }

    private void addMeta(HBox row, String text) {
        if (isBlank(text) || "Unknown".equals(text)) {
            return;
        }
        if (!row.getChildren().isEmpty()) {
            row.getChildren().add(metaDot());
        }
        row.getChildren().add(new Label(text.toUpperCase(Locale.ROOT)));
    }

    private void addDownloadMeta(HBox row, int downloads) {
        if (!row.getChildren().isEmpty()) {
            row.getChildren().add(metaDot());
        }
        HBox group = new HBox(5);
        group.setAlignment(Pos.CENTER_LEFT);
        group.getChildren().addAll(LauncherIcons.icon(LauncherIcons.Glyph.DOWNLOAD, 13), new Label(number(downloads)));
        row.getChildren().add(group);
    }

    private Region metaDot() {
        Region dot = new Region();
        dot.getStyleClass().add("project-changelog-meta-dot");
        return dot;
    }

    private void hideChangelogOverlay() {
        if (changelogOverlay == null) {
            return;
        }
        Parent parent = changelogOverlay.getParent();
        if (parent instanceof StackPane stack) {
            stack.getChildren().remove(changelogOverlay);
        }
        changelogOverlay = null;
        restoreOverlayBackdrop();
    }

    private void hideGalleryOverlay() {
        if (galleryOverlay == null) {
            return;
        }
        Parent parent = galleryOverlay.getParent();
        if (parent instanceof StackPane stack) {
            stack.getChildren().remove(galleryOverlay);
        }
        galleryOverlay = null;
        restoreOverlayBackdrop();
    }

    private void blurOverlayBackdrop(StackPane host) {
        restoreOverlayBackdrop();
        for (Node child : host.getChildren()) {
            overlayBackdropEffects.put(child, child.getEffect());
            child.setEffect(new GaussianBlur(6));
        }
    }

    private void restoreOverlayBackdrop() {
        overlayBackdropEffects.forEach(Node::setEffect);
        overlayBackdropEffects.clear();
    }

    private StackPane overlayHost() {
        if (content.getScene() == null) {
            return null;
        }
        Parent root = content.getScene().getRoot();
        if (root instanceof StackPane stack) {
            return stack;
        }
        for (Parent parent = content.getParent(); parent != null; parent = parent.getParent()) {
            if (parent instanceof StackPane stack) {
                return stack;
            }
        }
        return null;
    }

    private boolean needsChangelogHydration() {
        String key = changelogRouteKey();
        if (isBlank(key) || changelogCache.containsKey(key)) {
            return false;
        }
        List<ProjectVersion> projectVersions = versions(currentProject, currentDetail);
        return projectVersions.isEmpty() || projectVersions.stream().anyMatch(version -> version.changelog() == null);
    }

    private void hydrateChangelogs() {
        String key = changelogRouteKey();
        if (isBlank(key)) {
            return;
        }
        long requestId = ++changelogRequestId;
        CompletableFuture.supplyAsync(() -> apiClient.getProjectVersionChangelogs(key), executor)
                .whenComplete((changelogs, error) -> Platform.runLater(() -> {
                    if (requestId != changelogRequestId || currentProject == null
                            || !value(key, "").equals(value(changelogRouteKey(), ""))) {
                        return;
                    }
                    if (error != null) {
                        if (toast != null) {
                            Throwable cause = error.getCause() == null ? error : error.getCause();
                            toast.accept("Changelog unavailable", value(cause.getMessage(), "Could not load changelogs."));
                        }
                    } else {
                        changelogCache.put(key, changelogs == null ? List.of() : List.copyOf(changelogs));
                    }
                    if (changelogOverlay != null) {
                        rebuildChangelogOverlay(false);
                    }
                }));
    }

    private List<ChangelogEntry> changelogEntries() {
        List<ProjectVersion> projectVersions = versions(currentProject, currentDetail);
        Map<String, ProjectVersionChangelog> byId = changelogMap(true);
        Map<String, ProjectVersionChangelog> byVersion = changelogMap(false);
        if (!projectVersions.isEmpty()) {
            return projectVersions.stream()
                    .sorted(ProjectPageController::compareVersionDateDescending)
                    .map(version -> {
                        ProjectVersionChangelog hydrated = firstChangelog(version, byId, byVersion);
                        String changelog = hydrated == null ? version.changelog() : value(hydrated.changelog(), "");
                        return ChangelogEntry.from(version, changelog);
                    })
                    .toList();
        }
        return changelogCache.getOrDefault(changelogRouteKey(), List.of()).stream()
                .map(ChangelogEntry::from)
                .toList();
    }

    private List<ChangelogEntry> visibleChangelogEntries(List<ChangelogEntry> entries) {
        boolean forceShowExperimental = hasExperimentalChangelogs(entries) && !hasStableChangelogs(entries);
        boolean effectiveShowExperimental = showExperimentalChangelogs || forceShowExperimental;
        return entries.stream()
                .filter(entry -> effectiveShowExperimental || isRelease(entry.channel()))
                .toList();
    }

    private Map<String, ProjectVersionChangelog> changelogMap(boolean id) {
        List<ProjectVersionChangelog> changelogs = changelogCache.getOrDefault(changelogRouteKey(), List.of());
        java.util.HashMap<String, ProjectVersionChangelog> map = new java.util.HashMap<>();
        for (ProjectVersionChangelog changelog : changelogs) {
            String key = id ? changelog.id() : changelog.versionNumber();
            if (!isBlank(key)) {
                map.put(key, changelog);
            }
        }
        return map;
    }

    private ProjectVersionChangelog firstChangelog(
            ProjectVersion version,
            Map<String, ProjectVersionChangelog> byId,
            Map<String, ProjectVersionChangelog> byVersion
    ) {
        ProjectVersionChangelog changelog = isBlank(version.id()) ? null : byId.get(version.id());
        if (changelog != null) {
            return changelog;
        }
        return isBlank(version.versionNumber()) ? null : byVersion.get(version.versionNumber());
    }

    private boolean hasExperimentalChangelogs(List<ChangelogEntry> entries) {
        return entries.stream().anyMatch(entry -> isBetaOrAlpha(entry.channel()));
    }

    private boolean hasStableChangelogs(List<ChangelogEntry> entries) {
        return entries.stream().anyMatch(entry -> !isBetaOrAlpha(entry.channel())
                && !value(entry.versionNumber(), "").contains("-"));
    }

    private boolean isModpack() {
        return "MODPACK".equalsIgnoreCase(textClassification(currentProject, currentDetail));
    }

    private static boolean isModpack(ProjectSummary summary, ProjectDetail detail) {
        return "MODPACK".equalsIgnoreCase(textClassification(summary, detail));
    }

    private String changelogRouteKey() {
        if (currentProject != null) {
            return currentProject.routeKey();
        }
        return currentDetail == null ? "" : currentDetail.routeKey();
    }

    private HBox externalLinks(ProjectDetail detail) {
        HBox links = new HBox(8);
        links.getStyleClass().add("project-detail-external-links");
        if (detail == null) {
            return links;
        }
        if (!isBlank(detail.repositoryUrl())) {
            links.getChildren().add(externalIcon(LauncherIcons.Glyph.CODE, detail.repositoryUrl()));
        }
        Map<String, String> detailLinks = detail.links();
        if (!isBlank(detailLinks.get("DISCORD"))) {
            links.getChildren().add(externalBrandIcon(LauncherIcons.BrandGlyph.DISCORD, detailLinks.get("DISCORD")));
        }
        if (!isBlank(detailLinks.get("WEBSITE"))) {
            links.getChildren().add(externalIcon(LauncherIcons.Glyph.GLOBE, detailLinks.get("WEBSITE")));
        }
        return links;
    }

    private Node mobileExternalLinks(ProjectDetail detail) {
        if (!hasExternalLinks(detail)) {
            return null;
        }
        Button button = secondaryButton("External Links");
        button.getStyleClass().add("project-detail-external-menu-button");
        button.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.EXTERNAL_LINK, 15));
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> {
            String target = first(detail.repositoryUrl(), detail.links().get("WEBSITE"), detail.links().get("DISCORD"));
            openUrlInBrowser(absoluteExternalUrl(target));
        });
        return button;
    }

    private Button externalIcon(LauncherIcons.Glyph glyph, String url) {
        Button button = new Button(null, LauncherIcons.icon(glyph, 17));
        button.getStyleClass().add("project-detail-external-button");
        button.setOnAction(event -> openUrlInBrowser(absoluteExternalUrl(url)));
        return button;
    }

    private Button externalBrandIcon(LauncherIcons.BrandGlyph glyph, String url) {
        Button button = new Button(null, LauncherIcons.brandIcon(glyph, 17));
        button.getStyleClass().add("project-detail-external-button");
        button.setOnAction(event -> openUrlInBrowser(absoluteExternalUrl(url)));
        return button;
    }

    private Node externalState(String title, String subtitle, String actionLabel) {
        VBox state = new VBox(14);
        state.getStyleClass().add("project-link-state");
        state.setAlignment(Pos.CENTER);
        Label heading = new Label(title);
        heading.getStyleClass().add("empty-title");
        Label sub = new Label(subtitle);
        sub.getStyleClass().add("empty-subtitle");
        sub.setWrapText(true);
        sub.setMaxWidth(520);
        Button back = secondaryButton("Back");
        back.getStyleClass().add("small");
        back.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_LEFT, 14));
        back.setOnAction(event -> showDiscover.run());
        Button open = secondaryButton(actionLabel);
        open.getStyleClass().add("small");
        open.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.EXTERNAL_LINK, 14));
        open.setOnAction(event -> openCurrentInBrowser());
        HBox actions = new HBox(10, back, open);
        actions.setAlignment(Pos.CENTER);
        state.getChildren().addAll(heading, sub, actions);
        StackPane frame = new StackPane(state);
        frame.getStyleClass().add("project-link-frame");
        frame.setMaxSize(720, Region.USE_PREF_SIZE);
        StackPane shell = new StackPane(frame);
        shell.getStyleClass().add("project-external-view");
        return shell;
    }

    private void toggleFavorite() {
        if (currentProject == null) {
            return;
        }
        toggleFavorite.accept(currentProject);
        renderProject(false);
    }

    private boolean isFavorite(ProjectSummary project) {
        return project != null && Boolean.TRUE.equals(favoriteResolver.apply(project.id()));
    }

    private void openCurrentInBrowser() {
        openUrlInBrowser(currentUrl);
    }

    private void openUrlInBrowser(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return;
        }
        URI uri;
        try {
            uri = URI.create(rawUrl);
        } catch (IllegalArgumentException ex) {
            showBrowserError("Invalid page URL.");
            return;
        }
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            showBrowserError("Desktop browser integration is not available.");
            return;
        }
        try {
            Desktop.getDesktop().browse(uri);
        } catch (IOException | SecurityException | UnsupportedOperationException ex) {
            showBrowserError(value(ex.getMessage(), "Could not open your browser."));
        }
    }

    private void showBrowserError(String message) {
        if (toast != null) {
            toast.accept("Could not open browser", value(message, "Open the page manually: " + value(currentUrl, "")));
        }
    }

    private Rectangle roundedClip(Region owner, double radius) {
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(owner.widthProperty());
        clip.heightProperty().bind(owner.heightProperty());
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        clip.setFill(Color.BLACK);
        return clip;
    }

    private String projectUrl() {
        return currentUrl == null ? "" : currentUrl;
    }

    private static int compareVersionDateDescending(ProjectVersion left, ProjectVersion right) {
        Instant leftDate = parseDate(left == null ? null : left.releaseDate());
        Instant rightDate = parseDate(right == null ? null : right.releaseDate());
        if (leftDate != null && rightDate != null) {
            return rightDate.compareTo(leftDate);
        }
        if (leftDate != null) {
            return -1;
        }
        if (rightDate != null) {
            return 1;
        }
        return value(right == null ? null : right.versionNumber(), "")
                .compareTo(value(left == null ? null : left.versionNumber(), ""));
    }

    private static int compareVersionDateAscending(ProjectVersion left, ProjectVersion right) {
        return -compareVersionDateDescending(left, right);
    }

    private static Instant parseDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(rawDate);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDateTime.parse(rawDate).atZone(ZoneId.systemDefault()).toInstant();
            } catch (DateTimeParseException ignored) {
                try {
                    return LocalDate.parse(rawDate).atStartOfDay(ZoneId.systemDefault()).toInstant();
                } catch (DateTimeParseException ignoredAgain) {
                    return null;
                }
            }
        }
    }

    private static boolean isRelease(String channel) {
        return channel == null || channel.isBlank() || "RELEASE".equalsIgnoreCase(channel);
    }

    private static boolean isBetaOrAlpha(String channel) {
        return "BETA".equalsIgnoreCase(channel) || "ALPHA".equalsIgnoreCase(channel);
    }

    private static String channelStyle(String channel) {
        if ("BETA".equalsIgnoreCase(channel)) {
            return "beta";
        }
        if ("ALPHA".equalsIgnoreCase(channel)) {
            return "alpha";
        }
        return "release";
    }

    private record ChangelogEntry(
            String id,
            String versionNumber,
            List<String> gameVersions,
            String fileUrl,
            int downloadCount,
            String releaseDate,
            String changelog,
            List<ProjectDependency> dependencies,
            List<String> incompatibleProjectIds,
            String channel
    ) {
        static ChangelogEntry from(ProjectVersion version, String changelog) {
            return new ChangelogEntry(
                    version.id(),
                    version.versionNumber(),
                    version.gameVersions(),
                    version.fileUrl(),
                    version.downloadCount(),
                    version.releaseDate(),
                    changelog,
                    version.dependencies(),
                    version.incompatibleProjectIds(),
                    value(version.channel(), "RELEASE").toUpperCase(Locale.ROOT)
            );
        }

        static ChangelogEntry from(ProjectVersionChangelog changelog) {
            return new ChangelogEntry(
                    changelog.id(),
                    changelog.versionNumber(),
                    List.of(),
                    null,
                    0,
                    null,
                    value(changelog.changelog(), ""),
                    List.of(),
                    List.of(),
                    "RELEASE"
            );
        }

        String stableId() {
            return !isBlank(id) ? id : value(versionNumber, "version");
        }

        ProjectVersion toProjectVersion() {
            return new ProjectVersion(
                    id,
                    versionNumber,
                    gameVersions,
                    fileUrl,
                    downloadCount,
                    releaseDate,
                    changelog,
                    dependencies,
                    channel,
                    incompatibleProjectIds
            );
        }
    }

    private static boolean sameProject(ProjectSummary a, ProjectSummary b) {
        if (a == b) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return value(a.id(), "").equals(value(b.id(), ""))
                && value(a.routeKey(), "").equals(value(b.routeKey(), ""));
    }

    private static boolean isCompactWidth(double width) {
        return Double.isFinite(width) && width > 0 && width < 760;
    }

    private static List<ProjectVersion> versions(ProjectSummary summary, ProjectDetail detail) {
        if (detail != null && detail.versions() != null && !detail.versions().isEmpty()) {
            return detail.versions();
        }
        return summary == null ? List.of() : summary.versions();
    }

    private static ProjectSummary summaryFromDetail(ProjectDetail detail) {
        return new ProjectSummary(
                detail.id(),
                detail.slug(),
                detail.title(),
                detail.description(),
                detail.authorId(),
                detail.author(),
                detail.imageUrl(),
                detail.bannerUrl(),
                detail.classification(),
                detail.downloadCount(),
                detail.favoriteCount(),
                detail.updatedAt(),
                detail.versions()
        );
    }

    private static boolean hasWiki(ProjectDetail detail) {
        return detail != null && detail.hmWikiEnabled() && !isBlank(detail.hmWikiSlug());
    }

    private boolean hasGallery() {
        return !currentGalleryImages.isEmpty();
    }

    private static boolean hasInlineGalleryCarousel(ProjectDetail detail) {
        return detail != null && GALLERY_CAROUSEL_MARKER.matcher(value(detail.about(), "")).find();
    }

    private static boolean hasExternalLinks(ProjectDetail detail) {
        if (detail == null) {
            return false;
        }
        Map<String, String> links = detail.links();
        return !isBlank(detail.repositoryUrl())
                || !isBlank(links.get("DISCORD"))
                || !isBlank(links.get("WEBSITE"));
    }

    private static String textId(ProjectSummary summary, ProjectDetail detail) {
        return first(detail == null ? null : detail.id(), summary == null ? null : summary.id());
    }

    private static String textTitle(ProjectSummary summary, ProjectDetail detail) {
        return value(first(detail == null ? null : detail.title(), summary == null ? null : summary.title()), "Untitled Project");
    }

    private static String textAbout(ProjectSummary summary, ProjectDetail detail) {
        return first(detail == null ? null : detail.about(),
                detail == null ? null : detail.description(),
                summary == null ? null : summary.description());
    }

    private static String textDescription(ProjectSummary summary, ProjectDetail detail) {
        return first(detail == null ? null : detail.description(), summary == null ? null : summary.description());
    }

    private static String textAuthor(ProjectSummary summary, ProjectDetail detail) {
        return first(detail == null ? null : detail.author(), summary == null ? null : summary.author());
    }

    private static String textUpdatedAt(ProjectSummary summary, ProjectDetail detail) {
        return first(detail == null ? null : detail.updatedAt(), summary == null ? null : summary.updatedAt());
    }

    private static String textClassification(ProjectSummary summary, ProjectDetail detail) {
        return first(detail == null ? null : detail.classification(), summary == null ? null : summary.classification());
    }

    private static String textLicense(ProjectDetail detail) {
        return detail == null ? null : detail.license();
    }

    private static int downloads(ProjectSummary summary, ProjectDetail detail) {
        return detail == null ? summary.downloadCount() : detail.downloadCount();
    }

    private static int favorites(ProjectSummary summary, ProjectDetail detail) {
        return detail == null ? summary.favoriteCount() : detail.favoriteCount();
    }

    private static String stripMarkdown(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("(?is)<br\\s*/?>", "\n")
                .replaceAll("(?is)<[^>]+>", "")
                .replaceAll("!\\[([^]]*)]\\([^)]+\\)", "$1")
                .replaceAll("\\[([^]]+)]\\([^)]+\\)", "$1")
                .replace("**", "")
                .replace("__", "")
                .replace("`", "")
                .replace("*", "")
                .trim();
    }

    private static String initialFor(String value) {
        String normalized = value(value, "M").trim();
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private static String first(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value;
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String absoluteExternalUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }
        String trimmed = rawUrl.trim();
        return trimmed.startsWith("http://") || trimmed.startsWith("https://") ? trimmed : "https://" + trimmed;
    }

    private static String projectPageUrl(ProjectSummary project) {
        return LauncherConfig.siteBaseUrl().replaceAll("/+$", "") + projectPath(project);
    }

    private static String creatorPageUrl(ProjectSummary project) {
        return LauncherConfig.siteBaseUrl().replaceAll("/+$", "") + "/creator/" + encodePathSegment(creatorHandle(project));
    }

    private static String creatorHandle(ProjectSummary project) {
        if (project == null) {
            return "creator";
        }
        return value(project.author(), value(project.authorId(), "creator"));
    }

    private static boolean hasCreatorHandle(ProjectSummary project) {
        if (project == null) {
            return false;
        }
        return !isBlank(first(project.author(), project.authorId()));
    }

    private static String projectPath(ProjectSummary project) {
        return "/" + projectPrefix(project.classification()) + "/" + projectHandle(project);
    }

    private static String projectHandle(ProjectSummary project) {
        String customSlug = project.slug() == null ? "" : project.slug().trim();
        return customSlug.isBlank() ? createSlug(project.title(), project.id()) : customSlug;
    }

    private static String projectPrefix(String classification) {
        return ProjectClassification.routePrefixFor(classification);
    }

    private static String createSlug(String title, String id) {
        if (title == null || title.isBlank()) {
            return id;
        }
        String slug = title.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)+", "");
        if (slug.length() > 30) {
            slug = slug.substring(0, 30);
        }
        if (slug.isBlank()) {
            return id;
        }
        if (slug.matches("^\\d+$")) {
            return slug + "-mod~" + id;
        }
        return slug + "~" + id;
    }

    private static String encodePathSegment(String raw) {
        return URLEncoder.encode(value(raw, "creator"), StandardCharsets.UTF_8).replace("+", "%20");
    }
}
