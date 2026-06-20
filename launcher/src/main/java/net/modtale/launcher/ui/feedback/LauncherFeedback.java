package net.modtale.launcher.ui.feedback;

import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.modtale.launcher.ui.common.LauncherIcons;

public final class LauncherFeedback {

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String TOAST_SUCCESS = "toast-success";
    private static final String TOAST_ERROR = "toast-error";
    private static final String TOAST_NEUTRAL = "toast-neutral";

    private final Executor executor;
    private final Label statusText;
    private final VBox logList;
    private final StackPane toast;
    private final Label toastTitle;
    private final Label toastMessage;
    private final Supplier<String> idleStatus;
    private StackPane toastIcon;

    public LauncherFeedback(
            Executor executor,
            Label statusText,
            VBox logList,
            StackPane toast,
            Label toastTitle,
            Label toastMessage,
            Supplier<String> idleStatus
    ) {
        this.executor = executor;
        this.statusText = statusText;
        this.logList = logList;
        this.toast = toast;
        this.toastTitle = toastTitle;
        this.toastMessage = toastMessage;
        this.idleStatus = idleStatus;
    }

    public <T> void runAsync(String status, Supplier<T> work, Consumer<T> onSuccess) {
        runAsync(status, work, onSuccess, ignored -> {
        });
    }

    public <T> void runAsync(String status, Supplier<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
        Platform.runLater(() -> statusText.setText(status));
        log(status);
        CompletableFuture.supplyAsync(work, executor)
                .whenComplete((value, error) -> Platform.runLater(() -> {
                    statusText.setText(idleStatus.get());
                    if (error != null) {
                        Throwable cause = error.getCause() == null ? error : error.getCause();
                        log("Error: " + cause.getMessage());
                        showToast("Action failed", cause.getMessage());
                        onError.accept(cause);
                        return;
                    }
                    onSuccess.accept(value);
                }));
    }

    public void log(String message) {
        Platform.runLater(() -> {
            HBox line = new HBox(10);
            line.getStyleClass().add("log-line");
            Label time = new Label(LOG_TIME.format(java.time.LocalTime.now()));
            time.getStyleClass().add("log-time");
            Label text = new Label(message);
            text.getStyleClass().add("log-text");
            line.getChildren().addAll(time, text);
            logList.getChildren().add(line);
            if (logList.getChildren().size() > 80) {
                logList.getChildren().remove(0);
            }
        });
    }

    public void showToast(String title, String message) {
        toastTitle.setText(title == null ? "Modtale" : title);
        toastMessage.setText(message == null ? "" : message);
        ToastTone tone = toneFor(title);
        if (toast.getChildren().isEmpty()) {
            toastIcon = new StackPane();
            toastIcon.getStyleClass().add("toast-icon");

            VBox copy = new VBox(1, toastTitle, toastMessage);
            copy.getStyleClass().add("toast-copy");
            copy.setMaxWidth(292);
            HBox.setHgrow(copy, Priority.ALWAYS);

            HBox box = new HBox(10, toastIcon, copy);
            box.getStyleClass().add("toast-content");
            box.setAlignment(Pos.TOP_LEFT);
            toastTitle.getStyleClass().add("toast-title");
            toastTitle.setWrapText(true);
            toastTitle.setMaxWidth(292);
            toastMessage.getStyleClass().add("toast-message");
            toastMessage.setWrapText(true);
            toastMessage.setMaxWidth(292);
            toast.getChildren().add(box);
        }
        toast.getStyleClass().removeAll(TOAST_SUCCESS, TOAST_ERROR, TOAST_NEUTRAL);
        toast.getStyleClass().add(tone.styleClass);
        if (toastIcon != null) {
            Node icon = LauncherIcons.icon(tone.glyph, 14);
            toastIcon.getChildren().setAll(icon);
        }
        toast.setVisible(true);
        toast.setManaged(true);
        CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS).execute(() ->
                Platform.runLater(() -> {
                    toast.setVisible(false);
                    toast.setManaged(false);
                }));
    }

    private static ToastTone toneFor(String title) {
        String normalized = title == null ? "" : title.toLowerCase(Locale.ROOT);
        if (normalized.contains("failed")
                || normalized.contains("error")
                || normalized.startsWith("could not")) {
            return ToastTone.ERROR;
        }
        if (normalized.contains("signed in")
                || normalized.contains("saved")
                || normalized.contains("installed")
                || normalized.contains("ready")
                || normalized.contains("cleared")
                || normalized.contains("liked")
                || normalized.contains("updated")
                || normalized.equals("accepted")) {
            return ToastTone.SUCCESS;
        }
        return ToastTone.NEUTRAL;
    }

    private enum ToastTone {
        SUCCESS(TOAST_SUCCESS, LauncherIcons.Glyph.CHECK),
        ERROR(TOAST_ERROR, LauncherIcons.Glyph.X),
        NEUTRAL(TOAST_NEUTRAL, LauncherIcons.Glyph.BELL);

        private final String styleClass;
        private final LauncherIcons.Glyph glyph;

        ToastTone(String styleClass, LauncherIcons.Glyph glyph) {
            this.styleClass = styleClass;
            this.glyph = glyph;
        }
    }
}
