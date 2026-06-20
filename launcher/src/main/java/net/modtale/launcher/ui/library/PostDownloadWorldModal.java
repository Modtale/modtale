package net.modtale.launcher.ui.library;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleWorld;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherIcons;

final class PostDownloadWorldModal {

    private static final double MODAL_WIDTH = 512;
    private static final double MODAL_MAX_HEIGHT = 720;
    private static final double WORLD_ICON_IMAGE_SIZE = 44;
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass INDETERMINATE = PseudoClass.getPseudoClass("indeterminate");

    private final Supplier<StackPane> host;
    private final Consumer<Selection> apply;
    private final CachedImageLoader imageLoader;
    private final Map<String, LibraryToggleBox> worldChecks = new LinkedHashMap<>();
    private final Map<String, HBox> worldRows = new LinkedHashMap<>();
    private final Set<String> selectedWorldKeys = new LinkedHashSet<>();

    private StackPane overlay;
    private String title = "";
    private List<String> modIds = List.of();
    private List<WorldOption> worlds = List.of();
    private Button applyButton;
    private Button toggleAllButton;
    private Label selectedCount;

    PostDownloadWorldModal(Supplier<StackPane> host, Consumer<Selection> apply, CachedImageLoader imageLoader) {
        this.host = host == null ? () -> null : host;
        this.apply = apply == null ? ignored -> {
        } : apply;
        this.imageLoader = imageLoader;
    }

    boolean show(String title, List<String> modIds, List<WorldOption> worlds) {
        if (modIds == null || modIds.isEmpty() || worlds == null || worlds.isEmpty()) {
            return false;
        }
        hide();
        StackPane hostPane = host.get();
        if (hostPane == null) {
            return false;
        }
        this.title = value(title, "Installed project");
        this.modIds = modIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        this.worlds = List.copyOf(worlds);
        selectedWorldKeys.clear();
        this.worlds.stream()
                .filter(WorldOption::selected)
                .map(PostDownloadWorldModal::worldKey)
                .forEach(selectedWorldKeys::add);

        overlay = overlayShell();
        hostPane.getChildren().add(overlay);
        rebuildOverlay();
        return true;
    }

    private void rebuildOverlay() {
        if (overlay == null) {
            return;
        }
        worldChecks.clear();
        worldRows.clear();
        overlay.getChildren().setAll(modal());
        updateActionState();
        Platform.runLater(overlay::requestFocus);
    }

    private StackPane overlayShell() {
        StackPane shell = new StackPane();
        shell.getStyleClass().add("post-download-modal-overlay");
        shell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        shell.setFocusTraversable(true);
        shell.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                hide();
                event.consume();
            }
        });
        shell.setOnMouseClicked(event -> {
            if (event.getTarget() == shell) {
                hide();
            }
        });
        return shell;
    }

    private VBox modal() {
        VBox modal = new VBox(0);
        modal.getStyleClass().add("post-download-modal");
        modal.setMaxWidth(MODAL_WIDTH);
        modal.setPrefWidth(MODAL_WIDTH);
        modal.setMaxHeight(MODAL_MAX_HEIGHT);
        modal.setOnMouseClicked(event -> event.consume());

        ScrollPane scroll = new ScrollPane(body());
        scroll.getStyleClass().add("post-download-modal-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        modal.getChildren().addAll(header(), scroll, footer());
        return modal;
    }

    private HBox header() {
        HBox header = new HBox(16);
        header.getStyleClass().add("post-download-modal-header");
        header.setAlignment(Pos.CENTER_LEFT);

        HBox titleRow = new HBox(8, LauncherIcons.icon(LauncherIcons.Glyph.GLOBE, 20), new Label("Enable in Worlds"));
        titleRow.getStyleClass().add("post-download-modal-title");
        titleRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleRow, Priority.ALWAYS);

        Button close = new Button(null, LauncherIcons.icon(LauncherIcons.Glyph.X, 18));
        close.getStyleClass().add("post-download-modal-close");
        close.setOnAction(event -> hide());
        header.getChildren().addAll(titleRow, close);
        return header;
    }

    private VBox body() {
        VBox body = new VBox(16);
        body.getStyleClass().add("post-download-modal-body");
        body.getChildren().add(summaryRow());

        VBox list = new VBox(8);
        list.getStyleClass().add("post-download-modal-world-list");
        for (WorldOption world : worlds) {
            list.getChildren().add(worldRow(world));
        }
        body.getChildren().add(list);
        return body;
    }

    private HBox summaryRow() {
        HBox row = new HBox(12);
        row.getStyleClass().add("post-download-modal-summary");
        row.setAlignment(Pos.TOP_LEFT);

        VBox copy = new VBox(3);
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);
        Label description = new Label(title + " installed. Choose where it should be enabled.");
        description.getStyleClass().add("post-download-modal-description");
        description.setWrapText(true);
        selectedCount = new Label();
        selectedCount.getStyleClass().add("post-download-modal-muted");
        copy.getChildren().addAll(description, selectedCount);

        toggleAllButton = new Button();
        toggleAllButton.getStyleClass().add("post-download-modal-toggle-all");
        toggleAllButton.setMinWidth(Region.USE_PREF_SIZE);
        toggleAllButton.setMaxWidth(Region.USE_PREF_SIZE);
        toggleAllButton.setOnAction(event -> toggleAll());
        row.getChildren().addAll(copy, toggleAllButton);
        return row;
    }

    private HBox worldRow(WorldOption option) {
        HBox row = new HBox(10);
        row.getStyleClass().add("post-download-modal-world-row");
        row.setAlignment(Pos.CENTER_LEFT);

        String key = worldKey(option);
        row.pseudoClassStateChanged(SELECTED, selectedWorldKeys.contains(key));
        row.pseudoClassStateChanged(INDETERMINATE, option.indeterminate());
        worldRows.put(key, row);

        LibraryToggleBox check = new LibraryToggleBox();
        check.getStyleClass().add("post-download-modal-check");
        check.setSelected(selectedWorldKeys.contains(key));
        check.setIndeterminate(option.indeterminate());
        check.setOnAction(() -> setWorldSelected(key, check.isSelected()));
        worldChecks.put(key, check);

        StackPane icon = worldIcon(option.world());

        VBox copy = new VBox(3);
        Label name = new Label(option.world().name());
        name.getStyleClass().add("post-download-modal-world-title");
        Label meta = new Label(option.enabledCount() + "/" + option.totalCount() + " already enabled - " + option.meta());
        meta.getStyleClass().add("post-download-modal-world-meta");
        copy.getChildren().addAll(name, meta);
        HBox.setHgrow(copy, Priority.ALWAYS);

        row.setOnMouseClicked(event -> {
            if (event.getTarget() != check) {
                check.setIndeterminate(false);
                check.setSelected(!check.isSelected());
                setWorldSelected(key, check.isSelected());
            }
        });
        row.getChildren().addAll(check, icon, copy);
        return row;
    }

    private StackPane worldIcon(HytaleWorld world) {
        StackPane shell = new StackPane();
        shell.getStyleClass().add("post-download-modal-world-icon");

        String preview = world == null ? "" : world.previewImage();
        if (!preview.isBlank() && imageLoader != null) {
            ImageView image = new ImageView();
            image.setFitWidth(WORLD_ICON_IMAGE_SIZE);
            image.setFitHeight(WORLD_ICON_IMAGE_SIZE);
            image.setPreserveRatio(false);
            image.setSmooth(true);
            image.setMouseTransparent(true);
            image.setClip(roundedClip(WORLD_ICON_IMAGE_SIZE, 10));
            imageLoader.loadInto(image, preview, WORLD_ICON_IMAGE_SIZE, WORLD_ICON_IMAGE_SIZE);
            shell.getChildren().add(image);
        } else {
            shell.getChildren().add(LauncherIcons.icon(LauncherIcons.Glyph.GLOBE, 16));
        }
        return shell;
    }

    private HBox footer() {
        HBox footer = new HBox(10);
        footer.getStyleClass().add("post-download-modal-footer");
        footer.setAlignment(Pos.CENTER);

        Button skip = new Button("Not Now");
        skip.getStyleClass().add("post-download-modal-secondary");
        skip.setOnAction(event -> hide());

        applyButton = new Button();
        applyButton.getStyleClass().add("post-download-modal-primary");
        applyButton.setMaxWidth(Double.MAX_VALUE);
        applyButton.setAlignment(Pos.CENTER);
        applyButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        applyButton.setGraphic(applyButtonContent());
        applyButton.setOnAction(event -> {
            List<HytaleWorld> selected = selectedWorlds();
            hide();
            apply.accept(new Selection(selected, modIds));
        });
        HBox.setHgrow(applyButton, Priority.ALWAYS);
        footer.getChildren().addAll(skip, applyButton);
        return footer;
    }

    private VBox applyButtonContent() {
        VBox content = new VBox(0);
        content.setAlignment(Pos.CENTER);
        content.setMaxWidth(Double.MAX_VALUE);

        HBox title = new HBox(8, LauncherIcons.icon(LauncherIcons.Glyph.CHECK, 18), new Label("Enable Selected"));
        title.getStyleClass().add("post-download-modal-primary-title");
        title.setAlignment(Pos.CENTER);
        title.setMaxWidth(Double.MAX_VALUE);
        content.getChildren().add(title);
        return content;
    }

    private void toggleAll() {
        selectAll(selectedWorldKeys.size() != worlds.size());
    }

    private void selectAll(boolean selected) {
        selectedWorldKeys.clear();
        for (WorldOption option : worlds) {
            String key = worldKey(option);
            LibraryToggleBox check = worldChecks.get(key);
            HBox row = worldRows.get(key);
            if (selected) {
                selectedWorldKeys.add(key);
            }
            if (check != null) {
                check.setIndeterminate(false);
                check.setSelected(selected);
            }
            if (row != null) {
                row.pseudoClassStateChanged(SELECTED, selected);
                row.pseudoClassStateChanged(INDETERMINATE, false);
            }
        }
        rebuildOverlay();
    }

    private void setWorldSelected(String key, boolean selected) {
        if (selected) {
            selectedWorldKeys.add(key);
        } else {
            selectedWorldKeys.remove(key);
        }
        HBox row = worldRows.get(key);
        if (row != null) {
            row.pseudoClassStateChanged(SELECTED, selected);
            row.pseudoClassStateChanged(INDETERMINATE, false);
        }
        updateActionState();
    }

    private List<HytaleWorld> selectedWorlds() {
        List<HytaleWorld> selected = new ArrayList<>();
        for (WorldOption option : worlds) {
            if (selectedWorldKeys.contains(worldKey(option))) {
                selected.add(option.world());
            }
        }
        return selected;
    }

    private void updateActionState() {
        if (applyButton != null) {
            applyButton.setDisable(selectedWorldKeys.isEmpty());
            applyButton.setGraphic(applyButtonContent());
        }
        if (selectedCount != null) {
            int count = selectedWorldKeys.size();
            selectedCount.setText(count + " world" + LibraryProjectSupport.plural(count) + " selected");
        }
        if (toggleAllButton != null) {
            toggleAllButton.setText(selectedWorldKeys.size() == worlds.size() ? "Deselect All" : "Select All");
        }
    }

    private void hide() {
        if (overlay == null) {
            return;
        }
        Parent parent = overlay.getParent();
        if (parent instanceof StackPane stack) {
            stack.getChildren().remove(overlay);
        }
        overlay = null;
    }

    private static String worldKey(WorldOption option) {
        return option == null ? "" : worldKey(option.world());
    }

    private static String worldKey(HytaleWorld world) {
        return world == null || world.directory() == null
                ? ""
                : world.directory().toAbsolutePath().normalize().toString();
    }

    private static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static Rectangle roundedClip(double size, double radius) {
        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(radius * 2);
        clip.setArcHeight(radius * 2);
        return clip;
    }

    record WorldOption(
            HytaleWorld world,
            String meta,
            int enabledCount,
            int totalCount,
            boolean selected,
            boolean indeterminate
    ) {
        WorldOption {
            meta = value(meta, "World save");
            totalCount = Math.max(0, totalCount);
            enabledCount = Math.max(0, Math.min(enabledCount, totalCount));
        }
    }

    record Selection(List<HytaleWorld> worlds, List<String> modIds) {
        Selection {
            worlds = worlds == null ? List.of() : List.copyOf(worlds);
            modIds = modIds == null
                    ? List.of()
                    : modIds.stream()
                    .filter(id -> id != null && !id.isBlank())
                    .map(String::trim)
                    .distinct()
                    .toList();
        }
    }
}
