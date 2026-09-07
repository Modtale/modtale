package net.modtale.launcher.ui.browse.controls;

import java.util.function.Consumer;
import javafx.scene.Node;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.shape.Circle;

public final class ProjectBrowseSourceSelector {

    private final Consumer<ProjectBrowseSource> onSelect;
    private ProjectBrowseSource source = ProjectBrowseSource.MODTALE;
    private MenuButton picker;

    public ProjectBrowseSourceSelector(Consumer<ProjectBrowseSource> onSelect) {
        this.onSelect = onSelect;
    }

    public ProjectBrowseSource source() {
        return source;
    }

    public Node view() {
        if (picker == null) {
            picker = new MenuButton();
            picker.getStyleClass().add("provider-picker");
            addItem(ProjectBrowseSource.MODTALE);
            addItem(ProjectBrowseSource.CURSEFORGE);
            refresh();
        }
        return picker;
    }

    public void refresh() {
        if (picker == null) {
            return;
        }
        picker.setText(source.label());
        picker.getStyleClass().removeAll("modtale", "curseforge");
        picker.getStyleClass().add(source.name().toLowerCase());
        Circle providerDot = new Circle(3.5);
        providerDot.getStyleClass().add("provider-picker-dot");
        picker.setGraphic(providerDot);
        picker.setGraphicTextGap(7);
        picker.setAccessibleText("Browse source: " + source.label());
    }

    void select(ProjectBrowseSource selected) {
        ProjectBrowseSource next = selected == null ? ProjectBrowseSource.MODTALE : selected;
        if (next == source) {
            return;
        }
        source = next;
        refresh();
        onSelect.accept(source);
    }

    private void addItem(ProjectBrowseSource candidate) {
        MenuItem item = new MenuItem(candidate.label());
        item.setOnAction(event -> select(candidate));
        picker.getItems().add(item);
    }
}
