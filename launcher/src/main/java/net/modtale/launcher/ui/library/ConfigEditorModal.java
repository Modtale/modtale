package net.modtale.launcher.ui.library;

import static net.modtale.launcher.ui.common.LauncherUi.primaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.secondaryButton;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import net.modtale.launcher.config.HytaleConfigFiles;
import net.modtale.launcher.config.HytaleConfigFiles.ConfigFile;
import net.modtale.launcher.config.HytaleConfigFiles.Snapshot;

/** World-scoped editor. Disk work stays off the JavaFX application thread. */
final class ConfigEditorModal {
    private final HytaleConfigFiles files = new HytaleConfigFiles();
    private final Executor executor;
    private final StackPane host;
    private final Runnable onSaved;
    private final StackPane overlay = new StackPane();
    private final ListView<ConfigFile> list = new ListView<>();
    private final TextField search = new TextField();
    private final TextArea editor = new TextArea();
    private final Label status = new Label();
    private final Label pathLabel = new Label("Select a config file");
    private final Button save = primaryButton("Save");
    private final Button reload = secondaryButton("Reload");
    private final Button discard = secondaryButton("Discard edits");
    private List<ConfigFile> entries = List.of();
    private Snapshot snapshot;
    private boolean busy;

    ConfigEditorModal(StackPane host, Executor executor, Runnable onSaved) {
        this.host = host;
        this.executor = executor;
        this.onSaved = onSaved;
    }

    void show(Path globalMods, Path world, String worldName) {
        show(worldName, () -> files.discover(globalMods, world));
    }

    void show(List<ConfigFile> selectedFiles, String modName) {
        List<ConfigFile> selected = List.copyOf(selectedFiles);
        show(modName, () -> selected);
    }

    private void show(String name, DiskWork<List<ConfigFile>> discover) {
        Label title = new Label("Config · " + name);
        title.getStyleClass().add("config-editor-title");
        Label hint = new Label("Close Hytale before editing. JSON is validated; other formats are saved as text. A backup is kept beside each saved file. Configs are saved in launcher settings and sync when signed in.");
        hint.setWrapText(true);
        hint.getStyleClass().add("library-muted-text");
        search.setPromptText("Search config files");
        search.setAccessibleText("Search config files");
        list.setAccessibleText("Config files");
        list.setPlaceholder(new Label("No configs found.\nRun the world with its mods enabled first."));
        list.setPrefWidth(280);
        VBox browser = new VBox(8, search, list);
        VBox.setVgrow(list, Priority.ALWAYS);
        pathLabel.setWrapText(true);
        editor.setAccessibleText("Config contents");
        editor.setWrapText(false);
        editor.setStyle("-fx-font-family: monospace; -fx-font-size: 13px;");
        VBox content = new VBox(8, pathLabel, editor);
        VBox.setVgrow(editor, Priority.ALWAYS);
        SplitPane split = new SplitPane(browser, content);
        split.setDividerPositions(0.32);
        VBox.setVgrow(split, Priority.ALWAYS);
        status.setWrapText(true);
        status.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(status, Priority.ALWAYS);
        Button close = secondaryButton("Close");
        close.setOnAction(event -> close());
        save.setOnAction(event -> save());
        reload.setOnAction(event -> { if (snapshot != null) load(snapshot.file()); });
        discard.setOnAction(event -> {
            if (snapshot != null) editor.setText(snapshot.text());
            status.setText("Edits discarded.");
        });
        HBox footer = new HBox(8, status, discard, reload, close, save);
        VBox modal = new VBox(14, title, hint, split, footer);
        modal.getStyleClass().addAll("post-download-modal", "config-editor-modal");
        modal.setPadding(new Insets(22));
        modal.setPrefSize(1000, 650);
        modal.setMaxSize(1100, 750);
        modal.setMinSize(0, 0);
        overlay.getStyleClass().add("post-download-modal-overlay");
        overlay.setPadding(new Insets(24));
        overlay.getChildren().add(modal);
        overlay.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) { close(); event.consume(); }
            if (event.isShortcutDown() && event.getCode() == KeyCode.S) { save(); event.consume(); }
        });
        host.getChildren().add(overlay);
        editor.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!busy && snapshot != null) status.setText(dirty() ? "Unsaved changes" : "Ready");
            updateControls();
        });
        search.textProperty().addListener((observable, oldValue, newValue) -> filter());
        list.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, file) -> {
            if (file != null) load(file);
        });
        work("Finding configs…", discover, found -> {
            entries = found;
            filter();
            status.setText(found.size() + " config files");
        });
    }

    private void filter() {
        if (dirty() || busy) return;
        String query = search.getText().toLowerCase(Locale.ROOT);
        list.setItems(FXCollections.observableArrayList(entries.stream()
                .filter(file -> file.label().toLowerCase(Locale.ROOT).contains(query)).toList()));
    }

    private void load(ConfigFile file) {
        if (dirty() || busy) return;
        // Clear the prior snapshot so a failed read can never save to the previous selection.
        snapshot = null;
        editor.clear();
        pathLabel.setText(file.label());
        work("Loading…", () -> files.read(file), loaded -> {
            snapshot = loaded;
            editor.setText(loaded.text());
            status.setText("Ready");
            editor.requestFocus();
        });
    }

    private void save() {
        if (busy || !dirty()) return;
        Snapshot original = snapshot;
        String text = editor.getText();
        work("Saving…", () -> files.save(original, text), saved -> {
            snapshot = saved;
            status.setText("Saved. Backup created beside the config.");
            onSaved.run();
        });
    }

    private boolean dirty() {
        return snapshot != null && !editor.getText().equals(snapshot.text());
    }

    private void updateControls() {
        boolean dirty = dirty();
        list.setDisable(busy || dirty);
        search.setDisable(busy || dirty);
        editor.setDisable(busy || snapshot == null);
        save.setDisable(busy || !dirty);
        reload.setDisable(busy || dirty || snapshot == null);
        discard.setDisable(busy || !dirty);
    }

    private void close() {
        if (busy || dirty()) {
            status.setText(busy ? "Wait for the current operation to finish." : "Save or discard your edits before closing or changing files.");
            return;
        }
        host.getChildren().remove(overlay);
    }

    private <T> void work(String message, DiskWork<T> action, Consumer<T> success) {
        busy = true;
        status.setText(message);
        updateControls();
        CompletableFuture.supplyAsync(() -> {
            try { return action.run(); }
            catch (Exception ex) { throw new java.util.concurrent.CompletionException(ex); }
        }, executor).whenComplete((result, error) -> Platform.runLater(() -> {
            busy = false;
            if (error != null) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                status.setText(cause.getMessage() == null ? "Could not access this config." : cause.getMessage());
            } else {
                success.accept(result);
            }
            updateControls();
        }));
    }

    private interface DiskWork<T> { T run() throws Exception; }
}
