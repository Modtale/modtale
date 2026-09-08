package net.modtale.launcher.ui.shell;

import static net.modtale.launcher.ui.common.LauncherUi.primaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.secondaryButton;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import net.modtale.launcher.ui.browse.ProjectBrowseController;
import net.modtale.launcher.ui.common.LauncherView;
import net.modtale.launcher.ui.library.LauncherLibraryController;
import net.modtale.launcher.ui.play.LauncherPlayController;
import net.modtale.launcher.ui.settings.LauncherSettingsController;

public final class LauncherToolbarActions {

    private final ProjectBrowseController browseController;
    private final LauncherPlayController playController;
    private final LauncherLibraryController libraryController;
    private final LauncherSettingsController settingsController;
    private HBox actions;

    public LauncherToolbarActions(
            ProjectBrowseController browseController,
            LauncherPlayController playController,
            LauncherLibraryController libraryController,
            LauncherSettingsController settingsController
    ) {
        this.browseController = browseController;
        this.playController = playController;
        this.libraryController = libraryController;
        this.settingsController = settingsController;
    }

    public Node view() {
        if (actions == null) {
            actions = buildActions();
        }
        return actions;
    }

    public void update(LauncherView view) {
        if (actions == null) {
            return;
        }
        for (Node node : actions.getChildren()) {
            node.setVisible(false);
        }
        switch (view) {
            case DISCOVER -> actions.getChildren().get(0).setVisible(true);
            case PLAY -> actions.getChildren().get(1).setVisible(true);
            case LIBRARY, UPDATES -> actions.getChildren().get(2).setVisible(true);
            case SETTINGS -> actions.getChildren().get(3).setVisible(true);
            case NOTIFICATIONS, PROJECT -> {
            }
        }
    }

    private HBox buildActions() {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_RIGHT);
        row.getStyleClass().add("toolbar-actions");

        Button discover = secondaryButton("Search");
        discover.setOnAction(event -> browseController.searchProjects());
        Button launch = primaryButton("Launch");
        launch.setOnAction(event -> playController.launchHytale());
        Button check = primaryButton("Check Updates");
        check.setOnAction(event -> libraryController.checkUpdates());
        Button save = primaryButton("Save Settings");
        save.setOnAction(event -> settingsController.saveFromFields(true));

        row.getChildren().addAll(discover, launch, check, save);
        row.getChildren().forEach(node -> node.managedProperty().bind(node.visibleProperty()));
        return row;
    }
}
