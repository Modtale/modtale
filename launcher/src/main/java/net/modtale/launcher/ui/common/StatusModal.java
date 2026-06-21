package net.modtale.launcher.ui.common;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Supplier;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.Effect;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class StatusModal {

    private static final Logger LOG = LogManager.getLogger(StatusModal.class);

    public enum Type {
        SUCCESS("success", LauncherIcons.Glyph.CHECK),
        ERROR("error", LauncherIcons.Glyph.ALERT_TRIANGLE),
        WARNING("warning", LauncherIcons.Glyph.TRASH),
        INFO("info", LauncherIcons.Glyph.INFO);

        private final String styleSuffix;
        private final LauncherIcons.Glyph glyph;

        Type(String styleSuffix, LauncherIcons.Glyph glyph) {
            this.styleSuffix = styleSuffix;
            this.glyph = glyph;
        }
    }

    public enum Result {
        PRIMARY,
        SECONDARY,
        CLOSED
    }

    public static Builder builder(Supplier<StackPane> host) {
        return new Builder(host);
    }

    private static final double MODAL_WIDTH = 448;
    private static final double BODY_TEXT_WIDTH = 400;

    private final Supplier<StackPane> host;
    private final Type type;
    private final String title;
    private final String message;
    private final String actionLabel;
    private final LauncherIcons.Glyph actionIcon;
    private final String secondaryLabel;
    private final Node content;
    private final Map<Node, Effect> backdropEffects = new IdentityHashMap<>();

    private StackPane overlay;
    private StatusConfetti confetti;
    private boolean completed;

    private StatusModal(Builder builder) {
        this.host = builder.host == null ? () -> null : builder.host;
        this.type = builder.type == null ? Type.INFO : builder.type;
        this.title = value(builder.title);
        this.message = value(builder.message);
        this.actionLabel = value(builder.actionLabel).isBlank() ? "Close" : value(builder.actionLabel);
        this.actionIcon = builder.actionIcon;
        this.secondaryLabel = value(builder.secondaryLabel);
        this.content = builder.content;
    }

    public Result showAndWait() {
        if (!Platform.isFxApplicationThread()) {
            throw new IllegalStateException("StatusModal must be shown on the JavaFX application thread.");
        }
        if (!show()) {
            return Result.CLOSED;
        }
        Object result = Platform.enterNestedEventLoop(this);
        return result instanceof Result modalResult ? modalResult : Result.CLOSED;
    }

    private boolean show() {
        StackPane hostPane = host.get();
        if (hostPane == null) {
            return false;
        }
        if (type == Type.ERROR) {
            LOG.warn("Error modal: {} - {}", title, message);
        }
        overlay = overlayShell();
        blurBackdrop(hostPane);
        if (type == Type.SUCCESS) {
            confetti = new StatusConfetti();
            overlay.getChildren().setAll(confetti.canvas, card());
        } else {
            overlay.getChildren().setAll(card());
        }
        hostPane.getChildren().add(overlay);
        if (confetti != null) {
            confetti.start();
        }
        Platform.runLater(overlay::requestFocus);
        return true;
    }

    private StackPane overlayShell() {
        StackPane shell = new StackPane();
        shell.getStyleClass().add("status-modal-overlay");
        shell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        shell.setFocusTraversable(true);
        shell.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                complete(Result.CLOSED);
                event.consume();
            }
        });
        shell.setOnMouseClicked(event -> {
            if (event.getTarget() == shell) {
                complete(Result.CLOSED);
            }
        });
        return shell;
    }

    private StackPane card() {
        StackPane card = new StackPane();
        card.getStyleClass().addAll("status-modal", "status-modal-" + type.styleSuffix);
        card.setMaxWidth(MODAL_WIDTH);
        card.setMaxHeight(Region.USE_PREF_SIZE);
        card.setPrefWidth(MODAL_WIDTH);
        card.setOnMouseClicked(event -> event.consume());

        VBox layout = new VBox(0);
        layout.getChildren().addAll(body(), footer());

        Button close = new Button(null, LauncherIcons.icon(LauncherIcons.Glyph.X, 20));
        close.getStyleClass().add("status-modal-close");
        close.setOnAction(event -> complete(Result.CLOSED));
        StackPane.setAlignment(close, Pos.TOP_RIGHT);
        StackPane.setMargin(close, new Insets(16, 16, 0, 0));

        card.getChildren().addAll(layout, close);
        return card;
    }

    private VBox body() {
        VBox body = new VBox(0);
        body.getStyleClass().add("status-modal-body");
        body.setAlignment(Pos.CENTER);

        StackPane icon = new StackPane(LauncherIcons.icon(type.glyph, 32));
        icon.getStyleClass().add("status-modal-icon");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("status-modal-title");
        titleLabel.setWrapText(true);
        titleLabel.setAlignment(Pos.CENTER);
        titleLabel.setMaxWidth(BODY_TEXT_WIDTH);

        Label messageLabel = new Label(message);
        messageLabel.getStyleClass().add("status-modal-message");
        messageLabel.setWrapText(true);
        messageLabel.setAlignment(Pos.CENTER);
        messageLabel.setMaxWidth(BODY_TEXT_WIDTH);

        body.getChildren().addAll(icon, titleLabel, messageLabel);
        VBox.setMargin(icon, new Insets(0, 0, 16, 0));
        VBox.setMargin(titleLabel, new Insets(0, 0, 8, 0));
        if (content != null) {
            content.getStyleClass().add("status-modal-custom-content");
            if (content instanceof Region region) {
                region.setMaxWidth(BODY_TEXT_WIDTH);
            }
            if (content instanceof VBox box) {
                box.setAlignment(Pos.CENTER);
            } else if (content instanceof HBox box) {
                box.setAlignment(Pos.CENTER);
            } else if (content instanceof StackPane pane) {
                pane.setAlignment(Pos.CENTER);
            }
            VBox.setMargin(content, new Insets(16, 0, 0, 0));
            body.getChildren().add(content);
        }
        return body;
    }

    private HBox footer() {
        HBox footer = new HBox(12);
        footer.getStyleClass().add("status-modal-footer");
        footer.setAlignment(Pos.CENTER);

        if (showsSecondaryAction()) {
            Button secondary = new Button(secondaryLabel.isBlank() ? "Cancel" : secondaryLabel);
            secondary.getStyleClass().add("status-modal-secondary");
            secondary.setOnAction(event -> complete(Result.SECONDARY));
            footer.getChildren().add(secondary);
        }

        Button primary = new Button(actionLabel);
        primary.getStyleClass().add("status-modal-primary");
        primary.setOnAction(event -> complete(Result.PRIMARY));
        if (actionIcon != null || type == Type.SUCCESS) {
            primary.setGraphic(LauncherIcons.icon(actionIcon == null ? LauncherIcons.Glyph.ARROW_RIGHT : actionIcon, 20));
        }
        footer.getChildren().add(primary);
        return footer;
    }

    private boolean showsSecondaryAction() {
        return !secondaryLabel.isBlank() || type == Type.WARNING || type == Type.INFO;
    }

    private void complete(Result result) {
        if (completed) {
            return;
        }
        completed = true;
        hide();
        Platform.exitNestedEventLoop(this, result);
    }

    private void hide() {
        if (overlay == null) {
            return;
        }
        if (confetti != null) {
            confetti.stop();
            confetti = null;
        }
        Parent parent = overlay.getParent();
        if (parent instanceof StackPane stack) {
            stack.getChildren().remove(overlay);
        }
        overlay = null;
        restoreBackdrop();
    }

    private void blurBackdrop(StackPane hostPane) {
        restoreBackdrop();
        for (Node child : hostPane.getChildren()) {
            backdropEffects.put(child, child.getEffect());
            child.setEffect(new GaussianBlur(6));
        }
    }

    private void restoreBackdrop() {
        backdropEffects.forEach(Node::setEffect);
        backdropEffects.clear();
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    public static final class Builder {
        private final Supplier<StackPane> host;
        private Type type = Type.INFO;
        private String title = "";
        private String message = "";
        private String actionLabel = "";
        private LauncherIcons.Glyph actionIcon;
        private String secondaryLabel = "";
        private Node content;

        private Builder(Supplier<StackPane> host) {
            this.host = host;
        }

        public Builder type(Type type) {
            this.type = type;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder actionLabel(String actionLabel) {
            this.actionLabel = actionLabel;
            return this;
        }

        public Builder actionIcon(LauncherIcons.Glyph actionIcon) {
            this.actionIcon = actionIcon;
            return this;
        }

        public Builder secondaryLabel(String secondaryLabel) {
            this.secondaryLabel = secondaryLabel;
            return this;
        }

        public Builder content(Node content) {
            this.content = content;
            return this;
        }

        public Result showAndWait() {
            return new StatusModal(this).showAndWait();
        }
    }

    private static final class StatusConfetti {
        private static final int PARTICLE_COUNT = 400;
        private static final String[] COLORS = {
                "#3b82f6", "#ef4444", "#10b981", "#f59e0b",
                "#8b5cf6", "#ec4899", "#06b6d4", "#ffffff"
        };

        private final Canvas canvas = new Canvas();
        private final Random random = new Random();
        private final List<Particle> particles = new ArrayList<>(PARTICLE_COUNT);
        private final AnimationTimer timer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                draw();
            }
        };

        private StatusConfetti() {
            canvas.getStyleClass().add("status-modal-confetti");
            canvas.setMouseTransparent(true);
            canvas.parentProperty().addListener((observable, oldParent, newParent) -> {
                canvas.widthProperty().unbind();
                canvas.heightProperty().unbind();
                if (newParent instanceof Region region) {
                    canvas.widthProperty().bind(region.widthProperty());
                    canvas.heightProperty().bind(region.heightProperty());
                } else {
                    canvas.setWidth(0);
                    canvas.setHeight(0);
                }
            });
        }

        private void start() {
            timer.start();
        }

        private void stop() {
            timer.stop();
        }

        private void draw() {
            double width = canvas.getWidth();
            double height = canvas.getHeight();
            if (width <= 0 || height <= 0) {
                return;
            }
            if (particles.isEmpty()) {
                seed(width, height);
            }
            GraphicsContext graphics = canvas.getGraphicsContext2D();
            graphics.clearRect(0, 0, width, height);
            int activeCount = 0;
            for (Particle particle : particles) {
                if (!particle.active) {
                    continue;
                }
                particle.x += particle.vx;
                particle.y += particle.vy;
                particle.vy += particle.gravity;
                particle.vx *= particle.drag;
                particle.vy *= particle.drag;
                particle.tilt += particle.tiltAngleIncrement;
                particle.angle += particle.rotationSpeed;
                if (particle.y > height + 100) {
                    particle.active = false;
                    continue;
                }
                activeCount++;
                graphics.save();
                graphics.translate(particle.x, particle.y);
                graphics.rotate(Math.toDegrees(particle.angle));
                graphics.scale(1, Math.cos(particle.tilt));
                graphics.setFill(particle.color);
                graphics.fillRect(-particle.w / 2, -particle.h / 2, particle.w, particle.h);
                graphics.restore();
            }
            if (activeCount == 0) {
                stop();
            }
        }

        private void seed(double width, double height) {
            double centerX = width / 2;
            double centerY = height / 2;
            for (int i = 0; i < PARTICLE_COUNT; i++) {
                double angle = random.nextDouble() * Math.PI * 2;
                double velocity = random.nextDouble() * 35 + 10;
                particles.add(new Particle(
                        centerX,
                        centerY,
                        Math.cos(angle) * velocity,
                        Math.sin(angle) * velocity,
                        Color.web(COLORS[random.nextInt(COLORS.length)]),
                        random.nextDouble() * 12 + 4,
                        random.nextDouble() * 6 + 4,
                        0.6,
                        0.92,
                        random.nextDouble() * Math.PI * 2,
                        (random.nextDouble() - 0.5) * 0.3,
                        random.nextDouble() * 10,
                        random.nextDouble() * 0.1 + 0.05,
                        true
                ));
            }
        }
    }

    private static final class Particle {
        private double x;
        private double y;
        private double vx;
        private double vy;
        private final Color color;
        private final double w;
        private final double h;
        private final double gravity;
        private final double drag;
        private double angle;
        private final double rotationSpeed;
        private double tilt;
        private final double tiltAngleIncrement;
        private boolean active;

        private Particle(
                double x,
                double y,
                double vx,
                double vy,
                Color color,
                double w,
                double h,
                double gravity,
                double drag,
                double angle,
                double rotationSpeed,
                double tilt,
                double tiltAngleIncrement,
                boolean active
        ) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.color = color;
            this.w = w;
            this.h = h;
            this.gravity = gravity;
            this.drag = drag;
            this.angle = angle;
            this.rotationSpeed = rotationSpeed;
            this.tilt = tilt;
            this.tiltAngleIncrement = tiltAngleIncrement;
            this.active = active;
        }
    }
}
