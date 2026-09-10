package net.modtale.launcher.ui.library;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.beans.binding.Bindings;
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
        Label title = new Label("Worlds");
        title.getStyleClass().add("library-navigation-title");
        Label count = new Label();
        count.textProperty().bind(Bindings.createStringBinding(
                () -> Long.toString(projectList.getChildren().stream()
                        .filter(node -> node.getStyleClass().contains("library-world-tab")).count()),
                projectList.getChildren()));
        count.getStyleClass().add("library-count-pill");
        HBox header = new HBox(10, title, count);
        header.getStyleClass().add("library-pane-header");
        projectsPane.getChildren().addAll(header, projectList);
        VBox.setVgrow(projectList, Priority.ALWAYS);

        projectDetail.getStyleClass().add("library-detail-pane");
        projectDetail.setMinWidth(0);
        HBox.setHgrow(projectDetail, Priority.ALWAYS);
        content.getChildren().addAll(projectsPane, projectDetail);
        return content;
    }

}
