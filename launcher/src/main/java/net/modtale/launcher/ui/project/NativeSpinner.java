package net.modtale.launcher.ui.project;

import javafx.animation.Interpolator;
import javafx.animation.RotateTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import net.modtale.launcher.ui.common.LauncherIcons;

import java.util.Locale;

final class NativeSpinner {

    private static final Duration SPIN_DURATION = Duration.millis(900);

    private NativeSpinner() {
    }

    static Node inline() {
        return inline(null, 20);
    }

    static Node inline(String label) {
        return inline(label, 20);
    }

    static Node inline(double size) {
        return inline(null, size);
    }

    static Node inline(String label, double size) {
        HBox row = new HBox(8);
        row.getStyleClass().addAll("native-spinner", "inline");
        row.setAlignment(Pos.CENTER);
        row.getChildren().add(spinnerIcon(size));
        if (label != null && !label.isBlank()) {
            Label text = label(label);
            text.getStyleClass().add("inline");
            row.getChildren().add(text);
        }
        return row;
    }

    static Node centered() {
        VBox column = new VBox(12);
        column.getStyleClass().addAll("native-spinner", "centered");
        column.setAlignment(Pos.CENTER);
        column.getChildren().add(spinnerIcon(32));
        return column;
    }

    static Node fullScreen() {
        Node node = centered();
        node.getStyleClass().add("full-screen");
        return node;
    }

    private static Node spinnerIcon(double size) {
        StackPane icon = new StackPane(LauncherIcons.icon(LauncherIcons.Glyph.LOADER_2, size));
        icon.getStyleClass().add("native-spinner-icon");
        icon.setMinSize(size, size);
        icon.setPrefSize(size, size);
        icon.setMaxSize(size, size);
        spin(icon);
        return icon;
    }

    private static Label label(String value) {
        Label label = new Label(value.toUpperCase(Locale.ROOT));
        label.getStyleClass().add("native-spinner-label");
        return label;
    }

    private static void spin(Node node) {
        RotateTransition transition = new RotateTransition(SPIN_DURATION, node);
        transition.setByAngle(360);
        transition.setCycleCount(RotateTransition.INDEFINITE);
        transition.setInterpolator(Interpolator.LINEAR);
        node.sceneProperty().addListener((observable, previous, current) -> {
            if (current == null) {
                transition.stop();
            } else {
                transition.play();
            }
        });
    }

}
