package net.modtale.launcher.ui.common;

import javafx.geometry.Insets;
import javafx.scene.AccessibleRole;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Quiet, non-interactive placeholders with no timers to retain detached views. */
public final class LauncherSkeleton {
    private LauncherSkeleton() {}

    public static Region block(double width, double height) {
        Region block = new Region();
        block.getStyleClass().add("loading-skeleton-block");
        block.setMinSize(0, height);
        block.setPrefSize(width, height);
        block.setMaxSize(width, height);
        block.setMouseTransparent(true);
        return block;
    }

    public static VBox text() {
        VBox text = new VBox(10, block(180, 16), block(360, 10), block(260, 10));
        text.setMinWidth(0);
        text.setMaxWidth(Double.MAX_VALUE);
        return text;
    }

    public static VBox rows(int count) {
        VBox list = new VBox(22);
        list.setPadding(new Insets(20));
        list.setMaxWidth(Double.MAX_VALUE);
        list.setAccessibleRole(AccessibleRole.TEXT);
        list.setAccessibleText("Loading content");
        list.setMouseTransparent(true);
        for (int i = 0; i < count; i++) {
            VBox copy = text();
            HBox.setHgrow(copy, Priority.ALWAYS);
            HBox row = new HBox(16, block(44, 44), copy);
            row.setMinWidth(0);
            list.getChildren().add(row);
        }
        return list;
    }

    public static VBox card(double width, double height, boolean banner) {
        VBox card = new VBox(18);
        card.getStyleClass().add("loading-skeleton-card");
        card.setPadding(new Insets(height <= 100 ? 12 : 20));
        card.setMinSize(0, height);
        card.setPrefSize(width, height);
        card.setMaxWidth(width);
        card.setMouseTransparent(true);
        card.setAccessibleRole(AccessibleRole.TEXT);
        card.setAccessibleText("Loading project");
        if (banner) card.getChildren().add(block(Double.MAX_VALUE, Math.max(48, height * .30)));
        VBox copy = text();
        HBox.setHgrow(copy, Priority.ALWAYS);
        HBox heading = new HBox(14, block(44, 44), copy);
        card.getChildren().add(heading);
        if (height > 200) card.getChildren().addAll(block(Double.MAX_VALUE, 10), block(width * .6, 10));
        return card;
    }
}
