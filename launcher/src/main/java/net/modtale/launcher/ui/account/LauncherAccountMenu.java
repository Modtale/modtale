package net.modtale.launcher.ui.account;

import static net.modtale.launcher.ui.common.LauncherUi.pseudo;

import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.event.EventTarget;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import net.modtale.launcher.model.user.CurrentUser;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.common.LauncherOverlaySupport;
import net.modtale.launcher.ui.common.LauncherView;

public final class LauncherAccountMenu {

    private static final double PROFILE_MENU_ESTIMATED_WIDTH = 256;
    private static final double PROFILE_MENU_SCREEN_MARGIN = 8;
    private static final double PROFILE_MENU_TOP_OFFSET = 8;
    private static final double PROFILE_AVATAR_SIZE = 38;

    private final LauncherAccountController accountController;
    private final CachedImageLoader accountImageLoader;
    private final Supplier<StackPane> sceneLayer;
    private final Consumer<LauncherView> showView;
    private final Supplier<LauncherView> currentView;
    private final Runnable showFollowing;
    private final Runnable beforeShow;

    private Button menuButton;
    private VBox dropdownPanel;
    private Label eyebrowLabel;
    private Label accountNameLabel;
    private VBox actionGroup;
    private Region separator;
    private VBox dangerGroup;

    public LauncherAccountMenu(
            LauncherAccountController accountController,
            CachedImageLoader accountImageLoader,
            Supplier<StackPane> sceneLayer,
            Consumer<LauncherView> showView,
            Supplier<LauncherView> currentView,
            Runnable showFollowing,
            Runnable beforeShow
    ) {
        this.accountController = accountController;
        this.accountImageLoader = accountImageLoader;
        this.sceneLayer = sceneLayer;
        this.showView = showView;
        this.currentView = currentView;
        this.showFollowing = showFollowing;
        this.beforeShow = beforeShow;
    }

    public Button button() {
        if (menuButton == null) {
            menuButton = buildButton();
        }
        return menuButton;
    }

    public VBox panel() {
        button();
        return dropdownPanel;
    }

    public void hide() {
        if (dropdownPanel != null) {
            dropdownPanel.setVisible(false);
        }
        updateSelected();
    }

    public void updateSelected() {
        if (menuButton == null) {
            return;
        }
        LauncherView view = currentView.get();
        boolean utilityView = view == LauncherView.UPDATES
                || view == LauncherView.NOTIFICATIONS
                || view == LauncherView.SETTINGS;
        boolean menuShowing = dropdownPanel != null && dropdownPanel.isVisible();
        pseudo(menuButton, "selected", menuShowing || utilityView);
    }

    public void hideOnOutsidePress(EventTarget target) {
        if (dropdownPanel == null || !dropdownPanel.isVisible()) {
            return;
        }
        if (!LauncherOverlaySupport.eventTargetInside(target, dropdownPanel)
                && !LauncherOverlaySupport.eventTargetInside(target, menuButton)) {
            hide();
        }
    }

    private Button buildButton() {
        Label avatarInitial = new Label(initialFor(accountController.displayName()));
        ImageView avatarImage = new ImageView();
        avatarImage.getStyleClass().add("avatar-image");
        avatarImage.setFitWidth(PROFILE_AVATAR_SIZE);
        avatarImage.setFitHeight(PROFILE_AVATAR_SIZE);
        avatarImage.setSmooth(true);
        avatarImage.setMouseTransparent(true);
        avatarImage.setClip(roundAvatarClip(PROFILE_AVATAR_SIZE));
        avatarImage.setVisible(false);
        avatarImage.imageProperty().addListener((observable, oldImage, newImage) ->
                avatarImage.setVisible(newImage != null));

        StackPane avatar = new StackPane(avatarInitial, avatarImage);
        avatar.getStyleClass().add("avatar");

        Button button = new Button(null, avatar);
        button.getStyleClass().add("profile-menu-button");
        button.setMnemonicParsing(false);

        dropdownPanel = buildPanel();
        accountController.addCurrentUserListener(user -> {
            String displayName = accountController.displayName();
            avatarInitial.setText(initialFor(displayName));
            if (accountNameLabel != null) {
                accountNameLabel.setText(displayName);
            }
            refreshMenuActions(user);
            refreshAccountAvatar(user, avatarImage);
        });
        button.setOnAction(event -> toggle());
        return button;
    }

    private VBox buildPanel() {
        eyebrowLabel = new Label("MODTALE ACCOUNT");
        eyebrowLabel.getStyleClass().add("profile-menu-eyebrow");
        accountNameLabel = new Label(accountController.displayName());
        accountNameLabel.getStyleClass().add("profile-menu-name");
        VBox summary = new VBox(4, eyebrowLabel, accountNameLabel);
        summary.getStyleClass().add("profile-menu-summary");
        summary.setMaxWidth(Double.MAX_VALUE);
        VBox.setMargin(summary, new Insets(0, 8, 8, 8));

        VBox menuPanel = new VBox();
        menuPanel.getStyleClass().add("profile-dropdown-panel");
        menuPanel.setMinWidth(PROFILE_MENU_ESTIMATED_WIDTH);
        menuPanel.setPrefWidth(PROFILE_MENU_ESTIMATED_WIDTH);
        menuPanel.setMaxWidth(PROFILE_MENU_ESTIMATED_WIDTH);
        menuPanel.setVisible(false);
        menuPanel.setManaged(false);

        actionGroup = new VBox(2);
        actionGroup.getStyleClass().add("profile-dropdown-action-group");
        VBox.setMargin(actionGroup, new Insets(0, 8, 0, 8));

        separator = new Region();
        separator.getStyleClass().add("profile-dropdown-separator");
        VBox.setMargin(separator, new Insets(8, 16, 8, 16));

        dangerGroup = new VBox();
        dangerGroup.getStyleClass().add("profile-dropdown-action-group");
        VBox.setMargin(dangerGroup, new Insets(0, 8, 0, 8));

        menuPanel.getChildren().addAll(summary, actionGroup, separator, dangerGroup);
        refreshMenuActions(accountController.currentUser());
        return menuPanel;
    }

    private void refreshMenuActions(CurrentUser user) {
        if (eyebrowLabel != null) {
            eyebrowLabel.setText("MODTALE ACCOUNT");
        }
        if (accountNameLabel != null) {
            accountNameLabel.setText(accountController.displayName());
        }
        if (actionGroup == null || dangerGroup == null || separator == null) {
            return;
        }
        boolean signedIn = user != null;
        actionGroup.getChildren().setAll(
                dropdownItem("Library", LauncherIcons.Glyph.LAYERS, () -> showView.accept(LauncherView.LIBRARY), false),
                signedIn
                        ? dropdownItem("Updates", LauncherIcons.Glyph.DOWNLOAD, () -> showView.accept(LauncherView.UPDATES), false)
                        : dropdownItem("Sign In", LauncherIcons.Glyph.USER, accountController::signIn, false),
                signedIn
                        ? dropdownItem("Notifications", LauncherIcons.Glyph.BELL, () -> showView.accept(LauncherView.NOTIFICATIONS), false)
                        : dropdownItem("Settings", LauncherIcons.Glyph.SLIDERS, () -> showView.accept(LauncherView.SETTINGS), false)
        );
        if (signedIn) {
            actionGroup.getChildren().add(dropdownItem("Following", LauncherIcons.Glyph.USER, showFollowing, false));
            actionGroup.getChildren().add(dropdownItem("Settings", LauncherIcons.Glyph.SLIDERS, () -> showView.accept(LauncherView.SETTINGS), false));
            dangerGroup.getChildren().setAll(dropdownItem(
                    "Sign Out",
                    LauncherIcons.Glyph.LOG_OUT,
                    accountController::signOut,
                    true
            ));
        } else {
            dangerGroup.getChildren().clear();
        }
        separator.setVisible(signedIn);
        separator.setManaged(signedIn);
        dangerGroup.setVisible(signedIn);
        dangerGroup.setManaged(signedIn);
    }

    private Button dropdownItem(
            String label,
            LauncherIcons.Glyph icon,
            Runnable action,
            boolean danger
    ) {
        Button item = new Button(label);
        item.getStyleClass().add("profile-dropdown-item");
        if (danger) {
            item.getStyleClass().add("danger");
        }
        item.setGraphic(LauncherIcons.icon(icon, 16));
        item.setAlignment(Pos.CENTER_LEFT);
        item.setMaxWidth(Double.MAX_VALUE);
        item.setOnAction(event -> {
            hide();
            if (action != null) {
                action.run();
            }
        });
        return item;
    }

    private void toggle() {
        if (dropdownPanel.isVisible()) {
            hide();
            return;
        }
        show();
    }

    private void show() {
        StackPane layer = sceneLayer.get();
        if (layer == null || dropdownPanel == null || menuButton == null) {
            return;
        }
        if (beforeShow != null) {
            beforeShow.run();
        }
        dropdownPanel.applyCss();
        dropdownPanel.autosize();
        position();
        dropdownPanel.setVisible(true);
        dropdownPanel.toFront();
        updateSelected();
        Platform.runLater(this::position);
    }

    private void position() {
        StackPane layer = sceneLayer.get();
        if (layer == null || dropdownPanel == null || menuButton == null) {
            return;
        }
        Bounds anchorBounds = menuButton.localToScene(menuButton.getBoundsInLocal());
        if (anchorBounds == null) {
            return;
        }
        double centerX = anchorBounds.getMinX() + (anchorBounds.getWidth() / 2.0);
        double centerY = anchorBounds.getMinY() + (anchorBounds.getHeight() / 2.0);
        Point2D avatarBottomRight = layer.sceneToLocal(
                centerX + (PROFILE_AVATAR_SIZE / 2.0),
                centerY + (PROFILE_AVATAR_SIZE / 2.0)
        );
        double width = dropdownPanel.getLayoutBounds().getWidth() > 0
                ? dropdownPanel.getLayoutBounds().getWidth()
                : PROFILE_MENU_ESTIMATED_WIDTH;
        double maxX = Math.max(PROFILE_MENU_SCREEN_MARGIN,
                layer.getWidth() - width - PROFILE_MENU_SCREEN_MARGIN);
        double x = LauncherOverlaySupport.clamp(
                avatarBottomRight.getX() - width,
                PROFILE_MENU_SCREEN_MARGIN,
                maxX
        );
        double y = avatarBottomRight.getY() + PROFILE_MENU_TOP_OFFSET;
        dropdownPanel.relocate(x, y);
    }

    private void refreshAccountAvatar(CurrentUser user, ImageView avatarImage) {
        String avatarUrl = user == null ? null : user.avatarUrl();
        if (avatarUrl == null || avatarUrl.isBlank()) {
            accountImageLoader.clear(avatarImage);
            return;
        }
        double requestedSize = requestedAvatarImageSize();
        accountImageLoader.loadInto(avatarImage, avatarUrl, requestedSize, requestedSize);
    }

    private static Rectangle roundAvatarClip(double size) {
        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(size);
        clip.setArcHeight(size);
        return clip;
    }

    private static double requestedAvatarImageSize() {
        double scale = Screen.getScreens().stream()
                .mapToDouble(screen -> Math.max(screen.getOutputScaleX(), screen.getOutputScaleY()))
                .max()
                .orElse(1);
        return Math.ceil(PROFILE_AVATAR_SIZE * Math.max(1, Math.min(3, scale)));
    }

    private static String initialFor(String name) {
        if (name == null || name.isBlank() || "Signed out".equals(name)) {
            return "M";
        }
        return name.trim().substring(0, 1).toUpperCase();
    }
}
