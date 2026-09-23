package net.modtale.launcher.ui.shell;

import net.modtale.launcher.ui.common.LauncherTooltips;

import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import net.modtale.launcher.i18n.LauncherI18n;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.common.LauncherLayout;

public final class LauncherNavbar {
    public static HBox build(Button brand, List<Button> navigation, Button notifications, Button account) {
        HBox bar = new HBox(8);
        bar.getStyleClass().add("navbar");
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(LauncherLayout.NAVBAR_INSETS);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        bar.getChildren().addAll(brand, spacer);
        bar.getChildren().addAll(navigation);
        Region separator = new Region();
        separator.getStyleClass().add("nav-divider");
        bar.getChildren().addAll(separator, notifications, account);
        return bar;
    }

    public static Button brand(Runnable action) {
        javafx.scene.Node logo = LauncherVectorLogo.create();
        Button brand = new Button(null, logo);
        brand.getStyleClass().add("brand");
        brand.setMinWidth(142);
        brand.setAlignment(Pos.CENTER_LEFT);
        brand.setMnemonicParsing(false);
        brand.accessibleTextProperty().bind(LauncherI18n.get().binding("nav.goToPlay"));
        brand.setTooltip(LauncherI18n.get().tooltip("nav.goToPlay"));
        brand.setOnAction(event -> action.run());
        return brand;
    }

    public static Button navigation(String key, LauncherIcons.Glyph icon, Runnable action) {
        Button button = new Button();
        LauncherI18n.get().bind(button, key);
        LauncherTooltips.install(button, button.textProperty());
        button.getStyleClass().add("nav-btn");
        button.setFont(Font.font("Inter", FontWeight.BOLD, 14));
        button.setGraphic(LauncherIcons.icon(icon, 16));
        button.setOnAction(event -> action.run());
        return button;
    }
    private LauncherNavbar() {}
}
