package net.modtale.launcher.ui.browse.controls;

import static net.modtale.launcher.ui.common.LauncherUi.dangerButton;
import static net.modtale.launcher.ui.common.LauncherUi.pseudo;
import static net.modtale.launcher.ui.common.LauncherUi.styleInput;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.modtale.launcher.model.project.GameVersionCatalog;
import net.modtale.launcher.ui.common.GameVersionDropdown;
import net.modtale.launcher.ui.common.GameVersionGroups;
import net.modtale.launcher.ui.common.LauncherIcons;

public final class ProjectBrowseFilterOptions {

    private final Runnable onSearch;
    private final Runnable onChange;
    private final Runnable onResetTags;
    private final VBox popover = new VBox(20);
    private final GameVersionDropdown gameVersionDropdown = GameVersionDropdown.multiSelect();
    private final Button preReleaseToggle = new Button("Pre Releases");
    private final Button openSourceButton = new Button();
    private final TextField customMinFavoritesField = new TextField();
    private final TextField customMinDownloadsField = new TextField();
    private final DatePicker updatedAfterPicker = new DatePicker();
    private final Map<Integer, Button> minFavoritesButtons = new LinkedHashMap<>();
    private final Map<Integer, Button> minDownloadsButtons = new LinkedHashMap<>();
    private final Map<DateRangePreset, Button> dateRangeButtons = new LinkedHashMap<>();
    private GameVersionFilterCatalog gameVersions = GameVersionFilterCatalog.empty();
    private Integer minFavoritesPreset;
    private Integer minDownloadsPreset;
    private DateRangePreset dateRangePreset = DateRangePreset.ANY;
    private boolean showPreReleases;
    private boolean openSourceOnly;
    private boolean downloadSort;
    private boolean suppressSearch;

    public ProjectBrowseFilterOptions(Runnable onSearch, Runnable onChange, Runnable onResetTags) {
        this.onSearch = onSearch;
        this.onChange = onChange;
        this.onResetTags = onResetTags;
        configureInputs();
        configurePopover();
    }

    public VBox popover() {
        return popover;
    }

    public int activeFilterCount() {
        int count = 0;
        if (selectedGameVersion() != null) {
            count++;
        }
        if (openSourceOnly) {
            count++;
        }
        if (selectedMinimumFavorites() != null) {
            count++;
        }
        if (selectedMinimumDownloads() != null) {
            count++;
        }
        if (!downloadSort && selectedDateRange() != null) {
            count++;
        }
        return count;
    }

    public void setDownloadSort(boolean downloadSort) {
        this.downloadSort = downloadSort;
    }

    public String selectedGameVersion() {
        return gameVersionDropdown.selectedQuery();
    }

    public Boolean selectedOpenSource() {
        return openSourceOnly ? Boolean.TRUE : null;
    }

    public Integer selectedMinimumFavorites() {
        Integer custom = parsePositiveInteger(customMinFavoritesField.getText());
        if (custom != null) {
            return custom;
        }
        return minFavoritesPreset;
    }

    public Integer selectedMinimumDownloads() {
        Integer custom = parsePositiveInteger(customMinDownloadsField.getText());
        if (custom != null) {
            return custom;
        }
        return minDownloadsPreset;
    }

    public String selectedDateRange() {
        LocalDate customDate = updatedAfterPicker.getValue();
        if (customDate != null) {
            return customDate.toString();
        }
        return dateRangePreset.apiValue();
    }

    public void replaceGameVersions(List<String> versions) {
        String selected = selectedGameVersion();
        withSuppressedSearch(() -> {
            gameVersions = GameVersionFilterCatalog.fromVersions(versions);
            showPreReleases = false;
            updateGameVersionOptions(selected);
        });
        refreshAndNotify(false);
    }

    public void replaceGameVersionCatalog(GameVersionCatalog catalog) {
        if (catalog == null) {
            replaceGameVersions(List.of());
            return;
        }

        String selected = selectedGameVersion();
        withSuppressedSearch(() -> {
            gameVersions = GameVersionFilterCatalog.from(catalog);
            showPreReleases = false;
            updateGameVersionOptions(selected);
        });
        refreshAndNotify(false);
    }

    public void selectDateRange(String dateRange) {
        withSuppressedSearch(() -> {
            dateRangePreset = DateRangePreset.fromApiValue(dateRange);
            updatedAfterPicker.setValue(null);
        });
        refreshAndNotify(true);
    }

    private void configureInputs() {
        gameVersionDropdown.setOnSelectionChange(ignored -> changedAndSearch());
        gameVersionDropdown.getStyleClass().add("filter-game-version-dropdown");
        gameVersionDropdown.setMaxListHeight(192);
        preReleaseToggle.getStyleClass().add("pre-release-toggle");
        preReleaseToggle.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.CHECK, 11));
        preReleaseToggle.setOnAction(event -> togglePreReleases());
        openSourceButton.getStyleClass().add("filter-toggle-button");
        openSourceButton.setMaxWidth(Double.MAX_VALUE);
        HBox openSourceContent = openSourceButtonContent();
        openSourceContent.prefWidthProperty().bind(openSourceButton.widthProperty().subtract(24));
        openSourceButton.setGraphic(openSourceContent);
        openSourceButton.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        openSourceButton.setAccessibleText("Open source license filter");
        openSourceButton.setOnAction(event -> toggleOpenSource());
        updatedAfterPicker.setOnAction(event -> {
            if (updatedAfterPicker.getValue() != null) {
                dateRangePreset = DateRangePreset.ANY;
            }
            changedAndSearch();
        });
        customMinFavoritesField.setPromptText("Custom min favorites...");
        customMinFavoritesField.textProperty().addListener((observable, oldValue, newValue) -> changedAndSearch());
        customMinDownloadsField.setPromptText("Custom min downloads...");
        customMinDownloadsField.textProperty().addListener((observable, oldValue, newValue) -> changedAndSearch());
        updatedAfterPicker.setPromptText("Pick a date");
        styleInput(customMinFavoritesField, customMinDownloadsField);
        customMinFavoritesField.getStyleClass().add("filter-input");
        customMinDownloadsField.getStyleClass().add("filter-input");
        customMinFavoritesField.getStyleClass().add("filter-input-with-icon");
        customMinDownloadsField.getStyleClass().add("filter-input-with-icon");
        updatedAfterPicker.getStyleClass().addAll("date-picker", "filter-date-picker");
    }

    private void configurePopover() {
        popover.getStyleClass().add("filter-popover");
        popover.setPrefWidth(288);
        popover.setMaxWidth(288);
        popover.setVisible(false);
        popover.setManaged(false);
        popover.addEventHandler(ScrollEvent.SCROLL, ScrollEvent::consume);

        Node version = gameVersionSection();
        Node license = filterSection("LICENSE", openSourceButton);
        Node favorites = filterSection(
                "MINIMUM FAVORITES",
                numberPresetRow(minFavoritesButtons, List.of(
                        new NumberPreset("Any", null),
                        new NumberPreset("10+", 10),
                        new NumberPreset("50+", 50),
                        new NumberPreset("100+", 100)
                ), this::selectMinimumFavoritesPreset),
                inputWithIcon(customMinFavoritesField, LauncherIcons.Glyph.HEART)
        );
        Node downloads = filterSection(
                "DOWNLOADS",
                numberPresetRow(minDownloadsButtons, List.of(
                        new NumberPreset("Any", null),
                        new NumberPreset("1k+", 1_000),
                        new NumberPreset("5k+", 5_000),
                        new NumberPreset("10k+", 10_000)
                ), this::selectMinimumDownloadsPreset),
                inputWithIcon(customMinDownloadsField, LauncherIcons.Glyph.DOWNLOAD)
        );
        Node updated = filterSection(
                "LAST UPDATED",
                datePresetRow(),
                datePickerWithIcon()
        );

        Button reset = dangerButton("Reset Filters");
        reset.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.ROTATE_CCW, 14));
        reset.getStyleClass().add("filter-reset-button");
        reset.setMaxWidth(Double.MAX_VALUE);
        reset.setOnAction(event -> reset(true));
        VBox resetSection = new VBox(reset);
        resetSection.getStyleClass().add("filter-reset-section");

        popover.getChildren().setAll(version, license, favorites, downloads, updated, resetSection);
        refreshPresetButtons();
    }

    private Node gameVersionSection() {
        VBox box = new VBox(6);
        box.getStyleClass().add("filter-section");
        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label labelNode = new Label("GAME VERSION");
        labelNode.getStyleClass().add("filter-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(labelNode, spacer, preReleaseToggle);
        box.getChildren().addAll(header, gameVersionDropdown);
        gameVersionDropdown.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private HBox openSourceButtonContent() {
        HBox content = new HBox(8);
        content.getStyleClass().add("filter-toggle-content");
        content.setAlignment(Pos.CENTER_LEFT);
        Node check = LauncherIcons.icon(LauncherIcons.Glyph.CHECK, 13);
        check.getStyleClass().add("filter-toggle-check");
        Label label = new Label("Open Source");
        label.getStyleClass().add("filter-toggle-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Node scale = LauncherIcons.icon(LauncherIcons.Glyph.SCALE, 14);
        scale.getStyleClass().add("filter-toggle-icon");
        content.getChildren().addAll(check, label, spacer, scale);
        return content;
    }

    private Node inputWithIcon(TextField field, LauncherIcons.Glyph glyph) {
        StackPane wrapper = new StackPane(field);
        wrapper.getStyleClass().add("filter-input-wrap");
        Node icon = LauncherIcons.icon(glyph, 14);
        icon.getStyleClass().add("filter-input-icon");
        icon.setMouseTransparent(true);
        StackPane.setAlignment(icon, Pos.CENTER_LEFT);
        StackPane.setMargin(icon, new Insets(0, 0, 0, 12));
        wrapper.getChildren().add(icon);
        field.setMaxWidth(Double.MAX_VALUE);
        return wrapper;
    }

    private Node datePickerWithIcon() {
        StackPane wrapper = new StackPane(updatedAfterPicker);
        wrapper.getStyleClass().add("filter-input-wrap");
        Node icon = LauncherIcons.icon(LauncherIcons.Glyph.CALENDAR, 14);
        icon.getStyleClass().add("filter-input-icon");
        icon.setMouseTransparent(true);
        StackPane.setAlignment(icon, Pos.CENTER_LEFT);
        StackPane.setMargin(icon, new Insets(0, 0, 0, 12));
        wrapper.getChildren().add(icon);
        updatedAfterPicker.setMaxWidth(Double.MAX_VALUE);
        return wrapper;
    }

    private Node filterSection(String label, Node... controls) {
        VBox box = new VBox(6);
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("filter-label");
        box.getStyleClass().add("filter-section");
        box.getChildren().add(labelNode);
        for (Node control : controls) {
            if (control instanceof Region region) {
                region.setMaxWidth(Double.MAX_VALUE);
            }
            box.getChildren().add(control);
        }
        return box;
    }

    private HBox numberPresetRow(
            Map<Integer, Button> buttons,
            List<NumberPreset> presets,
            Consumer<Integer> onSelect
    ) {
        HBox row = new HBox(4);
        row.getStyleClass().add("filter-preset-row");
        row.setAlignment(Pos.CENTER);
        for (NumberPreset preset : presets) {
            Button button = presetButton(preset.label());
            button.setOnAction(event -> onSelect.accept(preset.value()));
            buttons.put(preset.value(), button);
            row.getChildren().add(button);
            HBox.setHgrow(button, Priority.ALWAYS);
        }
        return row;
    }

    private HBox datePresetRow() {
        HBox row = new HBox(4);
        row.getStyleClass().add("filter-preset-row");
        row.setAlignment(Pos.CENTER);
        for (DateRangePreset preset : List.of(
                DateRangePreset.ANY,
                DateRangePreset.SEVEN_DAYS,
                DateRangePreset.THIRTY_DAYS,
                DateRangePreset.NINETY_DAYS
        )) {
            Button button = presetButton(preset.label());
            button.setOnAction(event -> selectDateRange(preset.apiValue()));
            dateRangeButtons.put(preset, button);
            row.getChildren().add(button);
            HBox.setHgrow(button, Priority.ALWAYS);
        }
        return row;
    }

    private Button presetButton(String label) {
        Button button = new Button(label);
        button.getStyleClass().add("filter-preset-button");
        button.setMaxWidth(Double.MAX_VALUE);
        return button;
    }

    public void reset(boolean runSearch) {
        withSuppressedSearch(() -> {
            gameVersionDropdown.setSelectedVersions(List.of());
            minFavoritesPreset = null;
            minDownloadsPreset = null;
            dateRangePreset = DateRangePreset.ANY;
            showPreReleases = false;
            openSourceOnly = false;
            customMinFavoritesField.clear();
            customMinDownloadsField.clear();
            updatedAfterPicker.setValue(null);
            onResetTags.run();
            updateGameVersionOptions(null);
        });
        refreshAndNotify(runSearch);
    }

    private void selectMinimumFavoritesPreset(Integer value) {
        withSuppressedSearch(() -> {
            minFavoritesPreset = value;
            customMinFavoritesField.clear();
        });
        refreshAndNotify(true);
    }

    private void selectMinimumDownloadsPreset(Integer value) {
        withSuppressedSearch(() -> {
            minDownloadsPreset = value;
            customMinDownloadsField.clear();
        });
        refreshAndNotify(true);
    }

    private void refreshAndNotify(boolean runSearch) {
        refreshPresetButtons();
        onChange.run();
        if (runSearch) {
            onSearch.run();
        }
    }

    private void refreshPresetButtons() {
        boolean customFavorites = parsePositiveInteger(customMinFavoritesField.getText()) != null;
        boolean customDownloads = parsePositiveInteger(customMinDownloadsField.getText()) != null;
        boolean customDate = updatedAfterPicker.getValue() != null;
        minFavoritesButtons.forEach((value, button) ->
                pseudo(button, "selected", !customFavorites && Objects.equals(value, minFavoritesPreset)));
        minDownloadsButtons.forEach((value, button) ->
                pseudo(button, "selected", !customDownloads && Objects.equals(value, minDownloadsPreset)));
        dateRangeButtons.forEach((value, button) ->
                pseudo(button, "selected", !customDate && value == dateRangePreset));
        pseudo(openSourceButton, "selected", openSourceOnly);
        boolean hasPreReleases = gameVersions.hasPreReleases();
        preReleaseToggle.setVisible(hasPreReleases);
        preReleaseToggle.setManaged(hasPreReleases);
        pseudo(preReleaseToggle, "selected", showPreReleases);
    }

    private void changedAndSearch() {
        if (suppressSearch) {
            return;
        }
        refreshAndNotify(true);
    }

    private void withSuppressedSearch(Runnable work) {
        boolean previous = suppressSearch;
        suppressSearch = true;
        try {
            work.run();
        } finally {
            suppressSearch = previous;
        }
    }

    private void togglePreReleases() {
        String previous = selectedGameVersion();
        withSuppressedSearch(() -> {
            showPreReleases = !showPreReleases;
            updateGameVersionOptions(previous);
        });
        refreshAndNotify(!Objects.equals(previous, selectedGameVersion()));
    }

    private void toggleOpenSource() {
        openSourceOnly = !openSourceOnly;
        changedAndSearch();
    }

    private void updateGameVersionOptions(String preferredSelection) {
        List<String> options = gameVersions.visibleVersions(showPreReleases);
        List<String> preferred = GameVersionGroups.parseSelection(preferredSelection).stream()
                .filter(options::contains)
                .toList();
        gameVersionDropdown.setVersions(options);
        gameVersionDropdown.setSelectedVersions(preferred);
    }

    private static Integer parsePositiveInteger(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record NumberPreset(String label, Integer value) {
    }

    private enum DateRangePreset {
        ANY("Any", null),
        SEVEN_DAYS("7d", "7d"),
        THIRTY_DAYS("30d", "30d"),
        NINETY_DAYS("90d", "90d");

        private final String label;
        private final String apiValue;

        DateRangePreset(String label, String apiValue) {
            this.label = label;
            this.apiValue = apiValue;
        }

        String label() {
            return label;
        }

        String apiValue() {
            return apiValue;
        }

        static DateRangePreset fromApiValue(String rawValue) {
            if (rawValue == null || rawValue.isBlank()) {
                return ANY;
            }
            String normalized = rawValue.trim();
            for (DateRangePreset preset : values()) {
                if (Objects.equals(preset.apiValue, normalized)) {
                    return preset;
                }
            }
            return ANY;
        }
    }
}
