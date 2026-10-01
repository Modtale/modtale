package net.modtale.launcher.ui.wardrobe;

import java.util.*;
import java.nio.file.Path;
import javafx.scene.image.ImageView;
import java.util.function.Consumer;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import net.modtale.launcher.wardrobe.*;

final class WardrobeCategoryNavigation extends VBox implements AutoCloseable {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private final Map<String, Section> groups = new LinkedHashMap<>();
    private final Map<String, Button> choices = new LinkedHashMap<>();
    private final Map<String, String> remembered = new HashMap<>();
    private final WardrobeCategoryImages pictures;
    private String activeGroup = "";
    private boolean syncing, closed;

    WardrobeCategoryNavigation(CosmeticCatalogClient catalog, Path assets, Consumer<String> select) {
        pictures = new WardrobeCategoryImages(catalog, assets);
        getStyleClass().add("wardrobe-category-navigation");
        setMinWidth(0);
        setSpacing(5);
        for (String group : List.of("Head", "Body", "Tops", "Bottoms", "Accessories")) {
            var categories = catalog.availableCategories().stream()
                    .filter(entry -> CosmeticFraming.forCategory(entry.key()).group().equals(group)).toList();
            if (categories.isEmpty()) continue;
            TilePane tiles = new TilePane(6, 6);
            tiles.setPrefColumns(2); tiles.setPrefTileWidth(58); tiles.setPrefTileHeight(58);
            tiles.setMinWidth(0); tiles.setPrefWidth(126); tiles.setMaxWidth(126);
            double tileHeight = ((categories.size() + 1) / 2) * 64 + 12;
            tiles.setMinHeight(tileHeight); tiles.setPrefHeight(tileHeight); tiles.setMaxHeight(tileHeight);
            tiles.getStyleClass().add("wardrobe-category-tiles");
            String symbol = switch (group) {
                case "Head" -> "face"; case "Tops" -> "overtop"; case "Bottoms" -> "pants";
                case "Accessories" -> "gloves"; default -> "bodyCharacteristic";
            };
            String preferredSymbol = symbol;
            if (categories.stream().noneMatch(entry -> entry.key().equals(preferredSymbol))) symbol = categories.getFirst().key();
            var headingImage = picture(symbol, 24);
            Section pane = new Section(group, headingImage, tiles);
            pane.setMinWidth(0); pane.setMaxWidth(Double.MAX_VALUE);
            pane.setAccessibleText(group + " categories");
            pane.setId("wardrobe-group-" + group.toLowerCase(Locale.ROOT));
            groups.put(group, pane); getChildren().add(pane);
            pane.onExpand = () -> {
                if (!syncing && !closed) {
                    groups.values().stream().filter(other -> other != pane).forEach(other -> other.setExpanded(false));
                    select.accept(remembered.getOrDefault(group, categories.getFirst().key()));
                }
            };
            for (CosmeticCategory category : categories) {
                var image = picture(category.key(), 48);
                StackPane art = new StackPane(image);
                art.setMinSize(48, 48); art.setPrefSize(48, 48);
                Button button = new Button("", art);
                button.getStyleClass().add("wardrobe-category-picture");
                button.setId("wardrobe-category-" + category.key());
                button.setMinSize(58, 58); button.setPrefSize(58, 58); button.setMaxSize(58, 58);
                button.setAccessibleText(category.label());
                var help = new Tooltip(category.label()); help.setShowDelay(javafx.util.Duration.millis(150));
                button.setTooltip(help);
                button.setOnAction(e -> select.accept(category.key()));
                choices.put(category.key(), button); tiles.getChildren().add(button);

            }
        }
    }

    void selectCategory(String category) {
        String group = CosmeticFraming.forCategory(category).group();
        remembered.put(group, category);
        choices.forEach((key, button) -> button.pseudoClassStateChanged(SELECTED, key.equals(category)));
        groups.forEach((key, pane) -> pane.pseudoClassStateChanged(SELECTED, key.equals(group)));
        if (!group.equals(activeGroup)) {
            syncing = true;
            groups.forEach((key, pane) -> pane.setExpanded(key.equals(group)));
            syncing = false;
            activeGroup = group;
        }
    }

    private ImageView picture(String category, double size) {
        ImageView view = new ImageView();
        view.setFitWidth(size); view.setFitHeight(size); view.setPreserveRatio(true);
        view.setMouseTransparent(true);
        pictures.load(category).thenAccept(image -> { if (!closed) view.setImage(image); });
        return view;
    }

    static final class Section extends VBox {
        private final Button heading = new Button();
        private final StackPane body;
        private final javafx.beans.property.DoubleProperty reveal = new javafx.beans.property.SimpleDoubleProperty();
        private final javafx.animation.Timeline animation = new javafx.animation.Timeline();
        private final javafx.scene.Node arrow;
        private final double contentHeight;
        private final String label;
        private boolean expanded;
        Runnable onExpand = () -> {};

        Section(String label, javafx.scene.Node image, TilePane tiles) {
            this.label = label;
            arrow = net.modtale.launcher.ui.common.LauncherIcons.icon(
                    net.modtale.launcher.ui.common.LauncherIcons.Glyph.CHEVRON_RIGHT, 14);
            Label title = new Label(label); title.setMinWidth(0); title.getStyleClass().add("wardrobe-category-heading-label");
            Region spacer = new Region(); HBox.setHgrow(spacer, Priority.ALWAYS);
            HBox row = new HBox(6, image, title, spacer, arrow); row.setAlignment(Pos.CENTER_LEFT);
            heading.setGraphic(row); heading.setMaxWidth(Double.MAX_VALUE); heading.setMinWidth(0);
            row.prefWidthProperty().bind(heading.widthProperty().subtract(18));
            heading.getStyleClass().add("wardrobe-category-heading");
            heading.setAccessibleText(label + " categories");
            heading.setAccessibleHelp("Collapsed. Activate to expand.");
            heading.setTooltip(new Tooltip("Expand " + label));
            heading.setOnAction(e -> setExpanded(!expanded));
            contentHeight = tiles.getPrefHeight();
            body = new StackPane(tiles); body.setAlignment(Pos.TOP_CENTER);
            body.setMinWidth(0); body.setVisible(false); body.setManaged(false);
            body.minHeightProperty().bind(reveal); body.prefHeightProperty().bind(reveal); body.maxHeightProperty().bind(reveal);
            var clip = new javafx.scene.shape.Rectangle();
            clip.widthProperty().bind(body.widthProperty()); clip.heightProperty().bind(reveal); body.setClip(clip);
            getChildren().addAll(heading, body);
        }

        boolean isExpanded() { return expanded; }
        boolean isAnimated() { return true; }
        void setExpanded(boolean value) {
            if (expanded == value) return;
            expanded = value; animation.stop();
            heading.pseudoClassStateChanged(SELECTED, value);
            heading.setAccessibleHelp(value ? "Expanded. Activate to collapse." : "Collapsed. Activate to expand.");
            heading.setTooltip(new Tooltip((value ? "Collapse " : "Expand ") + label));
            body.setVisible(true); body.setManaged(true); body.setMouseTransparent(!value);
            body.setDisable(!value);
            animation.getKeyFrames().setAll(new javafx.animation.KeyFrame(javafx.util.Duration.millis(160),
                    new javafx.animation.KeyValue(reveal, value ? contentHeight : 0, javafx.animation.Interpolator.EASE_BOTH),
                    new javafx.animation.KeyValue(arrow.rotateProperty(), value ? 90 : 0, javafx.animation.Interpolator.EASE_BOTH),
                    new javafx.animation.KeyValue(body.opacityProperty(), value ? 1 : 0, javafx.animation.Interpolator.EASE_BOTH)));
            animation.setOnFinished(e -> { if (!expanded) { body.setVisible(false); body.setManaged(false); } });
            animation.play();
            if (value) onExpand.run();
        }
        void stop() { animation.stop(); }
    }

    @Override public void close() { closed = true; pictures.close(); groups.values().forEach(pane -> { pane.stop(); }); }
}
