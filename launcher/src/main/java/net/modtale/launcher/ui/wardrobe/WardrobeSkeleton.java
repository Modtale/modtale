package net.modtale.launcher.ui.wardrobe;

import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.shape.SVGPath;
import javafx.scene.layout.*;

final class WardrobeSkeleton {
    private WardrobeSkeleton() {}

    static Region line(double width, double height) {
        Region block = new Region();
        block.getStyleClass().addAll("loading-skeleton-block", "wardrobe-skeleton-block");
        block.setMinSize(0, height);
        block.setPrefSize(width, height);
        block.setMaxSize(width, height);
        return block;
    }

    static StackPane portrait() {
        VBox figure = new VBox(5, line(34, 34),
                new HBox(5, line(14, 53), line(40, 58), line(14, 53)),
                new HBox(5, line(18, 43), line(18, 43)));
        figure.setAlignment(Pos.CENTER);
        figure.setMaxSize(90, 150);
        figure.getChildren().stream().filter(HBox.class::isInstance)
                .map(HBox.class::cast).forEach(row -> row.setAlignment(Pos.TOP_CENTER));
        StackPane art = new StackPane(figure);
        art.getStyleClass().add("wardrobe-skeleton");
        art.setMinSize(0, 0);
        art.setAccessibleRole(AccessibleRole.TEXT);
        art.setAccessibleText("Loading outfit preview");
        art.setMouseTransparent(true);
        return art;
    }

    static StackPane cosmetic(String category) {
        Group model = new Group();
        String group = CosmeticFraming.forCategory(category).group();
        if (group.equals("Head")) {
            face(model, "M 31 25 L 76 18 L 96 32 L 51 40 Z", true);
            face(model, "M 31 25 L 51 40 L 51 85 L 31 70 Z", false);
            face(model, "M 51 40 L 96 32 L 96 78 L 51 85 Z", true);
            face(model, "M 51 87 L 76 83 L 76 93 L 99 102 L 99 121 L 29 121 L 29 102 L 51 95 Z", false);
        } else if (category.equals("shoes")) {
            face(model, "M 28 36 L 52 33 L 52 80 L 62 95 L 62 106 L 26 110 L 18 103 L 18 90 L 28 80 Z", true);
            face(model, "M 72 31 L 95 28 L 95 74 L 105 89 L 105 102 L 69 106 L 62 99 L 62 87 L 72 76 Z", true);
            face(model, "M 18 99 L 26 104 L 62 100 L 62 106 L 26 110 L 18 103 Z M 62 95 L 69 100 L 105 96 L 105 102 L 69 106 L 62 99 Z", false);
        } else if (group.equals("Bottoms")) {
            face(model, "M 31 23 L 91 18 L 95 49 L 88 121 L 66 124 L 63 65 L 56 66 L 51 125 L 29 121 L 27 49 Z", true);
            face(model, "M 31 23 L 91 18 L 92 29 L 30 35 Z M 63 65 L 70 61 L 72 122 L 66 124 Z", false);
        } else if (group.equals("Tops") || category.equals("gloves")) {
            face(model, "M 46 25 L 75 23 L 78 34 L 94 38 L 88 105 L 35 110 L 28 44 L 44 36 Z", true);
            face(model, "M 28 44 L 42 41 L 35 97 L 17 101 L 13 88 Z M 80 38 L 94 38 L 110 86 L 107 97 L 91 98 Z", false);
            face(model, "M 46 25 L 60 34 L 75 23 L 78 34 L 60 46 L 44 36 Z", false);
        } else if (category.equals("cape")) {
            face(model, "M 45 24 L 80 20 L 103 115 L 65 126 L 23 115 Z", true);
            face(model, "M 45 24 L 55 31 L 43 119 L 23 115 Z M 73 29 L 80 20 L 103 115 L 87 120 Z", false);
        } else {
            return portrait();
        }
        Pane canvas = new Pane(model);
        canvas.setMinSize(120, 140); canvas.setPrefSize(120, 140); canvas.setMaxSize(120, 140);
        StackPane art = new StackPane(canvas);
        art.getStyleClass().add("wardrobe-skeleton");
        art.setMinSize(0, 0);
        art.setMouseTransparent(true);
        art.setAccessibleRole(AccessibleRole.TEXT);
        art.setAccessibleText("Loading cosmetic preview");
        return art;
    }

    private static void face(Group model, String path, boolean lit) {
        SVGPath shape = new SVGPath();
        shape.setContent(path);
        shape.setFill(LinearGradient.valueOf(lit
                ? "from 15% 0% to 85% 100%, #40546d 0%, #29394f 100%"
                : "from 0% 0% to 100% 100%, #2d4058 0%, #1d2c40 100%"));
        shape.setStroke(Color.web("#61758d", .14));
        shape.setStrokeWidth(.7);
        model.getChildren().add(shape);
    }

    static Button cosmeticCard(String category) {
        StackPane art = cosmetic(category);
        art.getStyleClass().add("cosmetic-card-art");
        art.setPrefSize(145, 145);
        StackPane caption = new StackPane(line(95, 9));
        caption.setAlignment(Pos.CENTER_LEFT);
        caption.setPadding(new javafx.geometry.Insets(2, 3, 0, 3));
        caption.setMinHeight(19); caption.setPrefHeight(19);
        VBox contents = new VBox(8, art, caption);
        Button card = new Button();
        card.setGraphic(contents);
        card.getStyleClass().add("wardrobe-card");
        card.setMinWidth(0); card.setMaxWidth(Double.MAX_VALUE);
        art.prefWidthProperty().bind(card.widthProperty().subtract(22));
        card.setMouseTransparent(true); card.setFocusTraversable(false);
        card.setDisable(true); card.setStyle("-fx-opacity: 1;");
        card.setAccessibleRole(AccessibleRole.TEXT);
        card.setAccessibleText("Loading cosmetics");
        return card;
    }

    static VBox card(double height, boolean caption) {
        StackPane art = portrait();
        art.getStyleClass().add("wardrobe-card-art");
        art.setPrefHeight(height);
        VBox card = new VBox(8, art);
        if (caption) card.getChildren().add(line(95, 12));
        card.getStyleClass().addAll("wardrobe-card", "wardrobe-skeleton-card");
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMouseTransparent(true);
        card.setAccessibleRole(AccessibleRole.TEXT);
        card.setAccessibleText("Loading looks");
        return card;
    }
}
