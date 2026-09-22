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
import javafx.scene.image.ImageView;
import javafx.scene.shape.Rectangle;
import net.modtale.launcher.ui.common.CachedImageLoader;
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
        show(host, files, Map.of(), share);
    }

    static void show(StackPane host, List<ConfigFile> files, Map<String, String> modTitles, Consumer<List<ConfigFile>> share) {
        show(host, files, modTitles, Map.of(), null, share);
    }

    static void show(StackPane host, List<ConfigFile> files, Map<String, String> modTitles, Map<String, String> modIcons, CachedImageLoader imageLoader, Consumer<List<ConfigFile>> share) {
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

        VBox rows = new VBox(8);
        Map<ConfigFile, LibraryToggleBox> choices = new LinkedHashMap<>();
        Map<String, List<ConfigFile>> groups = new LinkedHashMap<>();
        files.stream().sorted(java.util.Comparator.comparing(file -> modTitle(file, modTitles), String.CASE_INSENSITIVE_ORDER))
                .forEach(file -> groups.computeIfAbsent(modTitle(file, modTitles), ignored -> new java.util.ArrayList<>()).add(file));
        for (var group : groups.entrySet()) {
            VBox configRows = new VBox(6);
            configRows.getStyleClass().add("share-config-files");
            configRows.setVisible(false);
            configRows.setManaged(false);
            Label count = new Label();
            count.getStyleClass().add("share-config-path");
            Runnable updateCount = () -> {
                long selected = group.getValue().stream().filter(file -> choices.containsKey(file) && choices.get(file).isSelected()).count();
                count.setText(selected > 0 ? selected + " / " + group.getValue().size() + " selected"
                        : group.getValue().size() + (group.getValue().size() == 1 ? " file" : " files"));
            };
            Label title = new Label(group.getKey());
            title.getStyleClass().add("share-config-filename");
            HBox.setHgrow(title, Priority.ALWAYS);
            title.setMaxWidth(Double.MAX_VALUE);
            StackPane icon = new StackPane(LauncherIcons.icon(LauncherIcons.Glyph.SLIDERS, 20));
            icon.getStyleClass().add("share-config-file-icon");
            String iconUrl = modIcons.get(group.getKey());
            if (imageLoader != null && iconUrl != null && !iconUrl.isBlank()) {
                var fallback = icon.getChildren().getFirst();
                ImageView image = new ImageView();
                image.setFitWidth(36);
                image.setFitHeight(36);
                Rectangle clip = new Rectangle(36, 36);
                clip.setArcWidth(14);
                clip.setArcHeight(14);
                image.setClip(clip);
                image.setSmooth(true);
                icon.getChildren().add(image);
                imageLoader.loadInto(image, iconUrl, 108, 108, true);
                CachedImageLoader.showFallbackUntilLoaded(fallback, image);
            }
            StackPane arrow = new StackPane(LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_RIGHT, 16));
            HBox heading = new HBox(14, icon, title, count, arrow);
            heading.setAlignment(Pos.CENTER_LEFT);
            heading.setMaxWidth(Double.MAX_VALUE);
            Button expand = new Button(null, heading);
            expand.setMaxWidth(Double.MAX_VALUE);
            heading.prefWidthProperty().bind(expand.widthProperty().subtract(32));
            expand.getStyleClass().add("share-config-group-toggle");
            expand.setAccessibleText("Show configs for " + group.getKey());
            expand.setOnAction(event -> {
                boolean open = !configRows.isVisible();
                configRows.setVisible(open);
                configRows.setManaged(open);
                arrow.getChildren().setAll(LauncherIcons.icon(open ? LauncherIcons.Glyph.CHEVRON_DOWN : LauncherIcons.Glyph.CHEVRON_RIGHT, 16));
                expand.setAccessibleText((open ? "Hide" : "Show") + " configs for " + group.getKey());
            });
            group.getValue().sort(java.util.Comparator.comparing(file -> file.path().toString(), String.CASE_INSENSITIVE_ORDER));
            for (ConfigFile file : group.getValue()) {
                LibraryToggleBox choice = new LibraryToggleBox();
                choice.setAccessibleText("Include " + file.label());
                choices.put(file, choice);
                Label name = new Label(file.path().getFileName().toString());
                name.getStyleClass().add("share-config-filename");
                name.setWrapText(true);
                VBox copy = new VBox(3, name);
                var relative = file.root().relativize(file.path());
                if (relative.getNameCount() > 2 || group.getValue().stream().filter(other -> other.path().getFileName().equals(file.path().getFileName())).count() > 1) {
                    Label path = new Label(relative.toString());
                    path.getStyleClass().add("share-config-path");
                    path.setWrapText(true);
                    copy.getChildren().add(path);
                }
                Tooltip.install(copy, new Tooltip(file.label()));
                HBox.setHgrow(copy, Priority.ALWAYS);
                HBox row = new HBox(14, choice, copy);
                row.setAlignment(Pos.CENTER_LEFT);
                row.getStyleClass().add("share-config-row");
                choice.selectedProperty().addListener((obs, old, selected) -> {
                    row.pseudoClassStateChanged(PseudoClass.getPseudoClass("selected"), selected);
                    updateCount.run();
                });
                row.setOnMouseClicked(event -> { choice.fire(); event.consume(); });
                configRows.getChildren().add(row);
            }
            updateCount.run();
            VBox card = new VBox(expand, configRows);
            card.getStyleClass().add("share-config-group");
            rows.getChildren().add(card);
        }
        if (files.isEmpty()) {
            Label empty = new Label("No mod settings found. Your list will include mods only.");
            empty.setWrapText(true);
            empty.getStyleClass().add("share-config-description");
            rows.getChildren().add(empty);
        }
        VBox body = new VBox(rows);
        body.getStyleClass().add("share-config-body");
        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("post-download-modal-scroll");
        scroll.setPrefViewportHeight(Math.min(460, 48 + Math.max(1, groups.size()) * 82));
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
    static String modTitle(ConfigFile file, Map<String, String> modTitles) {
        String title = modTitles.get(file.pluginId());
        if (title != null && !title.isBlank()) return title;
        String folder = file.root().relativize(file.path()).getName(0).toString();
        var matches = modTitles.entrySet().stream()
                .filter(entry -> entry.getKey().replace(':', '_').equalsIgnoreCase(folder)
                        || entry.getValue().equalsIgnoreCase(folder))
                .map(Map.Entry::getValue).distinct().toList();
        if (matches.size() == 1) return matches.getFirst();
        if (folder.equals("Hytale_HytaleGenerator")) return "World Generation";
        if (folder.equals("Hytale_Shop")) return "Shops";
        String name = folder.contains("_") ? folder.substring(folder.indexOf('_') + 1) : folder;
        name = name.replaceAll("([a-z0-9])([A-Z])", "$1 $2").replaceAll("[_-]+", " ").trim();
        return name.isBlank() ? "Mod settings" : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

}
