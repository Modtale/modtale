package net.modtale.launcher.ui.activity;

import static net.modtale.launcher.ui.common.LauncherUi.dangerButton;
import static net.modtale.launcher.ui.common.LauncherUi.primaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.pseudo;
import static net.modtale.launcher.ui.common.LauncherUi.secondaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.value;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
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
import net.modtale.launcher.model.user.CurrentUser;
import net.modtale.launcher.ui.account.LauncherAccountController;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherExternalLinks;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.common.LauncherView;
import net.modtale.launcher.ui.feedback.LauncherFeedback;

public final class LauncherNotificationsController {

    private static final DateTimeFormatter NOTIFICATION_DATE = DateTimeFormatter.ofPattern("MMM d, yyyy");
    private static final double NOTIFICATION_ICON_SIZE = 40;

    private final ModtaleApiClient apiClient;
    private final LauncherAccountController accountController;
    private final LauncherFeedback feedback;
    private final CachedImageLoader imageLoader;
    private final VBox notificationList = new VBox(0);
    private final Label notificationSummary = new Label("No notifications loaded");
    private final ToggleControl projectUpdates = new ToggleControl(
            "Favorite Project Updates",
            "Notify me when projects I've favorited release new versions."
    );
    private final ToggleControl creatorUploads = new ToggleControl(
            "New Creator Uploads",
            "Notify me when creators I follow upload new projects."
    );
    private final ToggleControl newComments = new ToggleControl(
            "New Comments",
            "Get notified when someone comments on your project."
    );
    private final ToggleControl newFollowers = new ToggleControl(
            "New Followers",
            "Notify me when someone starts following me."
    );
    private final ToggleControl dependencyUpdates = new ToggleControl(
            "Dependency Updates",
            "Alert me when a project I depend on releases a new version."
    );

    private Node view;
    private List<LauncherNotification> notifications = List.of();

    public LauncherNotificationsController(
            ModtaleApiClient apiClient,
            LauncherAccountController accountController,
            LauncherFeedback feedback,
            CachedImageLoader imageLoader
    ) {
        this.apiClient = apiClient;
        this.accountController = accountController;
        this.feedback = feedback;
        this.imageLoader = imageLoader;
        accountController.addCurrentUserListener(this::applyPreferences);
    }

    public Node view() {
        if (view == null) {
            view = buildView();
            applyPreferences(accountController.currentUser());
            renderNotifications();
        }
        return view;
    }

    public void refresh() {
        applyPreferences(accountController.currentUser());
        loadNotifications();
    }

    private Node buildView() {
        VBox root = new VBox(24);
        root.setUserData(LauncherView.NOTIFICATIONS);
        root.getStyleClass().addAll("view", "account-view");

        VBox preferences = new VBox(0);
        preferences.getStyleClass().add("account-card");
        preferences.getChildren().addAll(
                projectUpdates.node(),
                creatorUploads.node(),
                newComments.node(),
                newFollowers.node(),
                dependencyUpdates.node()
        );

        Button save = primaryButton("Save Changes");
        save.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.SAVE, 14));
        save.setOnAction(event -> savePreferences(save));
        HBox saveRow = new HBox(save);
        saveRow.setAlignment(Pos.CENTER_RIGHT);

        VBox recent = new VBox(0);
        recent.getStyleClass().add("account-card");
        Button refresh = iconAction(LauncherIcons.Glyph.REFRESH_CW, "Refresh");
        refresh.setOnAction(event -> loadNotifications());
        Button markAll = secondaryButton("Mark All Read");
        markAll.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.CHECK, 13));
        markAll.setOnAction(event -> markAllRead(markAll));
        Button clear = secondaryButton("Clear All");
        clear.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.TRASH, 13));
        clear.setOnAction(event -> clearAll(clear));
        HBox recentActions = new HBox(8, refresh, markAll, clear);
        recentActions.setAlignment(Pos.CENTER_RIGHT);
        HBox recentHeader = sectionHeader("Notifications", "Recent account activity and requests.", recentActions);
        notificationSummary.getStyleClass().add("account-section-meta");
        VBox.setMargin(notificationSummary, new Insets(0, 18, 12, 18));
        recent.getChildren().addAll(recentHeader, notificationSummary, notificationList);

        root.getChildren().addAll(preferences, saveRow, recent);
        return root;
    }

    private HBox sectionHeader(String title, String subtitle, Node actions) {
        Label heading = new Label(title);
        heading.getStyleClass().add("account-section-title");
        Label sub = new Label(subtitle);
        sub.getStyleClass().add("account-section-subtitle");
        VBox copy = new VBox(3, heading, sub);
        HBox header = new HBox(12, copy);
        header.getStyleClass().add("account-section-header");
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(copy, Priority.ALWAYS);
        if (actions != null) {
            header.getChildren().add(actions);
        }
        return header;
    }

    private void loadNotifications() {
        feedback.runAsync("Loading notifications...",
                apiClient::getNotifications,
                loaded -> {
                    notifications = List.copyOf(loaded);
                    renderNotifications();
                    feedback.log("Loaded " + notifications.size() + " notifications.");
                });
    }

    private void savePreferences(Button save) {
        save.setDisable(true);
        CurrentUser.NotificationPreferences preferences = new CurrentUser.NotificationPreferences(
                projectUpdates.value(),
                creatorUploads.value(),
                newComments.value(),
                newFollowers.value(),
                dependencyUpdates.value()
        );
        feedback.runAsync("Saving notification preferences...", () -> {
            apiClient.updateNotificationPreferences(preferences);
            return apiClient.currentUser();
        }, user -> {
            save.setDisable(false);
            accountController.setCurrentUser(user);
            feedback.showToast("Saved", "Notification preferences were updated.");
        }, error -> save.setDisable(false));
    }

    private void clearAll(Button clear) {
        clear.setDisable(true);
        feedback.runAsync("Clearing notifications...", () -> {
            apiClient.clearNotifications();
            return List.<LauncherNotification>of();
        }, ignored -> {
            clear.setDisable(false);
            notifications = List.of();
            renderNotifications();
            feedback.showToast("Notifications cleared", "Your notification list is empty.");
        }, error -> clear.setDisable(false));
    }

    private void markAllRead(Button markAll) {
        markAll.setDisable(true);
        feedback.runAsync("Marking notifications as read...", () -> {
            apiClient.markAllNotificationsRead();
            return apiClient.getNotifications();
        }, loaded -> {
            markAll.setDisable(false);
            notifications = List.copyOf(loaded);
            renderNotifications();
        }, error -> markAll.setDisable(false));
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
        notificationList.getChildren().clear();
        int unread = (int) notifications.stream().filter(item -> !item.read()).count();
        if (notifications.isEmpty()) {
            notificationSummary.setText("");
            notificationSummary.setVisible(false);
            notificationSummary.setManaged(false);
            return;
        }
        notificationSummary.setText(notifications.size() + " total - " + unread + " unread");
        notificationSummary.setVisible(true);
        notificationSummary.setManaged(true);
        for (LauncherNotification notification : notifications) {
            notificationList.getChildren().add(notificationRow(notification));
        }
    }

    private Node notificationRow(LauncherNotification notification) {
        HBox row = new HBox(12);
        row.getStyleClass().add("notification-row");
        row.setAlignment(Pos.TOP_LEFT);
        pseudo(row, "unread", !notification.read());

        StackPane icon = notificationIcon(notification.iconUrl());
        VBox copy = new VBox(4);
        HBox titleLine = new HBox(6);
        titleLine.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(value(notification.title(), "Notification"));
        title.getStyleClass().add("notification-title");
        title.setMaxWidth(Double.MAX_VALUE);
        titleLine.getChildren().add(title);
        if (!notification.read()) {
            Region unreadDot = new Region();
            unreadDot.getStyleClass().add("notification-unread-dot");
            titleLine.getChildren().add(unreadDot);
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
        Button open = iconAction(LauncherIcons.Glyph.EXTERNAL_LINK, "Open");
        open.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        open.setOnAction(event -> LauncherExternalLinks.open(notification.link(), feedback::showToast));
        Button dismiss = iconAction(LauncherIcons.Glyph.X, "Dismiss");
        dismiss.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        dismiss.setOnAction(event -> dismiss(notification));
        Button read = iconAction(LauncherIcons.Glyph.CIRCLE, notification.read() ? "Mark unread" : "Mark read");
        read.addEventFilter(MouseEvent.MOUSE_CLICKED, MouseEvent::consume);
        pseudo(read, "selected", !notification.read());
        read.setOnAction(event -> toggleRead(notification));
        actions.getChildren().addAll(open, dismiss, read);

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

    private void applyPreferences(CurrentUser user) {
        CurrentUser.NotificationPreferences prefs = user == null
                ? CurrentUser.NotificationPreferences.defaults()
                : user.notificationPreferences();
        projectUpdates.setValue(prefs.projectUpdates());
        creatorUploads.setValue(prefs.creatorUploads());
        newComments.setValue(prefs.newComments());
        newFollowers.setValue(prefs.newFollowers());
        dependencyUpdates.setValue(prefs.dependencyUpdates());
    }

    private static String formatDate(LocalDateTime createdAt) {
        return createdAt == null ? "" : NOTIFICATION_DATE.format(createdAt);
    }

    private static final class ToggleControl {
        private final String label;
        private final String description;
        private final Button off = new Button("Off");
        private final Button on = new Button("On");
        private CurrentUser.NotificationLevel value = CurrentUser.NotificationLevel.ON;
        private Node node;

        private ToggleControl(String label, String description) {
            this.label = label;
            this.description = description;
        }

        Node node() {
            if (node == null) {
                Label title = new Label(label);
                title.getStyleClass().add("notification-toggle-title");
                Label desc = new Label(description);
                desc.getStyleClass().add("notification-toggle-description");
                desc.setWrapText(true);
                VBox copy = new VBox(4, title, desc);
                HBox.setHgrow(copy, Priority.ALWAYS);

                off.getStyleClass().add("notification-toggle-segment");
                on.getStyleClass().add("notification-toggle-segment");
                off.setOnAction(event -> setValue(CurrentUser.NotificationLevel.OFF));
                on.setOnAction(event -> setValue(CurrentUser.NotificationLevel.ON));
                HBox segments = new HBox(0, off, on);
                segments.getStyleClass().add("notification-toggle-control");
                segments.setAlignment(Pos.CENTER_RIGHT);

                HBox row = new HBox(18, copy, segments);
                row.getStyleClass().add("notification-toggle-row");
                row.setAlignment(Pos.CENTER_LEFT);
                node = row;
                refresh();
            }
            return node;
        }

        String value() {
            return value.apiValue();
        }

        void setValue(String nextValue) {
            setValue(CurrentUser.NotificationLevel.fromApiValue(nextValue));
        }

        void setValue(CurrentUser.NotificationLevel nextValue) {
            value = nextValue == null ? CurrentUser.NotificationLevel.ON : nextValue;
            refresh();
        }

        private void refresh() {
            pseudo(off, "selected", value == CurrentUser.NotificationLevel.OFF);
            pseudo(on, "selected", value == CurrentUser.NotificationLevel.ON);
        }
    }
}
