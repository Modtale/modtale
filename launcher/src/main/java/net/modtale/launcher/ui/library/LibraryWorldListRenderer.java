package net.modtale.launcher.ui.library;

import static net.modtale.launcher.ui.common.LauncherUi.pseudo;

import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleWorld;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherIcons;

final class LibraryWorldListRenderer {

    private static final double WORLD_ICON_SIZE = 42;

    private final CachedImageLoader imageLoader;
    private final Consumer<HytaleWorld> selectWorld;

    LibraryWorldListRenderer(CachedImageLoader imageLoader, Consumer<HytaleWorld> selectWorld) {
        this.imageLoader = imageLoader;
        this.selectWorld = selectWorld;
    }

    Button worldRow(LibraryWorldListItem item, boolean selected) {
        Button button = new Button();
        button.getStyleClass().addAll("library-project-row", "library-world-tab");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setGraphic(worldRowContent(item));
        button.setOnAction(event -> selectWorld.accept(item.world()));
        pseudo(button, "selected", selected);
        return button;
    }

    private Node worldRowContent(LibraryWorldListItem item) {
        HytaleWorld world = item.world();
        HBox row = new HBox(12);
        row.getStyleClass().add("library-project-row-content");
        StackPane icon = worldIcon(world);

        VBox copy = new VBox(4);
        Label title = new Label(world.name());
        title.getStyleClass().add("library-project-title");
        Label meta = new Label(item.meta());
        meta.getStyleClass().add("library-project-meta");
        copy.getChildren().addAll(title, meta);
        HBox.setHgrow(copy, Priority.ALWAYS);

        VBox status = new VBox(5);
        status.setAlignment(Pos.CENTER_RIGHT);
        Label enabled = new Label(item.enabledProjectCount() + "/" + item.totalProjectCount());
        enabled.getStyleClass().add("library-version-pill");
        Label label = new Label("Enabled");
        label.getStyleClass().add("library-world-tab-caption");
        status.getChildren().addAll(enabled, label);

        row.getChildren().addAll(icon, copy, status);
        return row;
    }

    private StackPane worldIcon(HytaleWorld world) {
        StackPane shell = new StackPane();
        shell.getStyleClass().add("library-project-icon");
        shell.setMinSize(WORLD_ICON_SIZE, WORLD_ICON_SIZE);
        shell.setPrefSize(WORLD_ICON_SIZE, WORLD_ICON_SIZE);
        shell.setMaxSize(WORLD_ICON_SIZE, WORLD_ICON_SIZE);

        String preview = world.previewImage();
        if (!preview.isBlank() && imageLoader != null) {
            ImageView image = new ImageView();
            image.setFitWidth(WORLD_ICON_SIZE);
            image.setFitHeight(WORLD_ICON_SIZE);
            image.setPreserveRatio(false);
            image.setSmooth(true);
            image.setMouseTransparent(true);
            image.setClip(roundedClip(WORLD_ICON_SIZE, 8));
            imageLoader.loadInto(image, preview, WORLD_ICON_SIZE, WORLD_ICON_SIZE);
            shell.getChildren().add(image);
        } else {
            shell.getChildren().add(LauncherIcons.icon(LauncherIcons.Glyph.GLOBE, 18));
        }
        return shell;
    }

    private Rectangle roundedClip(double size, double radius) {
        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        return clip;
    }
}
