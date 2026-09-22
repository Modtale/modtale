package net.modtale.launcher.ui.wardrobe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import net.modtale.launcher.hytale.HytaleAuthSession;
import net.modtale.launcher.settings.LauncherSettings;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.common.LauncherView;
import net.modtale.launcher.ui.common.StatusModal;
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.wardrobe.*;
import static net.modtale.launcher.ui.common.LauncherUi.*;

/** Native wardrobe. Network work never blocks the application thread. */
public final class LauncherWardrobeController implements AutoCloseable {
    private enum Tab { CUSTOMIZE, POPULAR, SAVED }
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private final PopularSkinClient popular;
    private long selectionRequest;
    private boolean resolvingPopular;
    private final HBox searchRow = new HBox(10);
    private final Label feedCredit = label("Popular skins by HyTags · Images by Hyvatar", "wardrobe-muted");
    private final WardrobeApiClient api;
    private final WardrobeStore store;
    private final Supplier<LauncherSettings> settings;
    private final LauncherFeedback feedback;
    private final Executor executor;
    private final WardrobePreview preview;
    private final SavedLookThumbnails localThumbnails = new SavedLookThumbnails();
    private final CosmeticEditorController editor;
    private final java.util.function.Function<WardrobeItem, String> thumbnailResolver;
    private final VBox root = new VBox(22);
    private final VBox catalog = new VBox(16);
    private final VBox inspector = new VBox(14);
    private final HBox columns = new HBox(24);
    private final GridPane cards = new GridPane();
    private final PauseTransition resizeReload = new PauseTransition(Duration.millis(150));
    private int cardColumns = 3;
    private final TextField search = new TextField();
    private final ComboBox<String> filter = new ComboBox<>();
    private final Label selectedName = label("", "wardrobe-selected-title");
    private final Label selectedDetail = label("", "wardrobe-muted");
    private final Label status = label("", "wardrobe-muted");
    private final Button apply = primaryButton("Apply to account");
    private final Button save = secondaryButton("Save look");
    private final WardrobePagination pagination = new WardrobePagination(this::goToPage);
    private int totalPages = 1;
    private final Map<Tab, ToggleButton> tabs = new EnumMap<>(Tab.class);
    private List<WardrobeItem> entries = List.of();
    private WardrobeItem selected;
    private String previewProfile = "";
    private Tab tab = Tab.CUSTOMIZE;
    private int page = 1;
    private long request;
    private boolean loaded, busy, applying, disposed;
    public LauncherWardrobeController(WardrobeApiClient api, WardrobeStore store,
            Supplier<LauncherSettings> settings, LauncherFeedback feedback, Executor executor) {
        this(api, store, settings, feedback, executor, new WardrobePreview(executor), LauncherWardrobeController::thumbnail, new PopularSkinClient());
    }

    LauncherWardrobeController(WardrobeApiClient api, WardrobeStore store,
            Supplier<LauncherSettings> settings, LauncherFeedback feedback, Executor executor,
            WardrobePreview preview, java.util.function.Function<WardrobeItem, String> thumbnailResolver,
            PopularSkinClient popular) {
        this.popular = popular;
        this.api = api; this.store = store; this.settings = settings; this.feedback = feedback;
        this.executor = executor; this.preview = preview; this.thumbnailResolver = thumbnailResolver;
        this.editor = new CosmeticEditorController(api, store, settings, feedback, executor);
        build();
    }

    public Node view() { return root; }
    CosmeticEditorController editorForTesting() { return editor; }

    public void open() {
        refresh();
        editor.loadCurrentOnOpen();
    }

    public void refresh() {
        updateSelectionActions();
        if (tab == Tab.CUSTOMIZE) { editor.refresh(); return; }
        if (selected != null && selected.kind() == WardrobeItem.Kind.CAPE && !previewProfile.equals(activeProfile())) select(selected);
        if (!loaded) { loaded = true; load(); }
        else if (tab == Tab.SAVED) load();
    }

    private void build() {
        root.setUserData(LauncherView.WARDROBE);
        root.getStyleClass().add("wardrobe-page");
        root.setMinWidth(0);
        HBox tabBar = new HBox(6); tabBar.getStyleClass().add("wardrobe-tabs");
        ToggleGroup group = new ToggleGroup();
        String[] names = {"Customize", "Popular skins", "Saved looks"};
        LauncherIcons.Glyph[] icons = {LauncherIcons.Glyph.PALETTE, LauncherIcons.Glyph.GRID,
                LauncherIcons.Glyph.HEART};
        for (Tab value : Tab.values()) {
            ToggleButton button = new ToggleButton(names[value.ordinal()], LauncherIcons.icon(icons[value.ordinal()], 16));
            button.getStyleClass().addAll("nav-btn", "wardrobe-tab"); button.setToggleGroup(group);
            button.setOnAction(e -> { button.setSelected(true); selectTab(value); });
            tabs.put(value, button); tabBar.getChildren().add(button);
        }
        tabs.get(tab).setSelected(true);
        search.setPromptText("Search names and collections"); search.getStyleClass().add("wardrobe-search");
        search.setOnAction(e -> { page = 1; totalPages = 1; load(); }); HBox.setHgrow(search, Priority.ALWAYS);
        Button lookup = iconButton("Search", LauncherIcons.Glyph.SEARCH, () -> { page = 1; totalPages = 1; load(); });
        filter.setId("wardrobe-saved-filter"); filter.setVisible(false); filter.setManaged(false);
        filter.getStyleClass().add("wardrobe-filter"); filter.setOnAction(e -> { page = 1; totalPages = 1; load(); });
        Button importLook = secondaryButton("Import outfit"); importLook.setOnAction(e -> importOutfit());
        Button exportLook = secondaryButton("Export outfit"); exportLook.setOnAction(e -> exportOutfit());
        searchRow.getChildren().addAll(search, lookup, filter);
        feedCredit.setVisible(false); feedCredit.setManaged(false);
        FlowPane fileActions = new FlowPane(10, 6, importLook, exportLook);
        fileActions.visibleProperty().bind(feedCredit.visibleProperty().not());
        fileActions.managedProperty().bind(fileActions.visibleProperty());
        fileActions.getChildren().add(label("Outfits stay on this device. Share by exporting a file.", "wardrobe-muted"));
        cards.setId("wardrobe-cards"); cards.setMinWidth(0); cards.setHgap(14); cards.setVgap(14);
        rebuildCardColumns();
        resizeReload.setOnFinished(e -> { if (loaded && tab != Tab.CUSTOMIZE) load(); });
        cards.widthProperty().addListener((o, before, after) -> {
            int count = Math.max(1, (int) Math.floor((after.doubleValue() + cards.getHgap()) / (180 + cards.getHgap())));
            if (count == cardColumns) return;
            cardColumns = count; page = 1; totalPages = 1; request++; busy = loaded && tab != Tab.CUSTOMIZE; rebuildCardColumns(); renderCards();
            if (loaded && tab != Tab.CUSTOMIZE) resizeReload.playFromStart();
        });
        pagination.setId("wardrobe-pagination");
        catalog.getChildren().addAll(searchRow, fileActions, status, cards, pagination, feedCredit);
        catalog.setMinWidth(0); HBox.setHgrow(catalog, Priority.ALWAYS);
        status.managedProperty().bind(status.visibleProperty()); status.setVisible(false);
        inspector.getStyleClass().add("wardrobe-inspector"); inspector.setPrefWidth(350); inspector.setMinWidth(290);
        inspector.setMaxWidth(390); inspector.setMaxHeight(Region.USE_PREF_SIZE);
        preview.hideViewActions();
        Node previewNode = preview.view();
        if (previewNode instanceof Region region) { region.setPrefHeight(335); region.setMinHeight(260); }
        selectedDetail.setWrapText(true); selectedName.setWrapText(true);
        hideWhenEmpty(selectedDetail); hideWhenEmpty(selectedName);
        apply.setMaxWidth(Double.MAX_VALUE); apply.setOnAction(e -> applySelection());
        save.setMaxWidth(Double.MAX_VALUE); save.setOnAction(e -> saveSelection());
        Button customize = iconButton("Customize look", LauncherIcons.Glyph.PALETTE, () -> {
            if (selected == null || resolvingPopular) return;
            WardrobeItem item = selected;
            if (item.kind() == WardrobeItem.Kind.CAPE) {
                editor.editCape(payload(item).path("cape").asText());
                selectTab(Tab.CUSTOMIZE); tabs.get(Tab.CUSTOMIZE).setSelected(true);
            } else {
                editor.edit(item, () -> {
                    if (disposed || selected != item) return;
                    selectTab(Tab.CUSTOMIZE); tabs.get(Tab.CUSTOMIZE).setSelected(true);
                });
            }
        });
        customize.setMaxWidth(Double.MAX_VALUE);
        inspector.getChildren().addAll(previewNode, selectedName, selectedDetail, save, customize, apply);
        columns.setAlignment(Pos.TOP_LEFT); columns.getChildren().addAll(catalog, inspector);
        root.getChildren().addAll(tabBar, editor.view());
        root.widthProperty().addListener((o, a, b) -> {
            // Keep the fitting room usable at the launcher's compact window size.
            double width = b.doubleValue();
            inspector.setPrefWidth(width < 1050 ? 300 : 350);
        });
        updateSelectionActions();
    }

    private void selectTab(Tab value) {
        selectionRequest++; resolvingPopular = false;
        if (tab == Tab.POPULAR || value == Tab.POPULAR) { selected = null; preview.clear(); selectedName.setText(""); selectedDetail.setText(""); }
        feedCredit.setVisible(value == Tab.POPULAR); feedCredit.setManaged(value == Tab.POPULAR);
        searchRow.setVisible(value != Tab.POPULAR); searchRow.setManaged(value != Tab.POPULAR);
        request++; tab = value; page = 1; totalPages = 1; search.clear();
        if (selected != null) {
            if (value == Tab.SAVED) selected = store.items().stream()
                    .filter(item -> item.id().equals(selected.id())).findFirst().orElse(null);
            if (selected == null) {
                selectedName.setText("");
                selectedDetail.setText("");
                preview.clear();
            } else selectedName.setText(displayedLookName(selected));
        }
        root.getChildren().removeAll(columns, editor.view());
        if (value == Tab.CUSTOMIZE) { root.getChildren().add(editor.view()); editor.refresh(); return; }
        root.getChildren().add(columns);
        filter.setOnAction(null);
        filter.getItems().setAll("All looks", "Favorites", "Skins", "Capes");
        filter.setVisible(value == Tab.SAVED); filter.setManaged(value == Tab.SAVED);
        filter.getSelectionModel().selectFirst();
        filter.setOnAction(e -> { page = 1; totalPages = 1; load(); });
        search.setPromptText("Search names and collections");
        load();
    }

    private void load() {
        if (disposed) return;
        if (tab == Tab.CUSTOMIZE) { editor.refresh(); return; }
        resizeReload.stop(); loaded = true;
        long generation = ++request;
        Tab requestedTab = tab; int requestedPage = page; int pageSize = cardColumns * 4;
        String query = search.getText().trim(); String ordering = filter.getValue();
        busy = true; setStatus("Loading looks…"); updateSelectionActions();
        CompletableFuture.supplyAsync(() -> {
            if (requestedTab == Tab.POPULAR) return popularPage(requestedPage, pageSize);
            return pageItems(savedLooks(query, ordering), requestedPage, pageSize);
        }, executor).whenComplete((items, error) -> Platform.runLater(() -> {
            if (disposed || generation != request) return;
            busy = false;
            if (error != null) { totalPages = Math.max(1, page); entries = List.of(); renderCards(); setStatus(message(error) + (requestedTab == Tab.POPULAR ? "  Reopen Popular skins to retry." : "  Try Search again.")); }
            else { entries = items.items(); totalPages = requestedTab == Tab.POPULAR && items.hasNext()
                    ? Math.max(totalPages, items.totalPages()) : items.totalPages(); setStatus(""); renderCards(); if (selected == null && !entries.isEmpty()) select(entries.getFirst()); }
            updateSelectionActions();
        }));
    }

    private javafx.stage.FileChooser outfitChooser(String title) {
        var chooser = new javafx.stage.FileChooser(); chooser.setTitle(title);
        chooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Modtale outfit", "*.json"));
        return chooser;
    }

    private void importOutfit() {
        var file = outfitChooser("Import outfit").showOpenDialog(root.getScene().getWindow());
        if (file == null) return;
        feedback.runAsync("Importing outfit", () -> {
            try {
                var imported = LocalSkinLibrary.importFile(file.toPath());
                var item = store.items().stream().filter(saved -> saved.id().equals(imported.id())).findFirst().orElse(imported);
                store.saveItem(item); return item;
            }
            catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
        }, item -> { if (!disposed) { load(); select(item); } });
    }

    private void exportOutfit() {
        WardrobeItem item = selected;
        if (item == null || !payload(item).path("skin").isObject()) {
            feedback.showToast("Select an outfit", "Select a complete skin to export."); return;
        }
        var chooser = outfitChooser("Export outfit"); chooser.setInitialFileName("outfit.json");
        var file = chooser.showSaveDialog(root.getScene().getWindow()); if (file == null) return;
        feedback.runAsync("Exporting outfit", () -> {
            try { LocalSkinLibrary.exportFile(file.toPath(), item); return true; }
            catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
        }, done -> feedback.showToast("Outfit exported", "Only cosmetic selections were included. No account details were exported."));
    }

    private record CardPage(List<WardrobeItem> items, boolean hasNext, int totalPages) {}

    private CardPage popularPage(int page, int size) {
        int offset = (page - 1) * size, sourcePage = offset / 20 + 1, skip = offset % 20;
        List<WardrobeItem> items = new ArrayList<>();
        while (items.size() <= size) {
            var batch = popular.page(sourcePage++);
            for (int i = skip; i < batch.items().size() && items.size() <= size; i++) items.add(batch.items().get(i));
            if (!batch.hasNext()) break;
            skip = 0;
        }
        boolean more = items.size() > size;
        return new CardPage(List.copyOf(items.subList(0, Math.min(size, items.size()))), more, page + (more ? 1 : 0));
    }

    private static CardPage pageItems(List<WardrobeItem> items, int page, int size) {
        int from = Math.min(items.size(), (page - 1) * size);
        int to = Math.min(items.size(), from + size);
        return new CardPage(List.copyOf(items.subList(from, to)), to < items.size(), Math.max(1, (items.size() + size - 1) / size));
    }

    private void rebuildCardColumns() {
        cards.getColumnConstraints().clear();
        for (int i = 0; i < cardColumns; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / cardColumns); column.setHgrow(Priority.ALWAYS);
            cards.getColumnConstraints().add(column);
        }
    }

    private List<WardrobeItem> savedLooks(String query, String ordering) {
        String needle = query.toLowerCase(Locale.ROOT);
        return store.items().stream().filter(i -> (i.name() + " " + i.collection()).toLowerCase(Locale.ROOT).contains(needle))
                .filter(i -> !"Favorites".equals(ordering) || i.favorite())
                .filter(i -> !"Skins".equals(ordering) || i.kind() == WardrobeItem.Kind.SKIN)
                .filter(i -> !"Capes".equals(ordering) || i.kind() == WardrobeItem.Kind.CAPE).toList();
    }

    private void goToPage(int target) {
        if (busy || target < 1 || target > totalPages || target == page) return;
        page = target; load();
    }

    private void updatePagination() {
        pagination.update(page, totalPages, busy);
    }

    private void renderCards() {
        cards.getChildren().clear();
        updatePagination();
        for (int i = 0; i < entries.size(); i++) cards.add(card(entries.get(i)), i % cardColumns, i / cardColumns);
        if (entries.isEmpty()) {
            VBox empty = new VBox(12, LauncherIcons.icon(tab == Tab.SAVED ? LauncherIcons.Glyph.HEART : LauncherIcons.Glyph.SEARCH, 32),
                    label(tab == Tab.SAVED ? "No saved looks" : "No looks found", "wardrobe-section-title"));
            empty.setAlignment(Pos.CENTER); empty.getStyleClass().add("wardrobe-empty"); empty.setMinWidth(0);
            cards.add(empty, 0, 0, cardColumns, 1);
        }
    }

    private Node card(WardrobeItem entry) {
        WardrobeItem item = tab == Tab.SAVED
                ? store.items().stream().filter(saved -> saved.id().equals(entry.id())).findFirst().orElse(entry) : entry;
        ImageView image = new ImageView(); image.setFitHeight(150); image.setFitWidth(158); image.setPreserveRatio(true);
        String url = tab == Tab.POPULAR ? popular.thumbnail(payload(item).path("skinId").asText()) : thumbnailResolver.apply(item);
        if (!url.isBlank()) image.setImage(new Image(url, 190, 190, true, true, true));
        Label fallback = label(item.kind() == WardrobeItem.Kind.CAPE ? "CAPE" : "SKIN", "wardrobe-card-fallback");
        if (image.getImage() != null) fallback.visibleProperty().bind(image.getImage().errorProperty().or(image.getImage().progressProperty().lessThan(1)));
        StackPane visual = new StackPane(fallback, image); visual.getStyleClass().add("wardrobe-card-art");
        JsonNode savedSkin = payload(item).path("skin");
        java.nio.file.Path assets = editor.assetsForPreview();
        if (url.isBlank() && java.nio.file.Files.isRegularFile(assets)) {
            CompletableFuture<Image> rendered = item.kind() == WardrobeItem.Kind.CAPE
                    ? localThumbnails.loadCape(assets, payload(item).path("cape").asText())
                    : savedSkin.isObject() ? localThumbnails.load(assets, savedSkin) : null;
            if (rendered != null) rendered.thenAccept(thumbnail -> {
                if (disposed) return;
                image.setImage(thumbnail);
                fallback.setVisible(false);
            });
        }
        visual.setPrefHeight(170); visual.setMinWidth(0); visual.setMaxWidth(Double.MAX_VALUE);
        String displayName = displayedLookName(item);
        VBox contents = new VBox(8, visual);
        if (tab != Tab.SAVED) {
            Label name = label(displayName, "wardrobe-card-name"); hideWhenEmpty(name); name.setMinWidth(0); name.setMaxWidth(Double.MAX_VALUE);
            contents.getChildren().add(name);
        }
        Button button = new Button(); button.setId("wardrobe-look-" + item.id()); button.setGraphic(contents); button.getStyleClass().add("wardrobe-card");
        button.setMinWidth(0); button.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(button, Priority.ALWAYS); GridPane.setFillWidth(button, true);
        contents.setMinWidth(0);
        contents.prefWidthProperty().bind(button.widthProperty().subtract(22));
        contents.maxWidthProperty().bind(contents.prefWidthProperty());
        image.fitWidthProperty().bind(visual.widthProperty());
        if (!displayName.isBlank()) button.setTooltip(new Tooltip(displayName));
        button.setAccessibleText(displayName.isBlank() ? "Preview skin " + ((page - 1) * cardColumns * 4 + entries.indexOf(entry) + 1) : "Preview " + displayName);
        button.pseudoClassStateChanged(SELECTED, selected != null && selected.id().equals(item.id()));
        button.setOnAction(e -> select(item));
        return button;
    }

    private void select(WardrobeItem item) {
        long ticket = ++selectionRequest;
        resolvingPopular = false;
        if (tab == Tab.POPULAR && !payload(item).path("skin").isObject()) {
            selected = item; resolvingPopular = true; preview.clear(); selectedDetail.setText("Loading popular outfit…");
            updateSelectionActions();
            CompletableFuture.supplyAsync(() -> popular.download(payload(item).path("skinId").asText()), executor)
                    .whenComplete((downloaded, error) -> Platform.runLater(() -> {
                        if (disposed || ticket != selectionRequest || tab != Tab.POPULAR) return;
                        resolvingPopular = false;
                        if (error == null) select(downloaded);
                        else { selected = null; selectedDetail.setText(message(error)); updateSelectionActions(); }
                    }));
            return;
        }
        selected = item; previewProfile = activeProfile(); selectedName.setText(displayedLookName(item));
        selectedDetail.setText("");
        JsonNode payload = payload(item);
        java.nio.file.Path assets = editor.assetsForPreview();
        if (java.nio.file.Files.isRegularFile(assets)) {
            if (item.kind() == WardrobeItem.Kind.SKIN && payload.path("skin").isObject()) { preview.focusCategory(""); preview.showLocal(assets, payload.path("skin")); }
            else if (item.kind() == WardrobeItem.Kind.CAPE) {
                preview.clear();
                CompletableFuture.supplyAsync(() -> {
                    try {
                        var skin = new CosmeticCatalogClient(assets).defaultSkin();
                        skin.set("cape", payload.path("cape")); return skin;
                    } catch (java.io.IOException ex) { throw new java.io.UncheckedIOException(ex); }
                }, executor).whenComplete((skin, error) -> Platform.runLater(() -> {
                    if (disposed || selected != item) return;
                    if (error == null) { preview.focusCategory("cape"); preview.showLocal(assets, skin); }
                    else selectedDetail.setText(message(error));
                }));
            } else { preview.clear(); selectedDetail.setText("This old saved look needs an outfit file before it can be previewed."); }
        } else { preview.clear(); selectedDetail.setText("Set your Hytale game directory in Settings to preview this look."); }
        updateSelectionActions();
        for (Node card : cards.getChildren()) {
            card.pseudoClassStateChanged(SELECTED, ("wardrobe-look-" + item.id()).equals(card.getId()));
        }
    }

    private void updateSelectionActions() {
        HytaleAuthSession session = settings.get().getHytaleAuthSession();
        String username = session == null ? "" : session.getUsername();
        apply.setText(applying ? "Applying…" : username.isBlank() ? "Apply" : "Apply to " + username);
        apply.setDisable(selected == null || resolvingPopular || session == null || applying);
        apply.setTooltip(new Tooltip(username.isBlank() ? "Hytale account unavailable." : "Apply to " + username + ". The previous look is saved locally."));
        save.setDisable(selected == null || resolvingPopular || applying);
        save.setText(selected != null && store.items().stream().anyMatch(i -> i.id().equals(selected.id())) ? "Edit saved look" : "Save look");
        updatePagination();
    }

    private void applySelection() {
        if (selected == null || resolvingPopular || applying) return;
        WardrobeItem item = selected; LauncherSettings current = settings.get();
        HytaleAuthSession session = current.getHytaleAuthSession(); if (session == null) return;
        String target = session.getUuid(); String username = session.getUsername();
        applying = true; updateSelectionActions();
        feedback.runAsync("Applying look to " + username, () -> {
            if (current.getHytaleAuthSession() == null || !target.equals(current.getHytaleAuthSession().getUuid()))
                throw new IllegalStateException("The active Hytale profile changed. Select Apply again.");
            WardrobeItem previousLook = api.currentSkin(current);
            try {
                store.saveItem(new WardrobeItem(previousLook.id(), previousLook.kind(), username + " · previous look", false,
                        "Previous looks", previousLook.payload()));
            } catch (java.io.IOException e) { throw new java.io.UncheckedIOException("Could not save your previous look", e); }
            if (current.getHytaleAuthSession() == null || !target.equals(current.getHytaleAuthSession().getUuid()))
                throw new IllegalStateException("The active Hytale profile changed. Select Apply again.");
            api.apply(item, current, UUID.fromString(target)); return item;
        }, applied -> { applying = false; updateSelectionActions(); feedback.showToast("Look updated", "Applied to " + username + "."); },
                error -> { applying = false; updateSelectionActions(); });
    }

    private void saveSelection() {
        if (selected == null || resolvingPopular) return;
        WardrobeItem item = store.items().stream().filter(saved -> saved.id().equals(selected.id())).findFirst().orElse(selected);
        if (root.getScene() == null || !(root.getScene().getRoot() instanceof StackPane host)) return;
        TextField name = new TextField(lookName(item).isBlank() ? "My look" : lookName(item)); TextField collection = new TextField(item.collection());
        name.setPromptText("Look name"); collection.setPromptText("Collection (optional)");
        name.getStyleClass().add("wardrobe-search"); collection.getStyleClass().add("wardrobe-search");
        name.setAccessibleText("Look name"); collection.setAccessibleText("Collection");
        CheckBox favorite = new CheckBox("Add to favorites"); favorite.setSelected(item.favorite());
        favorite.getStyleClass().add("native-check");
        VBox form = new VBox(12, label("Name", "wardrobe-muted"), name,
                label("Collection", "wardrobe-muted"), collection, favorite);
        boolean existing = store.items().stream().anyMatch(i -> i.id().equals(item.id()));
        var invalid = javafx.beans.binding.Bindings.createBooleanBinding(
                () -> name.getText().isBlank(), name.textProperty());
        Platform.runLater(() -> { name.requestFocus(); name.selectAll(); });
        StatusModal.Result choice = StatusModal.builder(() -> host)
                .type(StatusModal.Type.INFO)
                .title(existing ? "Edit saved look" : "Save look")
                .message("Keep this outfit in Saved looks.")
                .content(form).actionLabel("Save").actionIcon(LauncherIcons.Glyph.SAVE)
                .secondaryLabel(existing ? "Remove saved look" : "Cancel")
                .actionDisabled(invalid).showAndWait();
        if (choice == StatusModal.Result.CLOSED || (!existing && choice == StatusModal.Result.SECONDARY)) return;
        try {
            if (choice == StatusModal.Result.PRIMARY) {
                WardrobeItem saved = new WardrobeItem(item.id(), item.kind(), name.getText(), favorite.isSelected(), collection.getText(), item.payload());
                feedback.runAsync("Saving look", () -> {
                    WardrobeItem hydrated = api.hydrate(saved);
                    try { store.saveItem(hydrated); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                    return hydrated;
                }, hydrated -> {
                    if (selected != null && selected.id().equals(item.id())) {
                        if (tab == Tab.SAVED) selected = hydrated;
                        selectedName.setText(displayedLookName(selected));
                    }
                    if (tab == Tab.SAVED) load(); updateSelectionActions();
                    feedback.showToast("Look saved", "Ready whenever you are.");
                });
            } else if (existing && choice == StatusModal.Result.SECONDARY) feedback.runAsync("Removing saved look", () -> {
                try { store.removeItem(item.id()); return true; } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
            }, done -> { if (tab == Tab.SAVED) load(); updateSelectionActions(); });
            if (tab == Tab.SAVED) load(); updateSelectionActions();
        } catch (Exception e) { feedback.showToast("Could not save look", message(e)); }
    }

    private String displayedLookName(WardrobeItem item) { return lookName(item); }

    private static String lookName(WardrobeItem item) {
        return item.kind() == WardrobeItem.Kind.SKIN && item.name().matches("(?i)Skin #[0-9a-f]{8,32}") ? "" : item.name();
    }

    private String activeProfile() { HytaleAuthSession session = settings.get().getHytaleAuthSession(); return session == null ? "" : session.getUuid(); }
    private String activeUsername() { HytaleAuthSession s = settings.get().getHytaleAuthSession(); return s == null || s.getUsername().isBlank() ? "NPC" : s.getUsername(); }
    private void setStatus(String text) { status.setText(text); status.setVisible(!text.isBlank()); }
    private static JsonNode payload(WardrobeItem item) { try { return JSON.readTree(item.payload()); } catch (Exception e) { return JSON.createObjectNode(); } }
    private static String thumbnail(WardrobeItem item) { return ""; }
    private static String message(Throwable e) { while (e.getCause() != null && e instanceof CompletionException) e = e.getCause(); return e.getMessage() == null ? "Please try again." : e.getMessage(); }
    private static Label label(String text, String style) { Label l = new Label(text); l.getStyleClass().add(style); return l; }
    private static void hideWhenEmpty(Label label) { label.visibleProperty().bind(label.textProperty().isNotEmpty()); label.managedProperty().bind(label.visibleProperty()); }
    private static Button iconButton(String title, LauncherIcons.Glyph glyph, Runnable action) { Button b = secondaryButton(title); b.setGraphic(LauncherIcons.icon(glyph, 15)); b.setOnAction(e -> action.run()); return b; }
    @Override public void close() { disposed = true; request++; resizeReload.stop(); localThumbnails.close(); preview.dispose(); editor.close(); }
}
