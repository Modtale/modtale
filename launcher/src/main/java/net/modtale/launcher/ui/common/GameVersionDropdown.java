package net.modtale.launcher.ui.common;

import static net.modtale.launcher.ui.common.LauncherUi.pseudo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class GameVersionDropdown extends VBox {

    private static final PseudoClass PARTIAL = PseudoClass.getPseudoClass("indeterminate");

    private final Button toggle = new Button();
    private final Label toggleLabel = new Label("Any");
    private final Node toggleChevron = LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_DOWN, 14);
    private final VBox panel = new VBox(0);
    private final VBox options = new VBox(0);
    private final ScrollPane scroll = new ScrollPane(options);
    private final Set<String> expandedGroups = new LinkedHashSet<>();

    private List<String> versions = List.of();
    private List<String> selectedVersions = List.of();
    private String emptyText = "No versions found";
    private String anyLabel = "Any";
    private double maxListHeight = 224;
    private Consumer<List<String>> selectionListener = ignored -> {
    };
    private Consumer<Boolean> openListener = ignored -> {
    };

    public GameVersionDropdown() {
        getStyleClass().add("game-version-dropdown");
        setSpacing(6);
        setMaxWidth(Double.MAX_VALUE);

        toggle.getStyleClass().add("game-version-dropdown-toggle");
        toggle.setMaxWidth(Double.MAX_VALUE);
        toggle.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        HBox toggleContent = toggleContent();
        toggleContent.prefWidthProperty().bind(toggle.widthProperty().subtract(22));
        toggle.setGraphic(toggleContent);
        toggle.setOnAction(event -> setOpen(!isOpen()));

        scroll.getStyleClass().add("game-version-dropdown-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setMaxHeight(maxListHeight);

        panel.getStyleClass().add("game-version-dropdown-panel");
        panel.getChildren().add(scroll);
        panel.setVisible(false);
        panel.setManaged(false);

        getChildren().setAll(toggle, panel);
        refresh();
    }

    public static GameVersionDropdown multiSelect() {
        return new GameVersionDropdown();
    }

    public void setVersions(List<String> versions) {
        List<String> safeVersions = versions == null ? List.of() : versions.stream()
                .filter(version -> version != null && !version.isBlank())
                .distinct()
                .toList();
        this.versions = List.copyOf(safeVersions);
        this.selectedVersions = GameVersionGroups.orderedSelection(this.selectedVersions, this.versions);
        refresh();
    }

    public List<String> versions() {
        return versions;
    }

    public void setSelectedVersions(List<String> selectedVersions) {
        this.selectedVersions = selectedVersions(selectedVersions);
        refresh();
    }

    public List<String> selectedVersions() {
        return selectedVersions;
    }

    public String selectedQuery() {
        return GameVersionGroups.selectionQuery(selectedVersions, versions);
    }

    public void setAnyLabel(String anyLabel) {
        this.anyLabel = anyLabel == null || anyLabel.isBlank() ? "Any" : anyLabel;
        refresh();
    }

    public void setEmptyText(String emptyText) {
        this.emptyText = emptyText == null || emptyText.isBlank() ? "No versions found" : emptyText;
        refresh();
    }

    public void setMaxListHeight(double maxListHeight) {
        this.maxListHeight = Math.max(120, maxListHeight);
        scroll.setMaxHeight(this.maxListHeight);
    }

    public boolean isOpen() {
        return panel.isVisible();
    }

    public void setOpen(boolean open) {
        if (panel.isVisible() == open) {
            return;
        }
        panel.setVisible(open);
        panel.setManaged(open);
        pseudo(toggle, "open", open);
        toggleChevron.setRotate(open ? 180 : 0);
        openListener.accept(open);
    }

    public void setOnSelectionChange(Consumer<List<String>> selectionListener) {
        this.selectionListener = selectionListener == null ? ignored -> {
        } : selectionListener;
    }

    public void setOnOpenChange(Consumer<Boolean> openListener) {
        this.openListener = openListener == null ? ignored -> {
        } : openListener;
    }

    private HBox toggleContent() {
        HBox content = new HBox(8);
        content.getStyleClass().add("game-version-dropdown-toggle-content");
        content.setAlignment(Pos.CENTER_LEFT);
        toggleLabel.getStyleClass().add("game-version-dropdown-toggle-label");
        toggleLabel.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(toggleLabel, Priority.ALWAYS);
        toggleChevron.getStyleClass().add("game-version-dropdown-chevron");
        content.getChildren().addAll(toggleLabel, toggleChevron);
        return content;
    }

    private void refresh() {
        toggleLabel.setText(GameVersionGroups.displayLabel(selectedVersions, versions, anyLabel));
        options.getChildren().setAll(optionNodes());
    }

    private List<Node> optionNodes() {
        List<Node> nodes = new ArrayList<>();
        nodes.add(optionRow(anyLabel, selectedVersions.isEmpty(), () -> commitSelection(List.of())));
        List<GameVersionGroups.Group> groups = GameVersionGroups.build(versions);
        if (groups.isEmpty()) {
            Label empty = new Label(emptyText);
            empty.getStyleClass().add("game-version-dropdown-empty");
            nodes.add(empty);
            return nodes;
        }
        for (GameVersionGroups.Group group : groups) {
            if (!group.grouped()) {
                String version = group.versions().isEmpty() ? "" : group.versions().getFirst();
                if (!version.isBlank()) {
                    nodes.add(optionRow(version, selectedVersions.contains(version), () -> toggleVersion(version)));
                }
                continue;
            }
            nodes.add(groupRow(group));
            if (expandedGroups.contains(group.label())) {
                for (String version : group.versions()) {
                    nodes.add(childRow(version, selectedVersions.contains(version), () -> toggleVersion(version)));
                }
            }
        }
        return nodes;
    }

    private Button optionRow(String label, boolean selected, Runnable action) {
        Button row = new Button();
        row.getStyleClass().add("game-version-dropdown-row");
        row.setMaxWidth(Double.MAX_VALUE);
        HBox content = rowContent(label, selected, null);
        content.prefWidthProperty().bind(row.widthProperty().subtract(20));
        row.setGraphic(content);
        row.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        pseudo(row, "selected", selected);
        row.setOnAction(event -> action.run());
        return row;
    }

    private Button childRow(String label, boolean selected, Runnable action) {
        Button row = optionRow(label, selected, action);
        row.getStyleClass().add("game-version-dropdown-child-row");
        return row;
    }

    private HBox groupRow(GameVersionGroups.Group group) {
        HBox row = new HBox(0);
        row.getStyleClass().add("game-version-dropdown-group-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);

        int selectedCount = (int) group.versions().stream().filter(selectedVersions::contains).count();
        boolean selected = selectedCount == group.versions().size();
        boolean partial = selectedCount > 0 && !selected;
        pseudo(row, "selected", selected);
        row.pseudoClassStateChanged(PARTIAL, partial);

        Button select = new Button();
        select.getStyleClass().add("game-version-dropdown-group-select");
        select.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        HBox selectContent = rowContent(group.label(), false, partial ? selectedCount + "/" + group.versions().size() : null);
        selectContent.prefWidthProperty().bind(select.widthProperty().subtract(10));
        select.setGraphic(selectContent);
        select.setMaxWidth(Double.MAX_VALUE);
        select.setOnAction(event -> toggleGroupSelection(group.versions()));
        HBox.setHgrow(select, Priority.ALWAYS);

        Button expand = new Button(null, LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_RIGHT, 13));
        expand.getStyleClass().add("game-version-dropdown-expand");
        expand.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        expand.setOnAction(event -> toggleGroupExpanded(group.label()));
        expand.getGraphic().setRotate(expandedGroups.contains(group.label()) ? 90 : 0);

        row.getChildren().addAll(select, expand);
        if (selected) {
            Node check = LauncherIcons.icon(LauncherIcons.Glyph.CHECK, 14);
            check.getStyleClass().add("game-version-dropdown-check");
            row.getChildren().add(check);
        }
        return row;
    }

    private HBox rowContent(String label, boolean selected, String count) {
        HBox content = new HBox(8);
        content.getStyleClass().add("game-version-dropdown-row-content");
        content.setAlignment(Pos.CENTER_LEFT);
        Label text = new Label(label);
        text.getStyleClass().add("game-version-dropdown-row-label");
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        content.getChildren().add(text);
        if (count != null && !count.isBlank()) {
            Label countLabel = new Label(count);
            countLabel.getStyleClass().add("game-version-dropdown-count");
            content.getChildren().add(countLabel);
        }
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        content.getChildren().add(spacer);
        if (selected) {
            Node check = LauncherIcons.icon(LauncherIcons.Glyph.CHECK, 14);
            check.getStyleClass().add("game-version-dropdown-check");
            content.getChildren().add(check);
        }
        return content;
    }

    private void toggleVersion(String version) {
        List<String> next = new ArrayList<>(selectedVersions);
        if (next.contains(version)) {
            next.remove(version);
        } else {
            next.add(version);
        }
        commitSelection(next);
    }

    private void toggleGroupSelection(List<String> groupVersions) {
        boolean hasEntireGroup = groupVersions.stream().allMatch(selectedVersions::contains);
        List<String> next = new ArrayList<>(selectedVersions);
        if (hasEntireGroup) {
            next.removeIf(groupVersions::contains);
        } else {
            for (String version : groupVersions) {
                if (!next.contains(version)) {
                    next.add(version);
                }
            }
        }
        commitSelection(next);
    }

    private void toggleGroupExpanded(String label) {
        if (expandedGroups.contains(label)) {
            expandedGroups.remove(label);
        } else {
            expandedGroups.add(label);
        }
        refresh();
    }

    private void commitSelection(List<String> nextSelection) {
        selectedVersions = selectedVersions(nextSelection);
        refresh();
        selectionListener.accept(selectedVersions);
    }

    private List<String> selectedVersions(List<String> selectedVersions) {
        if (selectedVersions == null || selectedVersions.isEmpty()) {
            return List.of();
        }
        Set<String> selected = new LinkedHashSet<>(selectedVersions);
        return versions.stream()
                .filter(selected::contains)
                .toList();
    }
}
