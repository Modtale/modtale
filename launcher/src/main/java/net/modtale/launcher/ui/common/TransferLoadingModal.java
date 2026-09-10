package net.modtale.launcher.ui.common;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.FillRule;
import javafx.scene.shape.StrokeLineJoin;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/** Shared, lightweight transfer feedback for installs and account restores. */
public final class TransferLoadingModal extends StackPane {
    private final Label title = new Label();
    private final Label detail = new Label();
    private final Timeline animation;

    public TransferLoadingModal(String heading, String message) {
        getStyleClass().add("install-loading-overlay");
        setFocusTraversable(true);
        setOnMouseClicked(event -> event.consume());
        Canvas emblem = new Canvas(180, 120);
        var phase = new SimpleDoubleProperty(0);
        phase.addListener((observable, before, after) -> drawLoading(emblem.getGraphicsContext2D(), after.doubleValue()));
        drawLoading(emblem.getGraphicsContext2D(), 0);
        animation = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(phase, 0)),
                new KeyFrame(Duration.seconds(1.8), new KeyValue(phase, 1, Interpolator.LINEAR)));
        animation.setCycleCount(Animation.INDEFINITE);
        title.getStyleClass().add("status-modal-title");
        title.setWrapText(true);
        title.setAlignment(Pos.CENTER);
        detail.getStyleClass().add("status-modal-message");
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

    private static double hexX(double vertex, double radius) {
        return 90 + radius * Math.cos(Math.toRadians(vertex * 60 - 90));
    }

    private static double hexY(double vertex, double radius) {
        return 48 + radius * Math.sin(Math.toRadians(vertex * 60 - 90));
    }

    private static void drawLoading(GraphicsContext graphics, double phase) {
        graphics.clearRect(0, 0, 180, 120);
        double[] x = new double[6];
        double[] y = new double[6];
        graphics.save();
        graphics.setFillRule(FillRule.EVEN_ODD);
        graphics.beginPath();
        for (double radius : new double[]{46, 38}) {
            graphics.moveTo(hexX(0, radius), hexY(0, radius));
            for (int vertex = 1; vertex < 6; vertex++) {
                graphics.lineTo(hexX(vertex, radius), hexY(vertex, radius));
            }
            graphics.closePath();
        }
        graphics.setFill(Color.web("#eff6ff"));
        graphics.fill();
        // Both colors share this exact silhouette; moving caps cannot protrude at corners.
        graphics.clip();
        graphics.setLineWidth(14);
        graphics.setLineJoin(StrokeLineJoin.ROUND);
        graphics.setLineCap(StrokeLineCap.BUTT);
        graphics.setStroke(Color.web("#3b82f6"));
        graphics.beginPath();
        // Every segment uses the same vertices as the fixed outline, including corners.
        double start = phase * 6;
        double end = start + 1.5;
        for (double cursor = start; cursor <= end + .0001;) {
            int edge = (int) Math.floor(cursor);
            double fraction = cursor - edge;
            double px = hexX(edge, 42) * (1 - fraction) + hexX(edge + 1, 42) * fraction;
            double py = hexY(edge, 42) * (1 - fraction) + hexY(edge + 1, 42) * fraction;
            if (cursor == start) graphics.moveTo(px, py); else graphics.lineTo(px, py);
            if (cursor >= end) break;
            cursor = Math.min(end, edge + 1);
        }
        graphics.stroke();
        graphics.restore();
        for (int vertex = 0; vertex < 6; vertex++) {
            x[vertex] = hexX(vertex, 24);
            y[vertex] = hexY(vertex, 24);
        }
        graphics.setFill(Color.web("#3b82f6"));
        graphics.fillPolygon(x, y, 6);
        graphics.setFill(Color.web("#22324c"));
        graphics.fillRoundRect(0, 112, 180, 4, 4, 4);
        graphics.setFill(Color.web("#3b82f6"));
        double offset = 126 * (1 - Math.cos(phase * Math.PI * 2)) / 2;
        graphics.fillRoundRect(offset, 112, 54, 4, 4, 4);
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
