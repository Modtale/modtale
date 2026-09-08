package net.modtale.launcher.ui.wardrobe;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import net.modtale.launcher.hytale.HytaleAuthSession;
import net.modtale.launcher.settings.HytalePathDetector;
import net.modtale.launcher.settings.LauncherSettings;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.wardrobe.*;
import static net.modtale.launcher.ui.common.LauncherUi.*;

/** The installed game's catalog drives every choice; draft changes stay local until applied. */
public final class CosmeticEditorController implements AutoCloseable {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private final WardrobeApiClient api;
    private final WardrobeStore store;
    private final Supplier<LauncherSettings> settings;
    private final LauncherFeedback feedback;
    private final Executor executor;
    private final WardrobePreview preview;
    private final VBox root = new VBox(18);
    private final VBox categoryRail = new VBox(5);
    private final VBox selectionPanel = new VBox(14);
    private final VBox inspector = new VBox(14);
    private final GridPane grid = new GridPane();
    private final FlowPane pagination = new FlowPane(24, 12);
    private final HBox pageButtons = new HBox(4);
    private final TextField jumpPage = new TextField();
    private final javafx.animation.PauseTransition resizePages = new javafx.animation.PauseTransition(javafx.util.Duration.millis(150));
    private int columns = 3, totalPages = 1;
    private final FlowPane colors = new FlowPane(7, 7);
    private final ComboBox<String> variant = new ComboBox<>();
    private final Label state = text("Loading the character creator…", "wardrobe-muted");
    private final Label choiceName = text("Your look", "wardrobe-selected-title");
    private final Label requirement = text("", "wardrobe-muted");
    private final Label changes = text("Nothing applied yet", "wardrobe-muted");
    private final Button undo = iconButton("Undo", LauncherIcons.Glyph.UNDO, this::undo);
    private final Button redo = iconButton("Redo", LauncherIcons.Glyph.REDO, this::redo);
    private final Button reset = button("Reset", LauncherIcons.Glyph.RESTORE, this::reset);
    private final Button remove = secondaryButton("Remove item");
    private final Button apply = primaryButton("Apply outfit");
    private final MenuItem saveOfficial = new MenuItem("Save to Hytale");
    private final MenuButton saveMenu = new MenuButton("Save");
    private final CheckBox ownedOnly = new CheckBox("Owned only");
    private final Button previous = pageIcon(LauncherIcons.Glyph.CHEVRON_LEFT, "Previous Page");
    private final Button next = pageIcon(LauncherIcons.Glyph.CHEVRON_RIGHT, "Next Page");
    private CosmeticCatalogClient catalog;
    private Path assets;
    private OutfitDraft draft;
    private String category = "haircut", selectedAsset = "";
    private int page = 1;
    private long generation, accountGeneration, draftRevision;
    private WardrobeApiClient.SkinSlots officialSlots;
    private String accountProfileLoaded = "";
    private Map<String, Set<String>> unlocked = Map.of();
    private boolean permissionsKnown, accountLoading;
    private boolean loading, applying, disposed, settingVariants;
    private List<CosmeticOption> combinations = List.of();
    private String pendingCape;
    private final Map<String, Button> categoryButtons = new LinkedHashMap<>();

    public CosmeticEditorController(WardrobeApiClient api, WardrobeStore store, Supplier<LauncherSettings> settings,
            LauncherFeedback feedback, Executor executor) {
        this.api = api; this.store = store; this.settings = settings; this.feedback = feedback; this.executor = executor;
        preview = new WardrobePreview(executor);
        build();
    }
    public Node view() { return root; }
    JsonNode draftSnapshot() { return draft == null ? JSON.createObjectNode() : draft.skin(); }
    Path assetsForPreview() { return assets == null ? findAssets() : assets; }

    public void refresh() {
        if (catalog == null && !loading) loadCatalog(findAssets());
        if (!accountProfileLoaded.equals(activeProfile())) {
            officialSlots = null; permissionsKnown = false; unlocked = Map.of();
            accountProfileLoaded = activeProfile(); loadAccountWardrobe();
        } else if (officialSlots == null && !accountLoading) loadAccountWardrobe();
        updateActions();
    }

    private Path findAssets() {
        Path game = settings.get().getHytaleGamePath().isBlank() ? HytalePathDetector.defaultGameDirectory() : settings.get().hytaleGameDirectory();
        Path file = game.resolve("Assets.zip");
        if (Files.isRegularFile(file)) return file;
        if (game.getFileName() != null && game.getFileName().toString().equalsIgnoreCase("Client") && game.getParent() != null)
            return game.getParent().resolve("Assets.zip");
        return file;
    }

    private void build() {
        root.getStyleClass().add("cosmetic-editor"); root.setMinWidth(0);
        state.setWrapText(true);
        hideWhenEmpty(state); hideWhenEmpty(requirement); hideWhenEmpty(changes);
        Button current = button("Load current look", LauncherIcons.Glyph.REFRESH_CW, this::loadCurrent);
        FlowPane toolbar = new FlowPane(8, 8, current, undo, redo, reset, ownedOnly); toolbar.setAlignment(Pos.CENTER_LEFT);
        ownedOnly.setSelected(true);
        ownedOnly.getStyleClass().add("cosmetic-owned-filter");
        ownedOnly.setOnAction(e -> { page = 1; browse(); });
        categoryRail.setMinWidth(0); categoryRail.setPrefWidth(160); categoryRail.getStyleClass().add("cosmetic-category-rail");
        ScrollPane categories = new ScrollPane(categoryRail); categories.setFitToWidth(true);
        categories.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER); categories.setPrefViewportHeight(670);
        categories.setMinWidth(165); categories.setPrefWidth(165); categories.getStyleClass().add("cosmetic-category-scroll");
        grid.setId("cosmetic-cards"); grid.setMinWidth(0); grid.setHgap(12); grid.setVgap(12);
        resizePages.setOnFinished(e -> browse());
        grid.widthProperty().addListener((o, before, after) -> {
            int count = Math.max(1, Math.min(25, (int) ((after.doubleValue() + 12) / 177)));
            if (columns == count) return;
            columns = count; page = 1;
            layoutCards();
            if (catalog != null) { generation++; resizePages.playFromStart(); }
        });
        configurePagination();
        selectionPanel.getChildren().addAll(grid, pagination);
        selectionPanel.setMinWidth(0); HBox.setHgrow(selectionPanel, Priority.ALWAYS);
        inspector.getStyleClass().add("wardrobe-inspector"); inspector.setPrefWidth(310); inspector.setMinWidth(270);
        inspector.setMaxHeight(Region.USE_PREF_SIZE);
        Node previewNode = preview.view(); if (previewNode instanceof Region r) { r.setPrefHeight(330); r.setMinHeight(270); }
        choiceName.visibleProperty().bind(choiceName.textProperty().isNotEmpty()
                .and(choiceName.textProperty().isNotEqualTo("Your character"))
                .and(choiceName.textProperty().isNotEqualTo("Your look")));
        choiceName.managedProperty().bind(choiceName.visibleProperty());
        choiceName.setWrapText(true); requirement.setWrapText(true); changes.setWrapText(true);
        variant.getStyleClass().add("wardrobe-filter"); variant.setMaxWidth(Double.MAX_VALUE);
        variant.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(String value) { return value == null ? "" : humanize(value); }
            @Override public String fromString(String value) { return value; }
        });
        variant.setOnAction(e -> { if (!settingVariants) chooseVariant(); });
        remove.setOnAction(e -> { if (draft != null) { draft.remove(category); changed(); showOptions(selectedAsset); } });
        remove.setMaxWidth(Double.MAX_VALUE);
        MenuItem saveLocal = new MenuItem("Save to Saved looks"); saveLocal.setOnAction(e -> saveLocal());
        saveMenu.getStyleClass().addAll("btn", "secondary", "cosmetic-save-menu");
        saveMenu.getItems().setAll(saveLocal, saveOfficial);
        saveMenu.setMinWidth(Region.USE_PREF_SIZE);
        apply.setMaxWidth(Double.MAX_VALUE); apply.setOnAction(e -> applyDraft());
        saveOfficial.setOnAction(e -> saveOfficial());
        HBox actions = new HBox(8, saveMenu, apply);
        HBox.setHgrow(apply, Priority.ALWAYS);
        remove.getStyleClass().add("cosmetic-quiet-action");
        inspector.getChildren().addAll(previewNode, choiceName,
                colors, variant, requirement, remove, changes, actions);
        HBox workspace = new HBox(18, categories, selectionPanel, inspector); workspace.setAlignment(Pos.TOP_LEFT);
        workspace.setMinWidth(0);
        root.getChildren().addAll(toolbar, state, workspace);
        root.widthProperty().addListener((o, a, b) -> {
            inspector.setPrefWidth(b.doubleValue() < 1100 ? 270 : 310);
        });
        updateActions();
    }

    private void loadCatalog(Path source) {
        if (disposed) return;
        long ticket = ++generation; loading = true; state.setText("Reading Hytale’s character creator…");
        CompletableFuture.supplyAsync(() -> {
            try { return new CosmeticCatalogClient(source); } catch (IOException e) { throw new UncheckedIOException(e); }
        }, executor).whenComplete((value, error) -> Platform.runLater(() -> {
            if (disposed || ticket != generation) return;
            loading = false;
            if (error != null) { state.setText("Install Hytale or set its game directory in Settings. " + message(error)); return; }
            assets = source; catalog = value;
            if (draft == null) { draft = new OutfitDraft(catalog.defaultSkin()); }
            if (pendingCape != null) { draft.choose("cape", pendingCape); pendingCape = null; category = "cape"; }
            renderCategories(); browse(); renderPreview(); updateActions();
            state.setText("");
        }));
    }

    private void renderCategories() {
        categoryRail.getChildren().clear(); categoryButtons.clear();
        for (CosmeticCategory entry : catalog.categories()) {
            Button button = new Button(entry.label()); button.getStyleClass().add("cosmetic-category");
            button.setMaxWidth(Double.MAX_VALUE); button.setAlignment(Pos.CENTER_LEFT);
            button.setOnAction(e -> { category = entry.key(); page = 1; browse(); });
            categoryButtons.put(entry.key(), button); categoryRail.getChildren().add(button);
        }
    }

    private void browse() {
        if (catalog == null || disposed) return;
        resizePages.stop();
        long ticket = ++generation; String key = category; int requestedPage = page; int pageSize = columns * 4;
        boolean filterOwned = ownedOnly.isSelected(); boolean known = permissionsKnown;
        Map<String, Set<String>> permissions = unlocked;
        categoryButtons.forEach((id, button) -> button.pseudoClassStateChanged(SELECTED, id.equals(key)));
        state.setText("Loading cosmetics…");
        CompletableFuture.supplyAsync(() -> {
            try {
                if (!filterOwned) return catalog.browseAssets(key, "", requestedPage, pageSize);
                List<CosmeticOption> choices = new ArrayList<>();
                if (known) {
                    int sourcePage = 1;
                    CosmeticCatalogClient.Page batch;
                    do {
                        batch = catalog.browseAssets(key, "", sourcePage++, 100);
                        for (CosmeticOption option : batch.options())
                            if (permissions.getOrDefault(key, Set.of()).contains(option.assetId())) choices.add(option);
                    } while (batch.hasNext());
                }
                int from = Math.min(choices.size(), (requestedPage - 1) * pageSize), to = Math.min(choices.size(), from + pageSize);
                return new CosmeticCatalogClient.Page(List.copyOf(choices.subList(from, to)), requestedPage, pageSize, choices.size(), to < choices.size(), true, catalog.source());
            } catch (IOException e) { throw new UncheckedIOException(e); }
        }, executor).whenComplete((result, error) -> Platform.runLater(() -> {
            if (disposed || ticket != generation) return;
            grid.getChildren().clear();
            if (error != null) { state.setText(message(error)); return; }
            state.setText("");
            for (CosmeticOption option : result.options()) grid.getChildren().add(optionCard(option));
            if (grid.getChildren().isEmpty()) grid.getChildren().add(text(ownedOnly.isSelected() && !permissionsKnown
                    ? "Owned items unavailable."
                    : "No matching items", "wardrobe-muted"));
            layoutCards();
            totalPages = Math.max(1, (result.total() + pageSize - 1) / pageSize);
            updatePagination();
            String selected = draft == null ? "" : draft.selected(key);
            String assetId = selected.contains(".") ? selected.substring(0, selected.indexOf('.')) : selected;
            showOptions(assetId); updateActions();
        }));
    }

    private void layoutCards() {
        grid.getColumnConstraints().clear();
        for (int i = 0; i < columns; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(100.0 / columns); column.setMinWidth(0);
            grid.getColumnConstraints().add(column);
        }
        for (int i = 0; i < grid.getChildren().size(); i++) {
            GridPane.setColumnIndex(grid.getChildren().get(i), i % columns);
            GridPane.setRowIndex(grid.getChildren().get(i), i / columns);
        }
    }

    private void configurePagination() {
        pagination.setId("cosmetic-pagination"); pagination.getStyleClass().add("pagination-nav");
        pagination.setAlignment(Pos.CENTER); pagination.setMaxWidth(Double.MAX_VALUE);
        HBox shell = new HBox(4, previous, pageButtons, next);
        shell.getStyleClass().add("pagination-page-shell"); shell.setAlignment(Pos.CENTER);
        pageButtons.getStyleClass().add("pagination-page-buttons"); pageButtons.setAlignment(Pos.CENTER);
        previous.setOnAction(e -> goToPage(page - 1)); next.setOnAction(e -> goToPage(page + 1));
        Label label = text("JUMP", "pagination-jump-label");
        jumpPage.setPromptText("#"); jumpPage.getStyleClass().add("pagination-jump-input");
        Button jump = pageIcon(LauncherIcons.Glyph.CORNER_DOWN_LEFT, "Go to page");
        jump.getStyleClass().add("pagination-jump-button");
        jump.disableProperty().bind(jumpPage.textProperty().isEmpty());
        Runnable submit = () -> {
            try { goToPage(Integer.parseInt(jumpPage.getText().trim())); }
            catch (NumberFormatException ignored) { }
            jumpPage.clear();
        };
        jump.setOnAction(e -> submit.run()); jumpPage.setOnAction(e -> submit.run());
        HBox jumpShell = new HBox(12, label, jumpPage, jump);
        jumpShell.getStyleClass().add("pagination-jump-shell"); jumpShell.setAlignment(Pos.CENTER);
        pagination.getChildren().setAll(shell, jumpShell); updatePagination();
    }

    private void updatePagination() {
        pagination.setVisible(totalPages > 1); pagination.setManaged(totalPages > 1);
        previous.setDisable(page <= 1); next.setDisable(page >= totalPages);
        pageButtons.getChildren().clear();
        int last = 0;
        for (int target = 1; target <= totalPages; target++) {
            if (totalPages > 7 && target != 1 && target != totalPages && Math.abs(target - page) > 1) continue;
            if (last > 0 && target > last + 1) pageButtons.getChildren().add(text("...", "pagination-dots"));
            int destination = target;
            Button button = new Button(Integer.toString(target)); button.getStyleClass().add("pagination-button");
            button.setMinSize(36, 36); button.setPrefSize(36, 36); button.setMaxSize(36, 36);
            button.setAccessibleText("Page " + target); button.pseudoClassStateChanged(SELECTED, target == page);
            button.setOnAction(e -> goToPage(destination)); pageButtons.getChildren().add(button); last = target;
        }
    }

    private void goToPage(int target) {
        if (target < 1 || target > totalPages || target == page) return;
        page = target; browse();
    }

    private static Button pageIcon(LauncherIcons.Glyph icon, String label) {
        Button button = new Button(); button.getStyleClass().addAll("pagination-button", "pagination-icon-button");
        button.setGraphic(LauncherIcons.icon(icon, 16)); button.setAccessibleText(label); button.setTooltip(new Tooltip(label));
        button.setMinSize(36, 36); button.setPrefSize(36, 36); button.setMaxSize(36, 36); return button;
    }

    private Node optionCard(CosmeticOption option) {
        ImageView image = new ImageView(new Image(cosmeticImage(option), 150, 140, true, true, true));
        image.setFitWidth(140); image.setFitHeight(135); image.setPreserveRatio(true);
        Label fallback = text(option.label(), "wardrobe-card-fallback"); fallback.setMaxWidth(130); fallback.setWrapText(true);
        fallback.visibleProperty().bind(image.getImage().progressProperty().lessThan(1).or(image.getImage().errorProperty()));
        StackPane art = new StackPane(fallback, image); art.getStyleClass().add("cosmetic-card-art"); art.setPrefSize(145, 145);
        Label name = text(option.label(), "wardrobe-card-name"); name.setMaxWidth(145);
        VBox contents = new VBox(8, art, name);
        if (permissionsKnown && !owned(option)) contents.getChildren().add(text("Locked", "wardrobe-card-detail"));
        Button button = new Button(); button.setGraphic(contents); button.getStyleClass().add("wardrobe-card");
        button.setMinWidth(0); button.setMaxWidth(Double.MAX_VALUE);
        art.prefWidthProperty().bind(button.widthProperty().subtract(22));
        name.maxWidthProperty().bind(button.widthProperty().subtract(22));
        button.setUserData(option.assetId());
        button.setAccessibleText("Choose " + option.label()); button.setTooltip(new Tooltip(option.label()));
        button.setOnAction(e -> { draft.choose(category, option.id()); selectedAsset = option.assetId(); changed(); showOptions(option.assetId()); });
        button.pseudoClassStateChanged(SELECTED, draft != null && (draft.selected(category).equals(option.assetId()) || draft.selected(category).startsWith(option.assetId() + ".")));
        return button;
    }

    private void showOptions(String assetId) {
        colors.setDisable(true); variant.setDisable(true);
        selectedAsset = assetId; combinations = List.of();
        if (assetId == null || assetId.isBlank() || catalog == null) {
            colors.getChildren().clear(); colors.setVisible(false); colors.setManaged(false);
            variant.setVisible(false); variant.setManaged(false);
            choiceName.setText("Your character"); requirement.setText(""); return;
        }
        String key = category; long ticket = generation;
        CompletableFuture.supplyAsync(() -> {
            try { return catalog.options(key, assetId); } catch (IOException e) { throw new UncheckedIOException(e); }
        }, executor).whenComplete((options, error) -> Platform.runLater(() -> {
            if (disposed || ticket != generation || !key.equals(category) || !assetId.equals(selectedAsset)) return;
            if (error != null) { requirement.setText(message(error)); return; }
            colors.getChildren().clear(); settingVariants = true; variant.getItems().clear(); settingVariants = false;
            colors.setDisable(false); variant.setDisable(false);
            combinations = options;
            CosmeticOption current = options.stream().filter(o -> o.id().equals(draft.selected(key))).findFirst().orElse(options.isEmpty() ? null : options.getFirst());
            if (current == null) return;
            choiceName.setText(current.label());
            requirement.setText(permissionsKnown && !owned(current) ? "Locked" : "");
            requirement.setTooltip(new Tooltip("Not unlocked for this account. You can preview and save it locally."));
            LinkedHashMap<String, CosmeticOption> shades = new LinkedHashMap<>();
            options.forEach(o -> shades.putIfAbsent(o.colorId(), o));
            colors.setVisible(shades.size() > 1); colors.setManaged(colors.isVisible());
            shades.forEach((color, option) -> {
                Button swatch = new Button(); swatch.getStyleClass().add("cosmetic-swatch");
                String hex = option.swatches().isEmpty() ? "#94a3b8" : option.swatches().getFirst();
                try { swatch.setGraphic(new Circle(10, Color.web(hex))); } catch (IllegalArgumentException e) { swatch.setGraphic(new Circle(10, Color.GRAY)); }
                swatch.setTooltip(new Tooltip(color.isBlank() ? "Default color" : humanize(color)));
                swatch.setAccessibleText("Color " + color); swatch.pseudoClassStateChanged(SELECTED, color.equals(current.colorId()));
                swatch.setOnAction(e -> {
                    CosmeticOption chosen = options.stream().filter(o -> o.colorId().equals(color) && o.variantId().equals(current.variantId())).findFirst().orElse(option);
                    draft.choose(key, chosen.id()); changed(); showOptions(assetId);
                }); colors.getChildren().add(swatch);
            });
            settingVariants = true;
            variant.getItems().setAll(options.stream().filter(o -> o.colorId().equals(current.colorId())).map(CosmeticOption::variantId).distinct().map(v -> v.isBlank() ? "Default style" : v).toList());
            variant.setValue(current.variantId().isBlank() ? "Default style" : current.variantId());
            variant.setVisible(variant.getItems().size() > 1); variant.setManaged(variant.isVisible()); settingVariants = false;
        }));
    }

    private void chooseVariant() {
        if (draft == null || variant.getValue() == null) return;
        CosmeticOption current = combinations.stream().filter(o -> o.id().equals(draft.selected(category))).findFirst().orElse(null);
        if (current == null) return;
        String id = "Default style".equals(variant.getValue()) ? "" : variant.getValue();
        combinations.stream().filter(o -> o.colorId().equals(current.colorId()) && o.variantId().equals(id)).findFirst()
                .ifPresent(o -> { draft.choose(category, o.id()); changed(); });
    }

    public void edit(WardrobeItem item) {
        if (draft != null && draft.dirty() && !confirm("Replace your draft?", "Discard the unapplied edits and open this look?")) return;
        long revision = draftRevision;
        feedback.runAsync("Preparing outfit", () -> api.hydrate(item), hydrated -> {
            if (disposed || revision != draftRevision) return;
            try { draft = new OutfitDraft(JSON.readTree(hydrated.payload()).path("skin")); refresh(); changed(); browse(); }
            catch (IOException e) { feedback.showToast("Could not open outfit", message(e)); }
        });
    }

    private void loadCurrent() {
        if (activeProfile().isBlank()) return;
        if (draft != null && draft.dirty() && !confirm("Replace your draft?", "Load your current Hytale look and discard the unapplied edits?")) return;
        String target = activeProfile();
        long revision = draftRevision;
        feedback.runAsync("Loading Hytale outfit", () -> api.currentSkin(settings.get()), item -> {
            if (disposed || revision != draftRevision) return;
            if (!target.equals(activeProfile())) { feedback.showToast("Account changed", "Load the outfit again for the selected account."); return; }
            try { draft = new OutfitDraft(JSON.readTree(item.payload()).path("skin")); changed(); browse(); }
            catch (IOException e) { feedback.showToast("Could not load outfit", message(e)); }
        });
    }

    private void changed() {
        draftRevision++;
        updateActions(); renderPreview();
        if (draft != null) for (Node card : grid.getChildren()) {
            String asset = String.valueOf(card.getUserData());
            card.pseudoClassStateChanged(SELECTED, draft.selected(category).equals(asset) || draft.selected(category).startsWith(asset + "."));
        }
    }

    public void editCape(String cape) {
        if (cape == null || cape.isBlank()) return;
        category = "cape"; page = 1;
        if (draft == null) { pendingCape = cape; refresh(); }
        else { draft.choose("cape", cape); changed(); browse(); }
    }
    private void renderPreview() {
        if (draft == null || assets == null) return;
        preview.showLocal(assets, draft.skin());
    }
    private void updateActions() {
        boolean hasDraft = draft != null;
        boolean locked = hasLockedSelection();
        undo.setDisable(!hasDraft || !draft.canUndo() || applying); redo.setDisable(!hasDraft || !draft.canRedo() || applying);
        reset.setDisable(!hasDraft || !draft.dirty() || applying); remove.setDisable(!hasDraft || !OutfitDraft.canRemove(category) || draft.selected(category).isBlank() || applying);
        remove.setVisible(hasDraft && OutfitDraft.canRemove(category) && !draft.selected(category).isBlank()); remove.setManaged(remove.isVisible());
        apply.setDisable(!hasDraft || activeProfile().isBlank() || applying || locked);
        saveOfficial.setDisable(!hasDraft || activeProfile().isBlank() || applying || locked);
        apply.setTooltip(new Tooltip(locked ? "This outfit contains locked cosmetics." : "Apply to " + activeUsername() + " and save the previous look locally."));
        saveMenu.setDisable(!hasDraft || applying);
        apply.setText(applying ? "Applying…" : "Apply");
        changes.setText(locked ? "Contains locked items" : hasDraft && draft.dirty() ? "Unapplied changes" : "");
    }
    private boolean hasLockedSelection() {
        if (!permissionsKnown || draft == null || catalog == null) return false;
        for (CosmeticCategory entry : catalog.categories()) {
            String selection = draft.selected(entry.key());
            if (!selection.isBlank() && !unlocked.getOrDefault(entry.key(), Set.of()).contains(selection.split("\\.", 2)[0])) return true;
        }
        return false;
    }
    private void undo() { if (draft != null) { draft.undo(); changed(); browse(); } }
    private void redo() { if (draft != null) { draft.redo(); changed(); browse(); } }
    private void reset() { if (draft != null) { draft.reset(); changed(); browse(); } }
    private void saveLocal() {
        if (draft == null) return;
        askName("Save outfit", "My outfit").ifPresent(name -> {
            WardrobeItem item = item(name, draft.skin());
            feedback.runAsync("Saving outfit", () -> { try { store.saveItem(item); return true; } catch (IOException e) { throw new UncheckedIOException(e); } },
                    done -> feedback.showToast("Outfit saved", "Find it under Saved looks."));
        });
    }

    private void applyDraft() {
        if (draft == null || applying || activeProfile().isBlank()) return;
        String target = activeProfile(); String username = activeUsername(); JsonNode skin = draft.skin();
        WardrobeItem selected = item("Custom outfit", skin);
        applying = true; updateActions();
        feedback.runAsync("Applying your outfit", () -> {
            WardrobeItem backup = api.currentSkin(settings.get());
            if (!target.equals(activeProfile())) throw new IllegalStateException("The active Hytale account changed. Apply again.");
            try { store.saveItem(new WardrobeItem(backup.id(), backup.kind(), username + " · previous look", false, "Previous looks", backup.payload())); }
            catch (IOException e) { throw new UncheckedIOException(e); }
            api.apply(selected, settings.get(), UUID.fromString(target)); return true;
        }, done -> {
            applying = false; if (draft.skin().equals(skin)) draft.markApplied(); updateActions();
            feedback.showToast("Outfit updated", "Applied to " + username + ". Your previous look is saved.");
        }, error -> { applying = false; updateActions(); });
    }


    private boolean owned(CosmeticOption option) {
        return permissionsKnown && unlocked.getOrDefault(option.category(), Set.of()).contains(option.assetId());
    }

    private void loadAccountWardrobe() {
        String target = activeProfile(); long ticket = ++accountGeneration;
        if (target.isBlank()) { accountLoading = false; return; }
        accountLoading = true;
        CompletableFuture.supplyAsync(() -> api.slots(settings.get()), executor).whenComplete((slots, error) -> Platform.runLater(() -> {
            if (disposed || ticket != accountGeneration || !target.equals(activeProfile())) return;
            accountLoading = false;
            if (error != null) return;
            officialSlots = slots; accountProfileLoaded = target; updateActions();
        }));
        CompletableFuture.supplyAsync(() -> api.unlockedCosmetics(settings.get()), executor).whenComplete((rights, error) -> Platform.runLater(() -> {
            if (disposed || ticket != accountGeneration || !target.equals(activeProfile())) return;
            permissionsKnown = error == null; unlocked = error == null ? rights : Map.of();
            if (catalog != null) browse();
        }));
    }

    private void saveOfficial() {
        if (draft == null || activeProfile().isBlank()) return;
        JsonNode skin = draft.skin();
        if (officialSlots != null && officialSlots.slots().size() >= officialSlots.max()) {
            feedback.showToast("Outfit slots full", "Save locally, or manage your outfits in Hytale."); return;
        }
        askName("Create Hytale outfit", "My outfit").ifPresent(name -> mutateSlot("Saving Hytale outfit",
                target -> api.createSkin(settings.get(), name, skin, target)));
    }

    private Optional<String> askName(String title, String initial) {
        TextInputDialog dialog = new TextInputDialog(initial); dialog.setTitle(title); dialog.setHeaderText(title); style(dialog);
        dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(
                () -> dialog.getEditor().getText().isBlank() || dialog.getEditor().getText().trim().length() > 120, dialog.getEditor().textProperty()));
        return dialog.showAndWait().map(String::trim).filter(name -> !name.isBlank());
    }
    private void mutateSlot(String status, java.util.function.Consumer<UUID> work) {
        if (applying || activeProfile().isBlank()) return;
        UUID target = UUID.fromString(activeProfile()); applying = true; updateActions();
        feedback.runAsync(status, () -> { work.accept(target); return true; }, done -> {
            applying = false; updateActions(); loadAccountWardrobe(); feedback.showToast("Hytale outfits updated", "Your change was saved to Hytale.");
        }, error -> { applying = false; updateActions(); });
    }

    private static WardrobeItem item(String name, JsonNode skin) { return new WardrobeItem(UUID.randomUUID(), WardrobeItem.Kind.SKIN, name, false, "Custom outfits", JSON.createObjectNode().set("skin", skin).toString()); }
    private boolean confirm(String title, String message) { Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL); alert.setTitle(title); alert.setHeaderText(null); style(alert); return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK; }
    private void style(Dialog<?> dialog) { if (root.getScene() != null) dialog.initOwner(root.getScene().getWindow()); dialog.getDialogPane().getStyleClass().add("wardrobe-dialog"); dialog.getDialogPane().getStylesheets().add(getClass().getResource("/net/modtale/launcher/ui/nativefx/launcher.css").toExternalForm()); }
    private String activeProfile() { HytaleAuthSession s = settings.get().getHytaleAuthSession(); return s == null ? "" : s.getUuid(); }
    private String activeUsername() { HytaleAuthSession s = settings.get().getHytaleAuthSession(); return s == null ? "" : s.getUsername(); }
    private static String cosmeticImage(CosmeticOption option) { return "https://hyvatar.io/render/cosmetic/" + encode(option.category()) + "/" + encode(option.id()) + "?size=256&rotate=25"; }
    private static String encode(String s) { return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"); }
    private static String humanize(String s) { return s.replace('_', ' ').replaceAll("([a-z])([A-Z])", "$1 $2"); }
    private static Label text(String text, String style) { Label l = new Label(text); l.getStyleClass().add(style); return l; }
    private static void hideWhenEmpty(Label label) { label.visibleProperty().bind(label.textProperty().isNotEmpty()); label.managedProperty().bind(label.visibleProperty()); }
    private static Button button(String text, LauncherIcons.Glyph icon, Runnable action) { Button b = secondaryButton(text); b.setGraphic(LauncherIcons.icon(icon, 14)); b.setOnAction(e -> action.run()); return b; }
    private static Button iconButton(String label, LauncherIcons.Glyph icon, Runnable action) {
        Button button = button("", icon, action);
        button.getStyleClass().add("icon-only-button");
        button.setMinSize(40, 40); button.setPrefSize(40, 40);
        button.setAccessibleText(label); button.setTooltip(new Tooltip(label));
        return button;
    }
    private static String message(Throwable t) { while (t instanceof CompletionException && t.getCause() != null) t = t.getCause(); return t.getMessage() == null ? "Please try again." : t.getMessage(); }
    @Override public void close() { disposed = true; resizePages.stop(); generation++; accountGeneration++; preview.dispose(); }
}
