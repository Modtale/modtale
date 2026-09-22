package net.modtale.launcher.ui.feedback;


import java.util.concurrent.Executor;
import java.util.function.Supplier;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

public final class LauncherFeedbackView {

    private final Label statusText = new Label("Ready");
    private final Label toastTitle = new Label();
    private final Label toastMessage = new Label();
    private final StackPane toast = new StackPane();

    public LauncherFeedbackView() {
        toast.getStyleClass().add("toast");
        toast.setMinWidth(Region.USE_PREF_SIZE);
        toast.setPrefWidth(Region.USE_COMPUTED_SIZE);
        toast.setMaxWidth(Region.USE_PREF_SIZE);
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
        return new LauncherFeedback(executor, statusText, toast, toastTitle, toastMessage, idleStatus);
    }


}
