package net.modtale.launcher.ui.library;

import static net.modtale.launcher.ui.common.LauncherUi.classificationLabel;
import static net.modtale.launcher.ui.common.LauncherUi.primaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.pseudo;
import static net.modtale.launcher.ui.common.LauncherUi.setVisibleManaged;
import static net.modtale.launcher.ui.common.LauncherUi.value;

import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.modtale.launcher.model.install.InstalledProject;
import net.modtale.launcher.model.install.UpdateCandidate;
import net.modtale.launcher.model.project.ProjectClassification;
import net.modtale.launcher.ui.common.LauncherIcons;

final class LibraryProjectListRenderer {

    private final Consumer<String> selectProject;
    private final Consumer<UpdateCandidate> updateProject;

    LibraryProjectListRenderer(Consumer<String> selectProject, Consumer<UpdateCandidate> updateProject) {
        this.selectProject = selectProject;
        this.updateProject = updateProject;
    }

    Button projectRow(InstalledProject project, boolean selected, boolean hasUpdate) {
        Button button = new Button();
        button.getStyleClass().add("library-project-row");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        button.setGraphic(projectRowContent(project, hasUpdate));
        button.setOnAction(event -> selectProject.accept(project.projectId()));
        pseudo(button, "selected", selected);
        pseudo(button, "update", hasUpdate);
        return button;
    }

    Node updateRow(UpdateCandidate update) {
        HBox row = net.modtale.launcher.ui.common.LauncherUi.rowCard(update.title(),
                classificationLabel(update.installedProject().classification()) + " - "
                        + value(update.currentVersion(), "current") + " -> " + value(update.newestVersionNumber(), "latest"));
        Button updateButton = primaryButton("Update");
        updateButton.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.DOWNLOAD, 14));
        updateButton.setOnAction(event -> updateProject.accept(update));
        row.getChildren().add(updateButton);
        return row;
    }

    private Node projectRowContent(InstalledProject project, boolean hasUpdate) {
        HBox row = new HBox(12);
        row.getStyleClass().add("library-project-row-content");
        StackPane icon = projectIcon(project.classification(), 18);
        icon.getStyleClass().add("library-project-icon");
        VBox copy = new VBox(4);
        Label title = new Label(value(project.title(), "Untitled Project"));
        title.getStyleClass().add("library-project-title");
        Label meta = new Label(LibraryProjectSupport.projectRowMeta(project));
        meta.getStyleClass().add("library-project-meta");
        copy.getChildren().addAll(title, meta);
        HBox.setHgrow(copy, Priority.ALWAYS);
        VBox status = new VBox(5);
        status.setAlignment(Pos.CENTER_RIGHT);
        Label version = new Label(value(project.installedVersion(), "installed"));
        version.getStyleClass().add("library-version-pill");
        Label update = new Label(hasUpdate ? "Update" : "");
        update.getStyleClass().add("library-update-pill");
        setVisibleManaged(update, hasUpdate);
        status.getChildren().addAll(version, update);
        row.getChildren().addAll(icon, copy, status);
        return row;
    }

    private StackPane projectIcon(String classification, double size) {
        LauncherIcons.Glyph glyph = ProjectClassification.isModpack(classification)
                ? LauncherIcons.Glyph.LAYERS
                : LauncherIcons.Glyph.BOX;
        return new StackPane(LauncherIcons.icon(glyph, size));
    }
}
