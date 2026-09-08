package net.modtale.launcher.ui.activity;

import static net.modtale.launcher.ui.common.LauncherUi.setVisibleManaged;
import static net.modtale.launcher.ui.common.LauncherUi.value;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.model.user.CurrentUser;
import net.modtale.launcher.model.user.UserSummary;
import net.modtale.launcher.ui.account.LauncherAccountController;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherExternalLinks;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.feedback.LauncherFeedback;

public final class LauncherFollowingController {

    private static final double AVATAR_SIZE = 40;

    private final ModtaleApiClient apiClient;
    private final LauncherAccountController accountController;
    private final LauncherFeedback feedback;
    private final CachedImageLoader imageLoader;
    private final StackPane modalLayer = new StackPane();
    private final VBox userList = new VBox(2);
    private final Label summary = new Label("No creators loaded");

    private boolean modalBuilt;
    private boolean loading;
    private List<UserSummary> users = List.of();

    public LauncherFollowingController(
            ModtaleApiClient apiClient,
            LauncherAccountController accountController,
            LauncherFeedback feedback,
            CachedImageLoader imageLoader
    ) {
        this.apiClient = apiClient;
        this.accountController = accountController;
        this.feedback = feedback;
        this.imageLoader = imageLoader;
        accountController.addCurrentUserListener(user -> {
            if (user == null) {
                users = List.of();
                loading = false;
                hideModal();
                renderUsers();
            }
        });
    }

    public Node modal() {
        if (!modalBuilt) {
            buildModal();
        }
        return modalLayer;
    }

    public void showModal() {
        modal();
        setVisibleManaged(modalLayer, true);
        modalLayer.toFront();
        refresh();
    }

    public void hideModal() {
        setVisibleManaged(modalLayer, false);
    }

    public void refresh() {
        CurrentUser user = accountController.currentUser();
        if (user == null || user.id() == null || user.id().isBlank()) {
            loading = false;
            users = List.of();
            renderUsers();
            return;
        }
        loading = true;
        renderUsers();
        feedback.runAsync("Loading following...",
                () -> apiClient.getFollowing(user.id()),
                loaded -> {
                    loading = false;
                    users = List.copyOf(loaded);
                    renderUsers();
                    feedback.log("Loaded " + users.size() + " followed creators.");
                },
                error -> {
                    loading = false;
                    renderUsers();
                });
    }

    private void buildModal() {
        modalBuilt = true;
        modalLayer.getStyleClass().add("following-modal-layer");
        setVisibleManaged(modalLayer, false);

        Region scrim = new Region();
        scrim.getStyleClass().add("following-modal-scrim");
        scrim.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        scrim.setOnMouseClicked(event -> hideModal());

        Label title = new Label("Following");
        title.getStyleClass().add("following-modal-title");
        title.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.USER, 18));
        title.setGraphicTextGap(8);

        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);

        Button close = new Button(null, LauncherIcons.icon(LauncherIcons.Glyph.X, 16));
        close.getStyleClass().addAll("icon-btn", "following-modal-close");
        close.setMnemonicParsing(false);
        close.setAccessibleText("Close following");
        close.setTooltip(new Tooltip("Close"));
        close.setOnAction(event -> hideModal());

        HBox header = new HBox(10, title, headerSpacer, close);
        header.getStyleClass().add("following-modal-header");
        header.setAlignment(Pos.CENTER_LEFT);

        summary.getStyleClass().add("following-modal-summary");
        userList.getStyleClass().add("following-modal-list");

        ScrollPane scrollPane = new ScrollPane(userList);
        scrollPane.getStyleClass().add("following-modal-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setMaxHeight(460);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox card = new VBox(0, header, summary, scrollPane);
        card.getStyleClass().add("following-modal-card");
        card.setMaxWidth(460);
        card.setMaxHeight(620);
        StackPane.setAlignment(card, Pos.CENTER);
        StackPane.setMargin(card, new Insets(24));

        modalLayer.getChildren().setAll(scrim, card);
        renderUsers();
    }

    private void renderUsers() {
        if (!modalBuilt) {
            return;
        }
        userList.getChildren().clear();
        if (loading) {
            setVisibleManaged(summary, true);
            summary.setText("Loading followed creators...");
            Label loadingLabel = new Label("Loading...");
            loadingLabel.getStyleClass().add("following-loading");
            StackPane loadingPane = new StackPane(loadingLabel);
            loadingPane.getStyleClass().add("following-loading-pane");
            userList.getChildren().add(loadingPane);
            return;
        }
        if (users.isEmpty()) {
            setVisibleManaged(summary, false);
            userList.getChildren().add(followingEmptyState());
            return;
        }
        setVisibleManaged(summary, true);
        summary.setText(users.size() + " followed creator" + (users.size() == 1 ? "" : "s"));
        for (UserSummary user : users) {
            userList.getChildren().add(userRow(user));
        }
    }

    private VBox followingEmptyState() {
        VBox empty = new VBox(8);
        empty.getStyleClass().add("following-empty-state");
        empty.setAlignment(Pos.CENTER);

        Label title = new Label("Not following anyone");
        title.getStyleClass().add("following-empty-title");
        Label subtitle = new Label("Follow creators on Modtale to see them here.");
        subtitle.getStyleClass().add("following-empty-subtitle");

        empty.getChildren().addAll(title, subtitle);
        return empty;
    }

    private Node userRow(UserSummary user) {
        HBox row = new HBox(12);
        row.getStyleClass().add("following-row");
        row.setAlignment(Pos.CENTER_LEFT);

        StackPane avatar = avatar(user);
        Label name = new Label(value(user.username(), "Unknown creator"));
        name.getStyleClass().add("following-name");
        VBox copy = new VBox(3, name);
        String role = primaryRole(user.roles());
        if (!role.isBlank()) {
            Label roleLabel = new Label(role);
            roleLabel.getStyleClass().add("following-role");
            copy.getChildren().add(roleLabel);
        }
        HBox.setHgrow(copy, Priority.ALWAYS);

        Button open = new Button(null, LauncherIcons.icon(LauncherIcons.Glyph.EXTERNAL_LINK, 15));
        open.getStyleClass().addAll("icon-btn", "following-open-button");
        open.setAccessibleText("Open creator profile");
        open.setTooltip(new Tooltip("Open creator profile"));
        open.addEventHandler(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        open.setOnAction(event -> {
            hideModal();
            LauncherExternalLinks.open(creatorPath(user), feedback::showToast);
        });

        row.setOnMouseClicked(event -> {
            hideModal();
            LauncherExternalLinks.open(creatorPath(user), feedback::showToast);
        });
        row.getChildren().addAll(avatar, copy, open);
        return row;
    }

    private StackPane avatar(UserSummary user) {
        Label initial = new Label(initialFor(user.username()));
        ImageView image = new ImageView();
        image.getStyleClass().add("following-avatar-image");
        image.setFitWidth(AVATAR_SIZE);
        image.setFitHeight(AVATAR_SIZE);
        image.setSmooth(true);
        Rectangle clip = new Rectangle(AVATAR_SIZE, AVATAR_SIZE);
        clip.setArcWidth(AVATAR_SIZE);
        clip.setArcHeight(AVATAR_SIZE);
        image.setClip(clip);
        imageLoader.loadInto(image, user.avatarUrl(), AVATAR_SIZE, AVATAR_SIZE);
        StackPane avatar = new StackPane(initial, image);
        avatar.getStyleClass().add("following-avatar");
        return avatar;
    }

    private static String creatorPath(UserSummary user) {
        String handle = user.username() == null || user.username().isBlank()
                ? user.id()
                : user.username();
        return "/creator/" + encodePath(handle);
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String primaryRole(List<String> roles) {
        if (roles == null || roles.isEmpty() || "USER".equals(roles.getFirst())) {
            return "";
        }
        return roles.getFirst();
    }

    private static String initialFor(String name) {
        if (name == null || name.isBlank()) {
            return "M";
        }
        return name.trim().substring(0, 1).toUpperCase();
    }
}
