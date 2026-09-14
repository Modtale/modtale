package net.modtale.launcher.ui.library;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.modtale.launcher.config.HytaleConfigFiles.ConfigFile;
import net.modtale.launcher.ui.common.LauncherIcons;
import static net.modtale.launcher.ui.common.LauncherUi.primaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.secondaryButton;

final class ShareConfigSelectionModal {
    static void show(StackPane host, List<ConfigFile> files, Consumer<List<ConfigFile>> share) {
        // A keyboard activation must not stack dialogs while focus is moving into the overlay.
        if (host.lookup(".share-config-overlay") != null) return;
        StackPane overlay = new StackPane();
        overlay.getStyleClass().addAll("post-download-modal-overlay", "share-config-overlay");
        overlay.setPadding(new Insets(24));
        Runnable dismiss = () -> host.getChildren().remove(overlay);

        HBox identity = new HBox(10, LauncherIcons.icon(LauncherIcons.Glyph.SLIDERS, 22), new Label("Share mod settings"));
        identity.setAlignment(Pos.CENTER_LEFT);
        identity.getStyleClass().add("post-download-modal-title");
        HBox.setHgrow(identity, Priority.ALWAYS);
        Button close = new Button(null, LauncherIcons.icon(LauncherIcons.Glyph.X, 18));
        close.getStyleClass().add("post-download-modal-close");
        close.setAccessibleText("Close shared list settings");
        close.setOnAction(event -> dismiss.run());
        HBox header = new HBox(16, identity, close);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("post-download-modal-header");

        Label hint = new Label("Include settings with your mod list, or leave them out to share just the mods.");
        hint.setWrapText(true);
        hint.getStyleClass().add("share-config-description");
        VBox rows = new VBox(8);
        Map<ConfigFile, LibraryToggleBox> choices = new LinkedHashMap<>();
        Map<String, List<ConfigFile>> groups = files.stream().collect(java.util.stream.Collectors.groupingBy(
                file -> file.label().split(" / ", 2)[0], LinkedHashMap::new, java.util.stream.Collectors.toList()));
        groups.forEach((scope, group) -> {
            Label heading = new Label(scope);
            heading.getStyleClass().add("share-config-section");
            rows.getChildren().add(heading);
            for (ConfigFile file : group) {
                LibraryToggleBox choice = new LibraryToggleBox();
                choice.setAccessibleText("Include " + file.label());
                choices.put(file, choice);
                Label name = new Label(file.path().getFileName().toString());
                name.getStyleClass().add("share-config-filename");
                String relative = file.root().relativize(file.path()).toString().replace('\\', '/');
                String folder = relative.contains("/") ? relative.substring(0, relative.lastIndexOf('/')) : scope;
                Label path = new Label(folder);
                path.getStyleClass().add("share-config-path");
                path.setWrapText(true);
                Tooltip.install(path, new Tooltip(file.label()));
                VBox copy = new VBox(4, name, path);
                copy.setMinWidth(0);
                HBox.setHgrow(copy, Priority.ALWAYS);
                StackPane icon = new StackPane(LauncherIcons.icon(LauncherIcons.Glyph.SLIDERS, 20));
                icon.getStyleClass().add("share-config-file-icon");
                HBox row = new HBox(14, choice, icon, copy);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("share-config-row");
                choice.selectedProperty().addListener((obs, old, selected) ->
                        row.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), selected));
                row.setOnMouseClicked(event -> { choice.fire(); event.consume(); });
                rows.getChildren().add(row);
            }
        });
        if (files.isEmpty()) {
            Label empty = new Label("No mod settings found. Your list will include mods only.");
            empty.setWrapText(true);
            empty.getStyleClass().add("share-config-description");
            rows.getChildren().add(empty);
        }
        Label note = new Label("Included files are public. Unmatched mod folders keep their original paths.");
        note.setWrapText(true);
        note.getStyleClass().add("share-config-path");
        VBox body = new VBox(16, hint, rows, note);
        body.getStyleClass().add("share-config-body");
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("post-download-modal-scroll");
        scroll.setPrefViewportHeight(Math.min(460, 130 + files.size() * 82 + groups.size() * 28));
        scroll.setMinHeight(0);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Button cancel = secondaryButton("Cancel");
        cancel.setOnAction(event -> dismiss.run());
        Button submit = primaryButton("Create shared list");
        submit.setOnAction(event -> {
            List<ConfigFile> selected = choices.entrySet().stream().filter(entry -> entry.getValue().isSelected())
                    .map(Map.Entry::getKey).toList();
            dismiss.run();
            share.accept(selected);
        });
        HBox footer = new HBox(12, cancel, submit);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.getStyleClass().add("mod-settings-footer");
        VBox modal = new VBox(header, scroll, footer);
        modal.getStyleClass().addAll("post-download-modal", "share-config-modal");
        modal.setMaxWidth(760);
        modal.setMaxHeight(Region.USE_PREF_SIZE);
        modal.setMinSize(0, 0);
        overlay.getChildren().add(modal);
        overlay.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) { dismiss.run(); event.consume(); }
        });
        host.getChildren().add(overlay);
        Platform.runLater(close::requestFocus);
    }
}
