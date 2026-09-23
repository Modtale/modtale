package net.modtale.launcher.ui.shell;

import static net.modtale.launcher.ui.common.LauncherUi.pseudo;

import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.event.EventTarget;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import net.modtale.launcher.i18n.LauncherI18n;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.common.LauncherOverlaySupport;
import net.modtale.launcher.ui.common.LauncherView;
import net.modtale.launcher.ui.wardrobe.LauncherWardrobeController;

/** Navbar shortcut to the wardrobe's existing tabs. */
public final class LauncherWardrobeMenu {
    private static final double MENU_WIDTH = 220;
    private static final double SCREEN_MARGIN = 8;
    private static final double TOP_OFFSET = 8;

    private final Supplier<StackPane> sceneLayer;
    private final Supplier<LauncherView> currentView;
    private final Supplier<LauncherWardrobeController> controller;
    private final Consumer<LauncherView> showView;
    private Button button;
    private VBox panel;

    public LauncherWardrobeMenu(Supplier<StackPane> sceneLayer, Supplier<LauncherView> currentView,
            Supplier<LauncherWardrobeController> controller, Consumer<LauncherView> showView) {
        this.sceneLayer = sceneLayer;
        this.currentView = currentView;
        this.controller = controller;
        this.showView = showView;
    }

    public Button button() {
        if (button == null) build();
        return button;
    }

    public VBox panel() {
        button();
        return panel;
    }

    public void hide() {
        if (panel != null) panel.setVisible(false);
        if (button != null) pseudo(button, "open", false);
    }

    public void updateSelected(LauncherView view) {
        if (button != null) pseudo(button, "selected", view == LauncherView.WARDROBE);
    }

    public void hideOnOutsidePress(EventTarget target) {
        if (panel != null && panel.isVisible()
                && !LauncherOverlaySupport.eventTargetInside(target, panel)
                && !LauncherOverlaySupport.eventTargetInside(target, button)) hide();
    }

    private void build() {
        button = new Button();
        button.getStyleClass().addAll("nav-btn", "browse-nav-button");
        button.setMnemonicParsing(false);
        Node icon = LauncherIcons.icon(LauncherIcons.Glyph.PALETTE, 16);
        icon.getStyleClass().add("browse-nav-leading-icon");
        Label title = new Label();
        LauncherI18n.get().bind(title, "nav.wardrobe");
        title.getStyleClass().add("browse-nav-button-label");
        title.setFont(Font.font("Inter", FontWeight.BOLD, 14));
        Node chevron = LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_DOWN, 14);
        chevron.getStyleClass().add("browse-nav-chevron");
        HBox label = new HBox(4, title, chevron);
        label.setAlignment(Pos.CENTER);
        HBox content = new HBox(8, icon, label);
        content.getStyleClass().add("browse-nav-button-content");
        content.setAlignment(Pos.CENTER);
        button.setGraphic(content);

        panel = new VBox();
        panel.getStyleClass().add("browse-dropdown-panel");
        panel.setMinWidth(MENU_WIDTH);
        panel.setPrefWidth(MENU_WIDTH);
        panel.setMaxWidth(MENU_WIDTH);
        panel.setVisible(false);
        panel.setManaged(false);
        panel.getChildren().addAll(
                item("Customize", LauncherIcons.Glyph.PALETTE, LauncherWardrobeController.Tab.CUSTOMIZE),
                item("Popular skins", LauncherIcons.Glyph.GRID, LauncherWardrobeController.Tab.POPULAR),
                item("Saved looks", LauncherIcons.Glyph.HEART, LauncherWardrobeController.Tab.SAVED));
        button.setOnAction(event -> {
            if (panel.isVisible()) hide();
            else show();
        });
    }

    private Button item(String name, LauncherIcons.Glyph icon, LauncherWardrobeController.Tab tab) {
        Button item = new Button(name, LauncherIcons.icon(icon, 16));
        item.getStyleClass().add("browse-dropdown-item");
        item.setAlignment(Pos.CENTER_LEFT);
        item.setMaxWidth(Double.MAX_VALUE);
        item.setOnAction(event -> {
            hide();
            showView.accept(LauncherView.WARDROBE);
            LauncherWardrobeController wardrobe = controller.get();
            if (wardrobe != null && currentView.get() == LauncherView.WARDROBE) wardrobe.showTab(tab);
        });
        return item;
    }

    private void show() {
        if (sceneLayer.get() == null) return;
        panel.applyCss();
        panel.autosize();
        position();
        panel.setVisible(true);
        panel.toFront();
        pseudo(button, "open", true);
        Platform.runLater(this::position);
    }

    private void position() {
        StackPane layer = sceneLayer.get();
        if (layer == null || button.getScene() == null) return;
        Bounds bounds = button.localToScene(button.getBoundsInLocal());
        if (bounds == null) return;
        double width = panel.getLayoutBounds().getWidth() > 0 ? panel.getLayoutBounds().getWidth() : MENU_WIDTH;
        double maxX = Math.max(SCREEN_MARGIN, layer.getWidth() - width - SCREEN_MARGIN);
        panel.relocate(LauncherOverlaySupport.clamp(bounds.getMaxX() - width, SCREEN_MARGIN, maxX),
                bounds.getMaxY() + TOP_OFFSET);
    }
}
