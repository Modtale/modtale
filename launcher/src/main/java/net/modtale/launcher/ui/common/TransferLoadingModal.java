package net.modtale.launcher.ui.common;

import javafx.animation.Animation;
import javafx.animation.FadeTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Polygon;
import javafx.util.Duration;

/** Shared, lightweight transfer feedback for installs and account restores. */
public final class TransferLoadingModal extends StackPane {
    private final Label title = new Label();
    private final Label detail = new Label();
    private final SequentialTransition animation = new SequentialTransition();

    public TransferLoadingModal(String heading, String message) {
        getStyleClass().add("install-loading-overlay");
        setFocusTraversable(true);
        setOnMouseClicked(event -> event.consume());
        StackPane emblem = new StackPane();
        emblem.setMinSize(100, 100);
        emblem.setMaxSize(100, 100);
        for (int i = 0; i < 3; i++) {
            double radius = 46 - i * 12;
            Polygon hex = new Polygon();
            for (int point = 0; point < 6; point++) {
                double angle = Math.toRadians(point * 60 - 90);
                hex.getPoints().addAll(50 + radius * Math.cos(angle), 50 + radius * Math.sin(angle));
            }
            hex.setFill(i == 2 ? Color.web("#3b82f6") : Color.TRANSPARENT);
            hex.setStroke(Color.web(i == 0 ? "#60a5fa" : "#93c5fd"));
            hex.setStrokeWidth(2);
            hex.setOpacity(.35);
            emblem.getChildren().add(hex);
            FadeTransition pulse = new FadeTransition(Duration.millis(420), hex);
            pulse.setFromValue(.35);
            pulse.setToValue(1);
            pulse.setAutoReverse(true);
            pulse.setCycleCount(2);
            animation.getChildren().add(pulse);
        }
        animation.setCycleCount(Animation.INDEFINITE);
        title.getStyleClass().add("install-loading-title");
        title.setWrapText(true);
        title.setAlignment(Pos.CENTER);
        detail.getStyleClass().add("install-loading-subtitle");
        detail.setWrapText(true);
        detail.setMaxWidth(360);
        VBox card = new VBox(18, emblem, title, detail);
        card.setAlignment(Pos.CENTER);
        card.getStyleClass().add("install-loading-card");
        card.setMaxWidth(448);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        getChildren().add(card);
        update(heading, message);
        sceneProperty().addListener((observable, before, after) -> {
            if (after == null) animation.stop(); else animation.playFromStart();
        });
    }

    public void update(String heading, String message) {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> update(heading, message));
            return;
        }
        title.setText(heading);
        detail.setText(message);
    }

    public void dismiss() {
        animation.stop();
        if (getParent() instanceof StackPane host) host.getChildren().remove(this);
    }
}
