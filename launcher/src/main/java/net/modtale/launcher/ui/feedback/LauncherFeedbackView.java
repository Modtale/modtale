package net.modtale.launcher.ui.feedback;

import static net.modtale.launcher.ui.common.LauncherUi.statusDot;

import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public final class LauncherFeedbackView {

    private final Label statusText = new Label("Ready");
    private final Label toastTitle = new Label();
    private final Label toastMessage = new Label();
    private final VBox logList = new VBox(4);
    private final StackPane toast = new StackPane();

    public LauncherFeedbackView() {
        toast.getStyleClass().add("toast");
        toast.setMaxWidth(360);
        toast.setMaxHeight(Region.USE_PREF_SIZE);
        toast.setVisible(false);
        toast.setManaged(false);
    }

    public Label statusText() {
        return statusText;
    }

    public StackPane toast() {
        return toast;
    }

    public LauncherFeedback feedback(Executor executor, Supplier<String> idleStatus) {
        return new LauncherFeedback(executor, statusText, logList, toast, toastTitle, toastMessage, idleStatus);
    }

    public Node statusChip() {
        HBox status = new HBox(8, statusDot(), statusText);
        status.getStyleClass().add("status-chip");
        status.setAlignment(Pos.CENTER_LEFT);
        return status;
    }
}
