package net.modtale.launcher.ui.shell;

import static net.modtale.launcher.ui.common.LauncherUi.pseudo;

import java.util.List;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.event.EventTarget;
import javafx.geometry.BoundingBox;
import javafx.geometry.Bounds;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import net.modtale.launcher.ui.browse.ProjectBrowseController;
import net.modtale.launcher.ui.browse.controls.BrowseOptions;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.common.LauncherOverlaySupport;
import net.modtale.launcher.ui.common.LauncherView;

public final class LauncherBrowseMenu {

    private static final double BROWSE_NAV_BUTTON_HEIGHT = 38;
    private static final double BROWSE_NAV_BUTTON_HORIZONTAL_PADDING = 12;
    private static final double BROWSE_MENU_ESTIMATED_WIDTH = 256;
    private static final double BROWSE_MENU_TOP_OFFSET = 8;
    private static final double SCREEN_MARGIN = 8;
    private static final double NAVBAR_TEXT_FONT_SIZE = 14;

    private final ProjectBrowseController browseController;
    private final Supplier<StackPane> sceneLayer;
    private final Supplier<LauncherView> currentView;

    private Button menuButton;
    private Node visualAnchor;
    private VBox dropdownPanel;

    public LauncherBrowseMenu(
            ProjectBrowseController browseController,
            Supplier<StackPane> sceneLayer,
            Supplier<LauncherView> currentView
    ) {
        this.browseController = browseController;
        this.sceneLayer = sceneLayer;
        this.currentView = currentView;
    }

    public Button button() {
        if (menuButton == null) {
            menuButton = buildButton();
        }
        return menuButton;
    }

    public VBox panel() {
        button();
        return dropdownPanel;
    }

    public void hide() {
        if (dropdownPanel != null) {
            dropdownPanel.setVisible(false);
        }
        if (menuButton != null) {
            pseudo(menuButton, "open", false);
            updateSelected();
        }
    }

    public void updateSelected() {
        if (menuButton == null) {
            return;
        }
        boolean browseView = currentView.get() == LauncherView.DISCOVER
                || currentView.get() == LauncherView.PROJECT;
        pseudo(menuButton, "selected", browseView);
    }

    public void updateSelected(LauncherView currentView) {
        if (menuButton == null) {
            return;
        }
        boolean browseView = currentView == LauncherView.DISCOVER
                || currentView == LauncherView.PROJECT;
        pseudo(menuButton, "selected", browseView);
    }

    public void hideOnOutsidePress(EventTarget target) {
        if (dropdownPanel == null || !dropdownPanel.isVisible()) {
            return;
        }
        if (!LauncherOverlaySupport.eventTargetInside(target, dropdownPanel)
                && !LauncherOverlaySupport.eventTargetInside(target, menuButton)) {
            hide();
        }
    }

    private Button buildButton() {
        Button button = new Button();
        button.getStyleClass().addAll("nav-btn", "browse-nav-button");
        button.setMnemonicParsing(false);

        Node leadingIcon = LauncherIcons.icon(LauncherIcons.Glyph.GRID, 16);
        leadingIcon.getStyleClass().add("browse-nav-leading-icon");
        Label label = new Label("Browse");
        label.getStyleClass().add("browse-nav-button-label");
        applyNavbarTitleFont(label);
        Node chevron = LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_DOWN, 14);
        chevron.getStyleClass().add("browse-nav-chevron");
        HBox labelCluster = new HBox(4, label, chevron);
        labelCluster.setAlignment(Pos.CENTER);
        HBox content = new HBox(8, leadingIcon, labelCluster);
        content.getStyleClass().add("browse-nav-button-content");
        content.setAlignment(Pos.CENTER);
        button.setGraphic(content);
        visualAnchor = content;

        dropdownPanel = buildPanel();
        button.setOnAction(event -> toggle());
        return button;
    }

    private VBox buildPanel() {
        VBox menuPanel = new VBox();
        menuPanel.getStyleClass().add("browse-dropdown-panel");
        menuPanel.setMinWidth(BROWSE_MENU_ESTIMATED_WIDTH);
        menuPanel.setPrefWidth(BROWSE_MENU_ESTIMATED_WIDTH);
        menuPanel.setMaxWidth(BROWSE_MENU_ESTIMATED_WIDTH);
        menuPanel.setVisible(false);
        menuPanel.setManaged(false);
        menuPanel.setMouseTransparent(false);

        List<BrowseOptions.ClassificationOption> order = List.of(
                BrowseOptions.ClassificationOption.ALL,
                BrowseOptions.ClassificationOption.MODPACKS,
                BrowseOptions.ClassificationOption.PLUGINS,
                BrowseOptions.ClassificationOption.WORLDS,
                BrowseOptions.ClassificationOption.ART,
                BrowseOptions.ClassificationOption.DATA
        );
        for (int i = 0; i < order.size(); i++) {
            BrowseOptions.ClassificationOption option = order.get(i);
            menuPanel.getChildren().add(menuItem(option));
            if (i == 0) {
                Region separator = new Region();
                separator.getStyleClass().add("browse-dropdown-separator");
                menuPanel.getChildren().add(separator);
            }
        }
        return menuPanel;
    }

    private Button menuItem(BrowseOptions.ClassificationOption option) {
        Button item = new Button(navBrowseLabel(option));
        item.getStyleClass().add("browse-dropdown-item");
        item.setGraphic(LauncherIcons.icon(option.icon(), 16));
        item.setAlignment(Pos.CENTER_LEFT);
        item.setMaxWidth(Double.MAX_VALUE);
        item.setOnAction(event -> {
            hide();
            browseController.selectClassification(option);
        });
        return item;
    }

    private void toggle() {
        if (dropdownPanel.isVisible()) {
            hide();
            return;
        }
        show();
    }

    private void show() {
        StackPane layer = sceneLayer.get();
        if (layer == null || dropdownPanel == null || menuButton == null || visualAnchor == null) {
            return;
        }
        dropdownPanel.applyCss();
        dropdownPanel.autosize();
        position();
        dropdownPanel.setVisible(true);
        dropdownPanel.toFront();
        pseudo(menuButton, "open", true);
        updateSelected();
        Platform.runLater(this::position);
    }

    private void position() {
        StackPane layer = sceneLayer.get();
        if (layer == null || dropdownPanel == null || menuButton == null || visualAnchor == null) {
            return;
        }
        Bounds anchorBounds = anchorBounds(menuButton, visualAnchor);
        if (anchorBounds == null) {
            return;
        }
        double width = dropdownPanel.getLayoutBounds().getWidth() > 0
                ? dropdownPanel.getLayoutBounds().getWidth()
                : BROWSE_MENU_ESTIMATED_WIDTH;
        double maxX = Math.max(SCREEN_MARGIN, layer.getWidth() - width - SCREEN_MARGIN);
        double x = LauncherOverlaySupport.clamp(anchorBounds.getMaxX() - width, SCREEN_MARGIN, maxX);
        double y = anchorBounds.getMaxY() + BROWSE_MENU_TOP_OFFSET;
        dropdownPanel.relocate(x, y);
    }

    private static Bounds anchorBounds(Button owner, Node visualAnchor) {
        Bounds contentBounds = visualAnchor.localToScene(visualAnchor.getBoundsInLocal());
        if (contentBounds == null) {
            return owner.localToScene(owner.getBoundsInLocal());
        }
        double width = contentBounds.getWidth() + (BROWSE_NAV_BUTTON_HORIZONTAL_PADDING * 2);
        double centerY = contentBounds.getMinY() + (contentBounds.getHeight() / 2.0);
        return new BoundingBox(
                contentBounds.getMinX() - BROWSE_NAV_BUTTON_HORIZONTAL_PADDING,
                centerY - (BROWSE_NAV_BUTTON_HEIGHT / 2.0),
                width,
                BROWSE_NAV_BUTTON_HEIGHT
        );
    }

    private static void applyNavbarTitleFont(Label text) {
        text.setFont(Font.font("Inter", FontWeight.EXTRA_BOLD, NAVBAR_TEXT_FONT_SIZE));
    }

    private static String navBrowseLabel(BrowseOptions.ClassificationOption option) {
        return option.browseMenuLabel();
    }
}
