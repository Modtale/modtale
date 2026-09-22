package net.modtale.launcher.ui.wardrobe;

import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
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
