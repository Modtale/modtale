package net.modtale.launcher.ui.library;

import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import net.modtale.launcher.ui.common.LauncherView;

final class LibraryShellView {

    private final VBox projectList;
    private final VBox projectDetail;

    LibraryShellView(
            VBox projectList,
            VBox projectDetail
    ) {
        this.projectList = projectList;
        this.projectDetail = projectDetail;
    }

    Node build() {
        VBox root = new VBox();
        root.setUserData(LauncherView.LIBRARY);
        root.getStyleClass().addAll("view", "library-view");
        root.getChildren().add(content());
        return root;
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
