package net.modtale.launcher.ui.library;

import static net.modtale.launcher.ui.common.LauncherUi.primaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.secondaryButton;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.common.LauncherView;

final class LibraryShellView {

    private final VBox projectList;
    private final VBox projectDetail;
    private final Runnable refresh;
    private final Runnable checkUpdates;

    LibraryShellView(
            VBox projectList,
            VBox projectDetail,
            Runnable refresh,
            Runnable checkUpdates
    ) {
        this.projectList = projectList;
        this.projectDetail = projectDetail;
        this.refresh = refresh;
        this.checkUpdates = checkUpdates;
    }

    Node build() {
        VBox root = new VBox(18);
        root.setUserData(LauncherView.LIBRARY);
        root.getStyleClass().addAll("view", "library-view");
        root.getChildren().addAll(header(), content());
        return root;
    }

    private Node header() {
        HBox header = new HBox(16);
        header.getStyleClass().add("library-header");
        VBox copy = new VBox(5);
        Label title = new Label("World Libraries");
        title.getStyleClass().add("library-title");
        copy.getChildren().add(title);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button refreshButton = secondaryButton("Refresh");
        refreshButton.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.REFRESH_CW, 15));
        refreshButton.setOnAction(event -> refresh.run());
        Button updatesButton = primaryButton("Check Updates");
        updatesButton.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.REFRESH_CW, 15));
        updatesButton.setOnAction(event -> checkUpdates.run());
        header.getChildren().addAll(copy, spacer, refreshButton, updatesButton);
        return header;
    }

    private Node content() {
        HBox content = new HBox(18);
        content.getStyleClass().add("library-content");
        VBox projectsPane = new VBox(14);
        projectsPane.getStyleClass().add("library-projects-pane");
        projectsPane.getChildren().add(projectList);
        VBox.setVgrow(projectList, Priority.ALWAYS);

        projectDetail.getStyleClass().add("library-detail-pane");
        HBox.setHgrow(projectDetail, Priority.ALWAYS);
        content.getChildren().addAll(projectsPane, projectDetail);
        return content;
    }
}
