package net.modtale.launcher.ui.library;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.modtale.launcher.config.HytaleConfigFiles.ConfigFile;
import static net.modtale.launcher.ui.common.LauncherUi.primaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.secondaryButton;

final class ShareConfigSelectionModal {
    static void show(StackPane host, List<ConfigFile> files, Consumer<List<ConfigFile>> share) {
        StackPane overlay = new StackPane();
        overlay.getStyleClass().add("post-download-modal-overlay");
        overlay.setPadding(new Insets(24));
        Label title = new Label("Include configs in this list");
        title.getStyleClass().add("config-editor-title");
        Label hint = new Label("Selected files will be public with the shared list. Choose only the mod settings you want to share. Leave everything unchecked to share mods only. Unattributed folders could not be matched to a mod; their original paths are preserved.");
        hint.setWrapText(true);
        Map<ConfigFile, CheckBox> choices = new LinkedHashMap<>();
        VBox rows = new VBox(10);
        for (ConfigFile file : files) {
            CheckBox choice = new CheckBox(file.label());
            choice.setStyle("-fx-text-fill: #cbd5e1;");
            choices.put(file, choice);
            rows.getChildren().add(choice);
        }
        if (files.isEmpty()) rows.getChildren().add(new Label("No mod config files found."));
        ScrollPane scroll = new ScrollPane(rows);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("post-download-modal-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        Button cancel = secondaryButton("Cancel");
        cancel.setOnAction(event -> host.getChildren().remove(overlay));
        Button submit = primaryButton("Create shared list");
        submit.setOnAction(event -> {
            List<ConfigFile> selected = choices.entrySet().stream().filter(entry -> entry.getValue().isSelected())
                    .map(Map.Entry::getKey).toList();
            host.getChildren().remove(overlay);
            share.accept(selected);
        });
        VBox modal = new VBox(16, title, hint, scroll, new HBox(8, cancel, submit));
        modal.getStyleClass().addAll("post-download-modal", "config-editor-modal");
        modal.setPadding(new Insets(24));
        modal.setMaxSize(760, 600);
        overlay.getChildren().add(modal);
        host.getChildren().add(overlay);
    }
}
