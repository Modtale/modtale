package net.modtale.launcher.ui.common;

import java.util.ArrayList;
import java.util.List;
import javafx.geometry.Bounds;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Labeled;
import javafx.scene.control.TitledPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.shape.Shape;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import javafx.scene.shape.MoveTo;
import javafx.geometry.BoundingBox;
import javafx.scene.text.Text;

/** Masks a fresh renderer tree in place: CSS, sizing, margins and responsive bindings stay intact. */
public final class LauncherSkeleton {
    private LauncherSkeleton() {}

    public static Node of(Node rendered) {
        if (!(rendered instanceof Pane pane)) {
            throw new IllegalArgumentException("Skeleton renderers must return a Pane");
        }
        return of(pane);
    }

    public static <T extends Pane> T of(T rendered) {
        rendered.getStyleClass().add("loading-skeleton");
        rendered.setAccessibleRole(AccessibleRole.TEXT);
        rendered.setAccessibleText("Loading content");
        rendered.setMouseTransparent(true);
        // Disable every descendant so placeholder buttons cannot receive keyboard activation.
        rendered.setDisable(true);
        Mask mask = new Mask(rendered);
        mask.setManaged(false);
        mask.setMouseTransparent(true);
        rendered.getChildren().add(mask);
        rendered.setAccessibleText("Loading content");
        mask.resize(rendered.getWidth(), rendered.getHeight());
        mask.prefWidthProperty().bind(rendered.widthProperty());
        mask.prefHeightProperty().bind(rendered.heightProperty());
        rendered.widthProperty().addListener((o, before, after) -> mask.resize(after.doubleValue(), rendered.getHeight()));
        rendered.heightProperty().addListener((o, before, after) -> mask.resize(rendered.getWidth(), after.doubleValue()));
        return rendered;
    }

    private static final class Mask extends Pane {
        private final Pane source;
        private final List<Node> targets = new ArrayList<>();
        private final List<Pane> bars = new ArrayList<>();

        private Mask(Pane source) {
            this.source = source;
            collect(source);
            for (Node ignored : targets) {
                Pane lines = new Pane();
                lines.setManaged(false);
                bars.add(lines);
                getChildren().add(lines);
            }
        }

        private void collect(Node node) {
            node.setFocusTraversable(false);
            node.setAccessibleText("");
            if (node instanceof TitledPane section) {
                if (section.getGraphic() != null) collect(section.getGraphic());
                if (section.getContent() != null) collect(section.getContent());
                return;
            }
            // Nested loading regions already own their mask; keep it independent.
            if (node != source && node.getStyleClass().contains("loading-skeleton")) return;
            boolean media = node.getStyleClass().stream().anyMatch(style ->
                    style.equals("project-banner-media") || style.equals("project-icon")
                    || style.endsWith("-avatar") || style.endsWith("-thumbnail")
                    || style.equals("notification-image-shell") || style.equals("notification-unread-dot"));
            if (node != source && (media || node instanceof Control || node instanceof Text
                    || node instanceof ImageView || node instanceof Shape)) {
                targets.add(node);
                if (node instanceof Labeled labeled && node.getStyleClass().contains("favorite-stat")
                        && labeled.getGraphic() != null) collect(labeled.getGraphic());
                node.setOpacity(0);
                // Inline precedence also hides controls whose :disabled stylesheet sets opacity.
                node.setStyle(node.getStyle() + "; -fx-opacity: 0;");
                node.boundsInParentProperty().addListener((o, before, after) -> requestLayout());
                return;
            }
            if (node instanceof Parent parent) {
                parent.getChildrenUnmodifiable().forEach(this::collect);
            }
        }

        @Override
        protected void layoutChildren() {
            for (int index = 0; index < targets.size(); index++) {
                Node target = targets.get(index);
                Pane lines = bars.get(index);
                if (!target.isVisible() || !target.isManaged()) {
                    lines.setVisible(false);
                    continue;
                }
                lines.setVisible(true);
                List<Bounds> rectangles = rectangles(target);
                int count = rectangles.size();
                while (lines.getChildren().size() < count) {
                    Region bar = new Region();
                    bar.getStyleClass().add("loading-skeleton-block");
                    if (target.getStyleClass().contains("project-banner-media")) {
                        bar.getStyleClass().add("loading-skeleton-media");
                    }
                    bar.setManaged(false);
                    lines.getChildren().add(bar);
                    bar.applyCss();
                }
                for (int i = 0; i < lines.getChildren().size(); i++) {
                    Region bar = (Region) lines.getChildren().get(i);
                    bar.setVisible(i < count);
                    if (i < count) {
                        Bounds bounds = rectangles.get(i);
                        bar.resizeRelocate(bounds.getMinX(), bounds.getMinY(), bounds.getWidth(), bounds.getHeight());
                    }
                }
            }
        }

        private List<Bounds> rectangles(Node target) {
            Text glyphs = target instanceof Text text ? text : null;
            boolean textControl = target instanceof Label || target.getStyleClass().contains("favorite-stat");
            if (textControl && target.lookup(".text") instanceof Text text) glyphs = text;
            if (glyphs != null && !glyphs.getText().isEmpty()) {
                // rangeShape comes from the renderer's actual glyph layout, including word wrapping,
                // ellipses, font metrics and alignment. Each subpath is one selected line/run.
                List<Bounds> result = new ArrayList<>();
                List<PathElement> run = new ArrayList<>();
                for (PathElement element : glyphs.rangeShape(0, glyphs.getText().length())) {
                    if (element instanceof MoveTo && !run.isEmpty()) {
                        addLine(result, glyphs, run);
                        run.clear();
                    }
                    run.add(element);
                }
                if (!run.isEmpty()) addLine(result, glyphs, run);
                if (!result.isEmpty()) return result;
            }
            Bounds bounds = sceneToLocal(target.localToScene(target.getLayoutBounds()));
            return bounds.getWidth() > 0 && bounds.getHeight() > 0 ? List.of(bounds) : List.of();
        }

        private void addLine(List<Bounds> lines, Text text, List<PathElement> elements) {
            Path selection = new Path(elements);
            selection.setStroke(null);
            selection.setFill(javafx.scene.paint.Color.BLACK);
            Bounds bounds = sceneToLocal(text.localToScene(selection.getBoundsInLocal()));
            double height = Math.min(bounds.getHeight(), text.getFont().getSize() * .7);
            if (bounds.getWidth() > 0 && height > 0) {
                lines.add(new BoundingBox(bounds.getMinX(), bounds.getMinY() + (bounds.getHeight() - height) / 2,
                        bounds.getWidth(), height));
            }
        }

    }
}
