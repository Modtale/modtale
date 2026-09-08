package net.modtale.launcher.ui.library;

import static net.modtale.launcher.ui.common.LauncherUi.primaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.secondaryButton;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.config.ConfigSettingsDocument;
import net.modtale.launcher.config.ConfigSettingsDocument.Setting;
import net.modtale.launcher.config.HytaleConfigFiles;
import net.modtale.launcher.config.HytaleConfigFiles.ConfigFile;

final class ConfigEditorModal {
    private final HytaleConfigFiles files = new HytaleConfigFiles();
    private final Executor executor;
    private final StackPane host;
    private final Runnable onSaved;
    private final StackPane overlay = new StackPane();
    private final VBox navigation = new VBox(5);
    private final VBox form = new VBox(0);
    private final VBox content = new VBox(18);
    private final TextField search = new TextField();
    private final Label heading = new Label();
    private final Label subtitle = new Label();
    private final Label status = new Label();
    private final Button save = primaryButton("Save changes");
    private final Button reset = secondaryButton("Reset changes");
    private final List<ConfigSettingsDocument> documents = new ArrayList<>();
    private final Set<Setting> invalid = new HashSet<>();
    private final List<Row> rows = new ArrayList<>();
    private String category = "All settings";
    private boolean busy;
    private int unavailable;

    ConfigEditorModal(StackPane host, Executor executor, Runnable onSaved) {
        this.host = host; this.executor = executor; this.onSaved = onSaved;
    }

    void show(Path globalMods, Path world, String name) {
        show(name, () -> files.discover(globalMods, world));
    }
    void show(List<ConfigFile> selectedFiles, String modName) {
        var selected = List.copyOf(selectedFiles);
        show(modName, () -> selected);
    }
    private void show(String name, DiskWork<List<ConfigFile>> discover) {
        Label title = new Label(name);
        HBox identity = new HBox(12, LauncherIcons.icon(LauncherIcons.Glyph.SLIDERS, 22), title);
        identity.setAlignment(Pos.CENTER_LEFT);
        identity.getStyleClass().add("post-download-modal-title");
        HBox.setHgrow(identity, Priority.ALWAYS);
        Button close = secondaryButton("Done");
        close.setOnAction(event -> close());
        HBox header = new HBox(20, identity, close);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("post-download-modal-header");

        search.setPromptText("Search settings…");
        search.setAccessibleText("Search settings");
        search.getStyleClass().add("input");
        search.textProperty().addListener((obs, old, value) -> filter());
        ScrollPane categoryScroll = new ScrollPane(navigation);
        categoryScroll.setFitToWidth(true); categoryScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        categoryScroll.getStyleClass().add("mod-settings-scroll");
        VBox.setVgrow(categoryScroll, Priority.ALWAYS);
        VBox sidebar = new VBox(categoryScroll);
        sidebar.getStyleClass().add("mod-settings-sidebar");
        sidebar.setPrefWidth(205); sidebar.setMinWidth(180); sidebar.setMaxWidth(220);
        heading.getStyleClass().add("settings-section-title");
        subtitle.getStyleClass().add("mod-settings-muted");
        subtitle.setWrapText(true);
        form.getStyleClass().add("mod-settings-card");
        content.getChildren().addAll(new VBox(5, heading, subtitle), form);
        content.getStyleClass().add("mod-settings-content");
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true); scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("mod-settings-scroll");
        VBox right = new VBox(18, search, scroll);
        right.setMinWidth(0); VBox.setVgrow(scroll, Priority.ALWAYS);
        HBox.setHgrow(right, Priority.ALWAYS);
        HBox body = new HBox(24, sidebar, right);
        body.getStyleClass().add("mod-settings-body");
        VBox.setVgrow(body, Priority.ALWAYS);
        reset.setOnAction(event -> {
            documents.forEach(ConfigSettingsDocument::reset); invalid.clear(); buildRows(); filter();
            status.setText("Changes reset."); updateControls();
        });
        save.setOnAction(event -> save());
        status.getStyleClass().add("mod-settings-muted");
        status.setWrapText(true); status.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(status, Priority.ALWAYS);
        HBox footer = new HBox(12, status, reset, save);
        footer.setAlignment(Pos.CENTER_LEFT); footer.getStyleClass().add("mod-settings-footer");
        VBox modal = new VBox(header, body, footer);
        modal.getStyleClass().addAll("post-download-modal", "mod-settings-modal");
        modal.setPrefSize(1040, 720); modal.setMaxSize(1120, 800); modal.setMinSize(0, 0);
        overlay.getStyleClass().add("post-download-modal-overlay");
        overlay.setPadding(new Insets(24)); overlay.getChildren().add(modal);
        overlay.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) { close(); event.consume(); }
            if (event.isShortcutDown() && event.getCode() == KeyCode.S) { save(); event.consume(); }
        });
        host.getChildren().add(overlay);
        work("Loading settings…", () -> {
            var loaded = new ArrayList<ConfigSettingsDocument>();
            for (var file : discover.run()) {
                try {
                    var document = new ConfigSettingsDocument(files.read(file));
                    if (loaded.stream().mapToInt(doc -> doc.settings().size()).sum() + document.settings().size() > 1500)
                        throw new java.io.IOException("Too many settings");
                    loaded.add(document);
                }
                catch (java.io.IOException ex) { unavailable++; }
            }
            return loaded;
        }, loaded -> {
            documents.addAll(loaded); buildRows(); buildNavigation(); filter();
            status.setText(unavailable > 0 ? "Some settings cannot be edited here yet." : "Close Hytale before making changes.");
        }, "Settings couldn't be loaded. Try refreshing the library.");
    }

    private void buildRows() {
        rows.clear(); form.getChildren().clear();
        Map<String, Long> repeatedNames = documents.stream().flatMap(doc -> doc.settings().stream())
                .collect(java.util.stream.Collectors.groupingBy(Setting::name, java.util.stream.Collectors.counting()));
        for (var document : documents) for (Setting setting : document.settings()) {
            Label title = new Label(setting.name());
            title.getStyleClass().add("settings-card-title"); title.setWrapText(true);
            VBox copy = new VBox(5, title); copy.setAlignment(Pos.CENTER_LEFT);
            copy.setMinWidth(0); HBox.setHgrow(copy, Priority.ALWAYS);
            if (repeatedNames.getOrDefault(setting.name(), 0L) > 1) {
                String scope = document.snapshot().file().label().startsWith("Global") ? "All worlds" : "This world";
                Label context = new Label(setting.context().isBlank() ? scope : setting.context() + " · " + scope);
                context.getStyleClass().add("library-muted-text"); context.setWrapText(true);
                copy.getChildren().add(context);
            }
            Node control;
            Label error = new Label(); error.getStyleClass().add("mod-settings-error");
            error.setVisible(false); error.setManaged(false);
            if (setting.toggle()) {
                LibraryToggleBox toggle = new LibraryToggleBox();
                toggle.setSelected(Boolean.parseBoolean(setting.value()));
                toggle.setAccessibleText(setting.name());
                toggle.setTooltip(new Tooltip(setting.name()));
                toggle.setOnAction(() -> {
                    setting.set(Boolean.toString(toggle.isSelected())); changed();
                });
                control = toggle;
            } else {
                TextField input = new TextField(setting.value());
                input.getStyleClass().add("input"); input.setAccessibleText(setting.name());
                input.setPrefWidth(setting.number() ? 145 : 245); input.setMaxWidth(setting.number() ? 145 : 245);
                input.textProperty().addListener((obs, old, value) -> {
                    try {
                        setting.set(value); invalid.remove(setting); error.setVisible(false); error.setManaged(false);
                    } catch (IllegalArgumentException ex) {
                        invalid.add(setting); error.setText(ex.getMessage()); error.setVisible(true); error.setManaged(true);
                    }
                    changed();
                });
                control = input;
            }
            HBox line = new HBox(22, copy, control); line.setAlignment(Pos.CENTER_LEFT);
            VBox row = new VBox(7, line, error); row.getStyleClass().add("mod-settings-row");
            rows.add(new Row(setting, row)); form.getChildren().add(row);
        }
    }
    private void buildNavigation() {
        navigation.getChildren().clear();
        LinkedHashSet<String> categories = new LinkedHashSet<>(); categories.add("All settings");
        rows.stream().map(row -> row.setting.category()).distinct().sorted().forEach(categories::add);
        for (String name : categories) {
            long count = rows.stream().filter(row -> name.equals("All settings") || name.equals(row.setting.category())).count();
            Button button = new Button(name + "   " + count);
            button.setAccessibleText(name); button.setMaxWidth(Double.MAX_VALUE); button.setWrapText(true);
            button.getStyleClass().add("mod-settings-nav");
            if (category.equals(name)) button.getStyleClass().add("active");
            button.setOnAction(event -> { category = name; buildNavigation(); filter(); });
            navigation.getChildren().add(button);
        }
    }
    private void filter() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        int shown = 0;
        for (Row row : rows) {
            boolean matches = (query.isEmpty() ? category.equals("All settings") || category.equals(row.setting.category()) :
                    (row.setting.name() + " " + row.setting.context() + " " + row.setting.category()).toLowerCase(Locale.ROOT).contains(query));
            row.node.setVisible(matches); row.node.setManaged(matches); if (matches) shown++;
        }
        heading.setText(query.isEmpty() ? category : "Search results");
        subtitle.setText(rows.isEmpty() ? "No editable settings are available for this mod yet." : "No matching settings. Try another search.");
        subtitle.setVisible(shown == 0); subtitle.setManaged(shown == 0);
        form.setVisible(shown > 0); form.setManaged(shown > 0);
    }
    private boolean dirty() { return documents.stream().anyMatch(ConfigSettingsDocument::dirty); }
    private void changed() { status.setText(invalid.isEmpty() ? "Unsaved changes" : "Check the highlighted values."); updateControls(); }
    private void updateControls() {
        form.setDisable(busy); search.setDisable(busy); navigation.setDisable(busy);
        save.setDisable(busy || !dirty() || !invalid.isEmpty());
        reset.setDisable(busy || (!dirty() && invalid.isEmpty()));
    }
    private void close() {
        if (busy || dirty() || !invalid.isEmpty()) {
            status.setText(busy ? "Please wait…" : "Save or reset your changes before closing."); return;
        }
        host.getChildren().remove(overlay);
    }
    private void save() {
        if (busy || !dirty() || !invalid.isEmpty()) return;
        work("Saving changes…", () -> {
            var changed = documents.stream().filter(ConfigSettingsDocument::dirty).toList();
            for (var doc : changed) if (!files.read(doc.snapshot().file()).text().equals(doc.snapshot().text()))
                throw new java.io.IOException("Settings changed outside the launcher");
            for (var doc : changed) doc.saved(files.save(doc.snapshot(), doc.serialize()));
            return true;
        }, done -> { status.setText("Changes saved. Ready for your next game."); onSaved.run(); },
                "Couldn't save all changes. Close Hytale and reopen settings if they changed elsewhere.");
    }
    private <T> void work(String message, DiskWork<T> action, Consumer<T> success, String failure) {
        busy = true; status.setText(message); updateControls();
        CompletableFuture.supplyAsync(() -> {
            try { return action.run(); } catch (Exception ex) { throw new java.util.concurrent.CompletionException(ex); }
        }, executor).whenComplete((result, error) -> Platform.runLater(() -> {
            busy = false;
            if (error != null) status.setText(failure); else success.accept(result);
            updateControls();
        }));
    }
    private record Row(Setting setting, VBox node) {}
    private interface DiskWork<T> { T run() throws Exception; }
}
