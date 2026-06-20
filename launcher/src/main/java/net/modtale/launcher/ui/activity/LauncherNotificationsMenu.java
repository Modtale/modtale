package net.modtale.launcher.ui.activity;

import static net.modtale.launcher.ui.common.LauncherUi.dangerButton;
import static net.modtale.launcher.ui.common.LauncherUi.emptyState;
import static net.modtale.launcher.ui.common.LauncherUi.primaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.pseudo;
import static net.modtale.launcher.ui.common.LauncherUi.value;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.event.EventTarget;
import javafx.geometry.Bounds;
import javafx.geometry.Point2D;
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
import net.modtale.launcher.model.notification.LauncherNotification;
import net.modtale.launcher.ui.account.LauncherAccountController;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherExternalLinks;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.common.LauncherOverlaySupport;
import net.modtale.launcher.ui.feedback.LauncherFeedback;

public final class LauncherNotificationsMenu {

    private static final DateTimeFormatter NOTIFICATION_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final double NOTIFICATION_ICON_SIZE = 40;
    private static final double MENU_ESTIMATED_WIDTH = 384;
    private static final double MENU_TOP_OFFSET = 8;
    private static final double SCREEN_MARGIN = 8;

    private final ModtaleApiClient apiClient;
    private final LauncherAccountController accountController;
    private final LauncherFeedback feedback;
    private final CachedImageLoader imageLoader;
    private final VBox notificationList = new VBox(0);

    private Supplier<StackPane> sceneLayer = () -> null;
    private Runnable beforeShow;

    private Button menuButton;
    private Button clearButton;
    private VBox dropdownPanel;
    private Region unreadDot;
    private List<LauncherNotification> notifications = List.of();
    private boolean loading;

    public LauncherNotificationsMenu(
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
                notifications = List.of();
                loading = false;
                hide();
                renderNotifications();
            }
        });
    }

    public void attachOverlay(Supplier<StackPane> sceneLayer, Runnable beforeShow) {
        this.sceneLayer = sceneLayer == null ? () -> null : sceneLayer;
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
        if (menuButton != null) {
            pseudo(menuButton, "selected", false);
        }
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
        unreadDot = new Region();
        unreadDot.getStyleClass().add("notification-menu-unread-dot");
        unreadDot.setVisible(false);
        unreadDot.setManaged(false);

        StackPane graphic = new StackPane(LauncherIcons.icon(LauncherIcons.Glyph.BELL, 18), unreadDot);
        graphic.getStyleClass().add("notification-menu-button-graphic");
        StackPane.setAlignment(unreadDot, Pos.TOP_RIGHT);

        Button button = new Button(null, graphic);
        button.getStyleClass().add("notification-menu-button");
        button.setMnemonicParsing(false);
        button.setAccessibleText("Notifications");
        button.setTooltip(new Tooltip("Notifications"));
        button.setOnAction(event -> toggle());

        dropdownPanel = buildPanel();
        renderNotifications();
        return button;
    }

    private VBox buildPanel() {
        Label title = new Label("Notifications");
        title.getStyleClass().add("notification-menu-title");
        HBox.setHgrow(title, Priority.ALWAYS);

        Button refresh = new Button(null, LauncherIcons.icon(LauncherIcons.Glyph.REFRESH_CW, 13));
        refresh.getStyleClass().addAll("icon-btn", "notification-menu-header-button");
        refresh.setMnemonicParsing(false);
        refresh.setAccessibleText("Refresh notifications");
        refresh.setTooltip(new Tooltip("Refresh"));
        refresh.setOnAction(event -> refresh(false));

        clearButton = new Button("Clear All", LauncherIcons.icon(LauncherIcons.Glyph.TRASH, 12));
        clearButton.getStyleClass().add("notification-menu-clear");
        clearButton.setOnAction(event -> clearAll());

        HBox header = new HBox(8, title, refresh, clearButton);
        header.getStyleClass().add("notification-menu-header");
        header.setAlignment(Pos.CENTER_LEFT);

        notificationList.getStyleClass().add("notification-menu-list");
        ScrollPane scroll = new ScrollPane(notificationList);
        scroll.getStyleClass().add("notification-menu-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setMaxHeight(520);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox menuPanel = new VBox(0, header, scroll);
        menuPanel.getStyleClass().add("notification-dropdown-panel");
        menuPanel.setMinWidth(MENU_ESTIMATED_WIDTH);
        menuPanel.setPrefWidth(MENU_ESTIMATED_WIDTH);
        menuPanel.setMaxWidth(MENU_ESTIMATED_WIDTH);
        menuPanel.setVisible(false);
        menuPanel.setManaged(false);
        return menuPanel;
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
        pseudo(menuButton, "selected", true);
        refresh(true);
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
        Point2D anchorBottomRight = layer.sceneToLocal(anchorBounds.getMaxX(), anchorBounds.getMaxY());
        double width = dropdownPanel.getLayoutBounds().getWidth() > 0
                ? dropdownPanel.getLayoutBounds().getWidth()
                : MENU_ESTIMATED_WIDTH;
        double maxX = Math.max(SCREEN_MARGIN, layer.getWidth() - width - SCREEN_MARGIN);
        double x = LauncherOverlaySupport.clamp(anchorBottomRight.getX() - width, SCREEN_MARGIN, maxX);
        double y = anchorBottomRight.getY() + MENU_TOP_OFFSET;
        dropdownPanel.relocate(x, y);
    }

    private void refresh(boolean markReadOnLoad) {
        if (accountController.currentUser() == null) {
            notifications = List.of();
            loading = false;
            renderNotifications();
            return;
        }
        loading = true;
        renderNotifications();
        feedback.runAsync("Loading notifications...",
                apiClient::getNotifications,
                loaded -> {
                    loading = false;
                    notifications = List.copyOf(loaded);
                    renderNotifications();
                    if (markReadOnLoad && unreadCount() > 0) {
                        markAllReadFromMenu();
                    }
                },
                error -> {
                    loading = false;
                    renderNotifications();
                });
    }

    private void clearAll() {
        if (clearButton != null) {
            clearButton.setDisable(true);
        }
        feedback.runAsync("Clearing notifications...", () -> {
            apiClient.clearNotifications();
            return List.<LauncherNotification>of();
        }, ignored -> {
            if (clearButton != null) {
                clearButton.setDisable(false);
            }
            notifications = List.of();
            renderNotifications();
            feedback.showToast("Notifications cleared", "Your notification list is empty.");
        }, error -> {
            if (clearButton != null) {
                clearButton.setDisable(false);
            }
        });
    }

    private void dismiss(LauncherNotification notification) {
        feedback.runAsync("Dismissing notification...", () -> {
            apiClient.deleteNotification(notification.id());
            return notification.id();
        }, id -> {
            notifications = notifications.stream()
                    .filter(item -> !id.equals(item.id()))
                    .toList();
            renderNotifications();
        });
    }

    private void toggleRead(LauncherNotification notification) {
        boolean nextRead = !notification.read();
        feedback.runAsync(nextRead ? "Marking notification as read..." : "Marking notification as unread...", () -> {
            apiClient.markNotificationRead(notification.id(), nextRead);
            return apiClient.getNotifications();
        }, loaded -> {
            notifications = List.copyOf(loaded);
            renderNotifications();
        });
    }

    private void markAllReadFromMenu() {
        feedback.runAsync("Marking notifications as read...", () -> {
            apiClient.markAllNotificationsRead();
            return apiClient.getNotifications();
        }, loaded -> {
            notifications = List.copyOf(loaded);
            renderNotifications();
        });
    }

    private void resolveAction(LauncherNotification notification, boolean accept) {
        feedback.runAsync((accept ? "Accepting" : "Declining") + " notification request...", () -> {
            apiClient.resolveNotificationAction(notification, accept);
            apiClient.deleteNotification(notification.id());
            return apiClient.getNotifications();
        }, loaded -> {
            notifications = List.copyOf(loaded);
            renderNotifications();
            feedback.showToast(accept ? "Accepted" : "Declined", "Notification request updated.");
        });
    }

    private void renderNotifications() {
        if (notificationList == null) {
            return;
        }
        notificationList.getChildren().clear();
        int unread = unreadCount();
        if (unreadDot != null) {
            unreadDot.setVisible(unread > 0);
            unreadDot.setManaged(unread > 0);
        }
        if (clearButton != null) {
            clearButton.setVisible(!notifications.isEmpty());
            clearButton.setManaged(!notifications.isEmpty());
        }
        if (loading) {
            Label loadingLabel = new Label("Loading...");
            loadingLabel.getStyleClass().add("notification-menu-loading");
            StackPane loadingPane = new StackPane(loadingLabel);
            loadingPane.getStyleClass().add("notification-menu-loading-pane");
            notificationList.getChildren().add(loadingPane);
            return;
        }
        if (notifications.isEmpty()) {
            VBox empty = emptyState("No notifications", "New activity and requests will appear here.");
            empty.getStyleClass().add("notification-menu-empty");
            notificationList.getChildren().add(empty);
            return;
        }
        for (LauncherNotification notification : notifications) {
            notificationList.getChildren().add(notificationRow(notification));
        }
    }

    private Node notificationRow(LauncherNotification notification) {
        HBox row = new HBox(12);
        row.getStyleClass().addAll("notification-row", "notification-menu-row");
        row.setAlignment(Pos.TOP_LEFT);
        pseudo(row, "unread", !notification.read());
        row.setOnMouseClicked(event -> {
            hide();
            LauncherExternalLinks.open(notification.link(), feedback::showToast);
        });

        StackPane icon = notificationIcon(notification.iconUrl());
        VBox copy = new VBox(4);
        HBox titleLine = new HBox(6);
        titleLine.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(value(notification.title(), "Notification"));
        title.getStyleClass().add("notification-title");
        title.setMaxWidth(Double.MAX_VALUE);
        titleLine.getChildren().add(title);
        if (!notification.read()) {
            Region rowUnreadDot = new Region();
            rowUnreadDot.getStyleClass().add("notification-unread-dot");
            titleLine.getChildren().add(rowUnreadDot);
        }

        Label message = new Label(value(notification.message(), ""));
        message.getStyleClass().add("notification-message");
        message.setWrapText(true);
        Label date = new Label(formatDate(notification.createdAt()));
        date.getStyleClass().add("notification-date");
        copy.getChildren().addAll(titleLine, message);
        if (notification.actionable()) {
            HBox decisions = new HBox(8);
            decisions.getStyleClass().add("notification-decisions");
            Button accept = primaryButton("Accept");
            accept.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.CHECK, 13));
            accept.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
            accept.setOnAction(event -> resolveAction(notification, true));
            Button decline = dangerButton("Decline");
            decline.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.X, 13));
            decline.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
            decline.setOnAction(event -> resolveAction(notification, false));
            decisions.getChildren().addAll(accept, decline);
            copy.getChildren().add(decisions);
        } else {
            copy.getChildren().add(date);
        }
        HBox.setHgrow(copy, Priority.ALWAYS);

        VBox actions = new VBox(6);
        actions.getStyleClass().add("notification-row-actions");
        actions.setAlignment(Pos.TOP_RIGHT);
        Button dismiss = iconAction(LauncherIcons.Glyph.X, "Dismiss");
        dismiss.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        dismiss.setOnAction(event -> dismiss(notification));
        Button read = iconAction(LauncherIcons.Glyph.CIRCLE, notification.read() ? "Mark unread" : "Mark read");
        read.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        pseudo(read, "selected", !notification.read());
        read.setOnAction(event -> toggleRead(notification));
        actions.getChildren().addAll(dismiss, read);

        row.getChildren().addAll(icon, copy, actions);
        return row;
    }

    private StackPane notificationIcon(String iconUrl) {
        ImageView image = new ImageView();
        image.getStyleClass().add("notification-image");
        image.setFitWidth(NOTIFICATION_ICON_SIZE);
        image.setFitHeight(NOTIFICATION_ICON_SIZE);
        image.setSmooth(true);
        Rectangle clip = new Rectangle(NOTIFICATION_ICON_SIZE, NOTIFICATION_ICON_SIZE);
        clip.setArcWidth(8);
        clip.setArcHeight(8);
        image.setClip(clip);
        imageLoader.loadInto(image, iconUrl, NOTIFICATION_ICON_SIZE, NOTIFICATION_ICON_SIZE);
        StackPane icon = new StackPane(LauncherIcons.icon(LauncherIcons.Glyph.BELL, 18), image);
        icon.getStyleClass().add("notification-image-shell");
        return icon;
    }

    private Button iconAction(LauncherIcons.Glyph glyph, String tooltipText) {
        Button button = new Button(null, LauncherIcons.icon(glyph, 14));
        button.getStyleClass().addAll("icon-btn", "notification-icon-button");
        button.setMnemonicParsing(false);
        button.setAccessibleText(tooltipText);
        button.setTooltip(new Tooltip(tooltipText));
        return button;
    }

    private int unreadCount() {
        return (int) notifications.stream().filter(item -> !item.read()).count();
    }

    private static String formatDate(LocalDateTime createdAt) {
        return createdAt == null ? "" : NOTIFICATION_DATE.format(createdAt);
    }
}
