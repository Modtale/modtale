package net.modtale.launcher.ui.wardrobe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import javafx.application.Platform;
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
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.wardrobe.*;
import static net.modtale.launcher.ui.common.LauncherUi.*;

/** Native wardrobe. Network work never blocks the application thread. */
public final class LauncherWardrobeController implements AutoCloseable {
    private enum Tab { CUSTOMIZE, SKINS, SAVED }
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private final WardrobeApiClient api;
    private final WardrobeStore store;
    private final Supplier<LauncherSettings> settings;
    private final LauncherFeedback feedback;
    private final Executor executor;
    private final WardrobePreview preview;
    private final CosmeticEditorController editor;
    private final java.util.function.Function<WardrobeItem, String> thumbnailResolver;
    private final VBox root = new VBox(22);
    private final VBox catalog = new VBox(16);
    private final VBox inspector = new VBox(14);
    private final HBox columns = new HBox(24);
    private final FlowPane cards = new FlowPane(14, 14);
    private final TextField search = new TextField();
    private final ComboBox<String> filter = new ComboBox<>();
    private final Label selectedName = label("Make it yours", "wardrobe-selected-title");
    private final Label selectedDetail = label("", "wardrobe-muted");
    private final Label status = label("", "wardrobe-muted");
    private final Button apply = primaryButton("Apply to account");
    private final Button save = secondaryButton("Save look");
    private final Button previous = secondaryButton("Previous");
    private final Button next = secondaryButton("Next");
    private final Label pageLabel = label("Page 1", "wardrobe-muted");
    private final Map<Tab, ToggleButton> tabs = new EnumMap<>(Tab.class);
    private List<WardrobeItem> entries = List.of();
    private WardrobeItem selected;
    private String previewProfile = "";
    private Tab tab = Tab.SKINS;
    private int page = 1;
    private long request;
    private boolean loaded, busy, applying, disposed;
    public LauncherWardrobeController(WardrobeApiClient api, WardrobeStore store,
            Supplier<LauncherSettings> settings, LauncherFeedback feedback, Executor executor) {
        this(api, store, settings, feedback, executor, new WardrobePreview(executor), LauncherWardrobeController::thumbnail);
    }

    LauncherWardrobeController(WardrobeApiClient api, WardrobeStore store,
            Supplier<LauncherSettings> settings, LauncherFeedback feedback, Executor executor,
            WardrobePreview preview, java.util.function.Function<WardrobeItem, String> thumbnailResolver) {
        this.api = api; this.store = store; this.settings = settings; this.feedback = feedback;
        this.executor = executor; this.preview = preview; this.thumbnailResolver = thumbnailResolver;
        this.editor = new CosmeticEditorController(api, store, settings, feedback, executor);
        build();
    }

    public Node view() { return root; }
    CosmeticEditorController editorForTesting() { return editor; }

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
        String[] names = {"Customize", "Skins", "Saved looks"};
        LauncherIcons.Glyph[] icons = {LauncherIcons.Glyph.PALETTE, LauncherIcons.Glyph.GRID,
                LauncherIcons.Glyph.HEART};
        for (Tab value : Tab.values()) {
            ToggleButton button = new ToggleButton(names[value.ordinal()], LauncherIcons.icon(icons[value.ordinal()], 16));
            button.getStyleClass().add("wardrobe-tab"); button.setToggleGroup(group);
            button.setOnAction(e -> { button.setSelected(true); selectTab(value); });
            tabs.put(value, button); tabBar.getChildren().add(button);
        }
        tabs.get(tab).setSelected(true);
        search.setPromptText("Username or skin hash"); search.getStyleClass().add("wardrobe-search");
        search.setOnAction(e -> { page = 1; load(); }); HBox.setHgrow(search, Priority.ALWAYS);
        Button lookup = iconButton("Search", LauncherIcons.Glyph.SEARCH, () -> { page = 1; load(); });
        filter.getItems().setAll("Popular", "Skin ID"); filter.setValue("Popular");
        filter.getStyleClass().add("wardrobe-filter"); filter.setOnAction(e -> { page = 1; load(); });
        HBox searchRow = new HBox(10, search, lookup, filter);
        cards.setPrefWrapLength(630); cards.setMinWidth(0);
        previous.setOnAction(e -> { page = Math.max(1, page - 1); load(); });
        next.setOnAction(e -> { page++; load(); });
        HBox pager = new HBox(12, previous, pageLabel, next); pager.setAlignment(Pos.CENTER);
        catalog.getChildren().addAll(searchRow, status, cards, pager);
        catalog.setMinWidth(0); HBox.setHgrow(catalog, Priority.ALWAYS);
        status.managedProperty().bind(status.visibleProperty()); status.setVisible(false);
        inspector.getStyleClass().add("wardrobe-inspector"); inspector.setPrefWidth(350); inspector.setMinWidth(290);
        inspector.setMaxWidth(390); inspector.setMaxHeight(Region.USE_PREF_SIZE);
        Node previewNode = preview.view();
        if (previewNode instanceof Region region) { region.setPrefHeight(335); region.setMinHeight(260); }
        selectedDetail.setWrapText(true); selectedName.setWrapText(true);
        hideWhenEmpty(selectedDetail);
        apply.setMaxWidth(Double.MAX_VALUE); apply.setOnAction(e -> applySelection());
        save.setMaxWidth(Double.MAX_VALUE); save.setOnAction(e -> saveSelection());
        Button customize = iconButton("Customize look", LauncherIcons.Glyph.PALETTE, () -> {
            if (selected == null) return;
            WardrobeItem item = selected;
            if (item.kind() == WardrobeItem.Kind.CAPE) {
                editor.editCape(payload(item).path("cape").asText());
                selectTab(Tab.CUSTOMIZE); tabs.get(Tab.CUSTOMIZE).setSelected(true);
            } else { editor.edit(item); selectTab(Tab.CUSTOMIZE); tabs.get(Tab.CUSTOMIZE).setSelected(true); }
        });
        customize.setMaxWidth(Double.MAX_VALUE);
        inspector.getChildren().addAll(previewNode, selectedName, selectedDetail, save, customize, apply);
        columns.setAlignment(Pos.TOP_LEFT); columns.getChildren().addAll(catalog, inspector);
        root.getChildren().addAll(tabBar, columns);
        root.widthProperty().addListener((o, a, b) -> {
            // Keep the fitting room usable at the launcher's compact window size.
            double width = b.doubleValue();
            inspector.setPrefWidth(width < 1050 ? 300 : 350);
            cards.setPrefWrapLength(Math.max(200, width - inspector.getPrefWidth() - 24));
        });
        updateSelectionActions();
    }

    private void selectTab(Tab value) {
        request++; tab = value; page = 1; search.clear();
        root.getChildren().removeAll(columns, editor.view());
        if (value == Tab.CUSTOMIZE) { root.getChildren().add(editor.view()); editor.refresh(); return; }
        root.getChildren().add(columns);
        filter.setOnAction(null);
        filter.getItems().setAll(value == Tab.SAVED ? List.of("All looks", "Favorites", "Skins", "Capes") : List.of("Popular", "Skin ID"));
        filter.getSelectionModel().selectFirst();
        filter.setOnAction(e -> { page = 1; load(); });
        search.setPromptText(value == Tab.SAVED ? "Search names and collections" : "Username or skin hash");
        load();
    }

    private void load() {
        if (disposed) return;
        if (tab == Tab.CUSTOMIZE) { editor.refresh(); return; }
        long generation = ++request;
        Tab requestedTab = tab; int requestedPage = page;
        String query = search.getText().trim(); String ordering = filter.getValue();
        busy = true; setStatus("Loading looks…"); updateSelectionActions();
        CompletableFuture.supplyAsync(() -> {
            if (requestedTab == Tab.SAVED) return pageItems(savedLooks(query, ordering), requestedPage);
            if (!query.isBlank()) {
                String candidate = query;
                if (query.startsWith("https://hytags.com/skin/")) candidate = query.substring("https://hytags.com/skin/".length());
                return List.of(candidate.matches("[a-fA-F0-9]{32}") ? api.lookupSkinHash(candidate) : api.lookupSkin(candidate));
            }
            return api.browseSkins(requestedPage, "Skin ID".equals(ordering) ? "unique_id" : "user_count");
        }, executor).whenComplete((items, error) -> Platform.runLater(() -> {
            if (disposed || generation != request) return;
            busy = false;
            if (error != null) { entries = List.of(); renderCards(); setStatus(message(error) + "  Try Search again."); }
            else { entries = items; setStatus(""); if (selected == null && !items.isEmpty()) select(items.getFirst()); else renderCards(); }
            updateSelectionActions();
        }));
    }

    private static List<WardrobeItem> pageItems(List<WardrobeItem> items, int page) { return items.stream().skip((long) (page - 1) * 20).limit(20).toList(); }

    private List<WardrobeItem> savedLooks(String query, String ordering) {
        String needle = query.toLowerCase(Locale.ROOT);
        return store.items().stream().filter(i -> (i.name() + " " + i.collection()).toLowerCase(Locale.ROOT).contains(needle))
                .filter(i -> !"Favorites".equals(ordering) || i.favorite())
                .filter(i -> !"Skins".equals(ordering) || i.kind() == WardrobeItem.Kind.SKIN)
                .filter(i -> !"Capes".equals(ordering) || i.kind() == WardrobeItem.Kind.CAPE).toList();
    }

    private void renderCards() {
        cards.getChildren().clear();
        boolean paged = tab != Tab.SKINS || search.getText().isBlank();
        previous.setVisible(paged); previous.setManaged(paged); next.setVisible(paged); next.setManaged(paged);
        pageLabel.setVisible(paged); pageLabel.setManaged(paged); pageLabel.setText("Page " + page);
        previous.setDisable(page <= 1 || busy); next.setDisable(entries.size() < 20 || busy);
        for (WardrobeItem item : entries) cards.getChildren().add(card(item));
        if (entries.isEmpty()) {
            VBox empty = new VBox(12, LauncherIcons.icon(tab == Tab.SAVED ? LauncherIcons.Glyph.HEART : LauncherIcons.Glyph.SEARCH, 32),
                    label(tab == Tab.SAVED ? "No saved looks" : "No looks found", "wardrobe-section-title"));
            empty.setAlignment(Pos.CENTER); empty.getStyleClass().add("wardrobe-empty"); cards.getChildren().add(empty);
        }
    }

    private Node card(WardrobeItem entry) {
        WardrobeItem item = store.items().stream().filter(saved -> saved.id().equals(entry.id())).findFirst().orElse(entry);
        ImageView image = new ImageView(); image.setFitHeight(150); image.setFitWidth(158); image.setPreserveRatio(true);
        String url = thumbnailResolver.apply(item);
        if (!url.isBlank()) image.setImage(new Image(url, 190, 190, true, true, true));
        Label fallback = label(item.kind() == WardrobeItem.Kind.CAPE ? "CAPE" : "SKIN", "wardrobe-card-fallback");
        if (image.getImage() != null) fallback.visibleProperty().bind(image.getImage().errorProperty().or(image.getImage().progressProperty().lessThan(1)));
        StackPane visual = new StackPane(fallback, image); visual.getStyleClass().add("wardrobe-card-art");
        visual.setPrefHeight(170);
        Label name = label(item.name(), "wardrobe-card-name"); name.setMaxWidth(160);
        VBox contents = new VBox(8, visual, name);
        if (!item.collection().isBlank()) contents.getChildren().add(label(item.collection(), "wardrobe-card-detail"));
        Button button = new Button(); button.setGraphic(contents); button.getStyleClass().add("wardrobe-card");
        button.setAccessibleText("Preview " + item.name());
        button.pseudoClassStateChanged(SELECTED, selected != null && selected.id().equals(item.id()));
        button.setOnAction(e -> select(item));
        if (item.favorite()) name.setText("♥  " + item.name());
        return button;
    }

    private void select(WardrobeItem item) {
        selected = item; previewProfile = activeProfile(); selectedName.setText(item.name());
        selectedDetail.setText("");
        JsonNode payload = payload(item);
        if (item.kind() == WardrobeItem.Kind.SKIN && payload.path("skinId").asText("").isBlank()
                && payload.path("username").asText("").isBlank()) {
            java.nio.file.Path assets = editor.assetsForPreview();
            if (java.nio.file.Files.isRegularFile(assets) && payload.path("skin").isObject()) preview.showLocal(assets, payload.path("skin"));
            else { preview.clear(); selectedDetail.setText("Select game assets in Customize to preview this look."); }
        } else preview.show(item.kind() == WardrobeItem.Kind.CAPE ? activeUsername() : payload.path("username").asText("NPC"), payload.path("skinId").asText(""), payload.path("cape").asText(""));
        updateSelectionActions(); renderCards();
    }

    private void updateSelectionActions() {
        HytaleAuthSession session = settings.get().getHytaleAuthSession();
        String username = session == null ? "" : session.getUsername();
        apply.setText(applying ? "Applying…" : username.isBlank() ? "Link a Hytale account" : "Apply to " + username);
        apply.setDisable(selected == null || session == null || applying);
        apply.setTooltip(new Tooltip(username.isBlank() ? "Link Hytale from the profile menu." : "Apply to " + username + ". The previous look is saved locally."));
        save.setDisable(selected == null || applying);
        save.setText(selected != null && store.items().stream().anyMatch(i -> i.id().equals(selected.id())) ? "Edit saved look" : "Save look");
        previous.setDisable(busy || page <= 1); next.setDisable(busy || entries.size() < 20);
    }

    private void applySelection() {
        if (selected == null || applying) return;
        WardrobeItem item = selected; LauncherSettings current = settings.get();
        HytaleAuthSession session = current.getHytaleAuthSession(); if (session == null) return;
        String target = session.getUuid(); String username = session.getUsername();
        applying = true; updateSelectionActions();
        feedback.runAsync("Applying " + item.name() + " to " + username, () -> {
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
        }, applied -> { applying = false; updateSelectionActions(); feedback.showToast("Look updated", applied.name() + " applied to " + username + "."); },
                error -> { applying = false; updateSelectionActions(); });
    }

    private void saveSelection() {
        if (selected == null) return;
        WardrobeItem item = store.items().stream().filter(saved -> saved.id().equals(selected.id())).findFirst().orElse(selected);
        Dialog<ButtonType> dialog = new Dialog<>(); dialog.setTitle("Save look");
        dialog.getDialogPane().getStylesheets().add(getClass().getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm());
        dialog.getDialogPane().getStyleClass().add("wardrobe-dialog");
        if (root.getScene() != null) dialog.initOwner(root.getScene().getWindow());
        TextField name = new TextField(item.name()); TextField collection = new TextField(item.collection());
        name.setPromptText("Look name"); collection.setPromptText("Collection (optional)");
        CheckBox favorite = new CheckBox("Add to favorites"); favorite.setSelected(item.favorite());
        VBox form = new VBox(12, new Label("Name"), name, new Label("Collection"), collection, favorite);
        form.setPrefWidth(360); dialog.getDialogPane().setContent(form);
        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        boolean existing = store.items().stream().anyMatch(i -> i.id().equals(item.id()));
        ButtonType removeType = new ButtonType("Remove saved look", ButtonBar.ButtonData.LEFT);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);
        if (existing) dialog.getDialogPane().getButtonTypes().add(removeType);
        dialog.getDialogPane().lookupButton(saveType).disableProperty().bind(name.textProperty().isEmpty());
        dialog.showAndWait().ifPresent(choice -> {
            try {
                if (choice == saveType) {
                    WardrobeItem saved = new WardrobeItem(item.id(), item.kind(), name.getText(), favorite.isSelected(), collection.getText(), item.payload());
                    feedback.runAsync("Saving look", () -> {
                        WardrobeItem hydrated = api.hydrate(saved);
                        try { store.saveItem(hydrated); } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                        return hydrated;
                    }, hydrated -> {
                        if (selected != null && selected.id().equals(item.id())) { selected = hydrated; selectedName.setText(hydrated.name()); }
                        if (tab == Tab.SAVED) load(); updateSelectionActions();
                        feedback.showToast("Look saved", "Ready whenever you are.");
                    });
                } else if (choice == removeType) feedback.runAsync("Removing saved look", () -> {
                    try { store.removeItem(item.id()); return true; } catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
                }, done -> { if (tab == Tab.SAVED) load(); updateSelectionActions(); });
                if (tab == Tab.SAVED) load(); updateSelectionActions();
            } catch (Exception e) { feedback.showToast("Could not save look", message(e)); }
        });
    }

    private String activeProfile() { HytaleAuthSession session = settings.get().getHytaleAuthSession(); return session == null ? "" : session.getUuid(); }
    private String activeUsername() { HytaleAuthSession s = settings.get().getHytaleAuthSession(); return s == null || s.getUsername().isBlank() ? "NPC" : s.getUsername(); }
    private void setStatus(String text) { status.setText(text); status.setVisible(!text.isBlank()); }
    private static JsonNode payload(WardrobeItem item) { try { return JSON.readTree(item.payload()); } catch (Exception e) { return JSON.createObjectNode(); } }
    private static String thumbnail(WardrobeItem item) {
        JsonNode p = payload(item);
        if (item.kind() == WardrobeItem.Kind.SKIN && p.path("skinId").asText("").isBlank() && p.path("username").asText("").isBlank()) return "";
        String username = p.path("username").asText("NPC");
        if (!username.matches("[A-Za-z0-9_]{1,16}")) username = "NPC";
        String skin = java.net.URLEncoder.encode(p.path("skinId").asText(""), java.nio.charset.StandardCharsets.UTF_8);
        String cape = java.net.URLEncoder.encode(p.path("cape").asText(""), java.nio.charset.StandardCharsets.UTF_8);
        return "https://hyvatar.io/render/" + (item.kind() == WardrobeItem.Kind.CAPE ? "cape/" : "full/") + username + "?size=256&rotate=" + (item.kind() == WardrobeItem.Kind.CAPE ? "180" : "-25") + "&skin_id=" + skin + "&cape=" + cape;
    }
    private static String message(Throwable e) { while (e.getCause() != null && e instanceof CompletionException) e = e.getCause(); return e.getMessage() == null ? "Please try again." : e.getMessage(); }
    private static Label label(String text, String style) { Label l = new Label(text); l.getStyleClass().add(style); return l; }
    private static void hideWhenEmpty(Label label) { label.visibleProperty().bind(label.textProperty().isNotEmpty()); label.managedProperty().bind(label.visibleProperty()); }
    private static Button iconButton(String title, LauncherIcons.Glyph glyph, Runnable action) { Button b = secondaryButton(title); b.setGraphic(LauncherIcons.icon(glyph, 15)); b.setOnAction(e -> action.run()); return b; }
    @Override public void close() { disposed = true; request++; preview.dispose(); editor.close(); }
}
