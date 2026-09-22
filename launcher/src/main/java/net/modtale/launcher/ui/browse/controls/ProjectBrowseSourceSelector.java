package net.modtale.launcher.ui.browse.controls;

import java.util.function.Consumer;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import net.modtale.launcher.ui.common.LauncherIcons;

public final class ProjectBrowseSourceSelector {

    private final Consumer<ProjectBrowseSource> onSelect;
    private ProjectBrowseSource source = ProjectBrowseSource.MODTALE;
    private Button picker;
    private Label caption;
    private VBox dropdown;

    public ProjectBrowseSourceSelector(Consumer<ProjectBrowseSource> onSelect) {
        this.onSelect = onSelect;
    }

    public ProjectBrowseSource source() {
        return source;
    }

    public Button view() {
        if (picker == null) {
            picker = new Button();
            picker.getStyleClass().add("provider-picker");
            caption = new Label();
            HBox content = new HBox(6, caption, LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_DOWN, 12));
            content.setAlignment(Pos.CENTER);
            picker.setGraphic(content);
            refresh();
        }
        return picker;
    }

    public VBox dropdown() {
        if (dropdown == null) {
            dropdown = new VBox();
            dropdown.getStyleClass().add("sort-dropdown-panel");
            dropdown.setMinWidth(180);
            dropdown.setPrefWidth(180);
            dropdown.setMaxWidth(180);
            dropdown.setManaged(false);
            dropdown.setVisible(false);
            for (ProjectBrowseSource candidate : ProjectBrowseSource.values()) {
                Button item = new Button();
                Label label = new Label(candidate.label());
                label.getStyleClass().add("sort-dropdown-item-label");
                item.setGraphic(label);
                item.getStyleClass().add("sort-dropdown-item");
                item.setMaxWidth(Double.MAX_VALUE);
                item.setOnAction(event -> {
                    hide();
                    select(candidate);
                    picker.requestFocus();
                });
                dropdown.getChildren().add(item);
            }
        }
        return dropdown;
    }

    public void hide() {
        dropdown().setVisible(false);
        view().pseudoClassStateChanged(PseudoClass.getPseudoClass("showing"), false);
    }

    public void show() {
        dropdown().setVisible(true);
        dropdown().toFront();
        view().pseudoClassStateChanged(PseudoClass.getPseudoClass("showing"), true);
    }

    public void refresh() {
        if (picker == null) {
            return;
        }
        caption.setText(source.label());
        picker.getStyleClass().removeAll("modtale", "curseforge");
        picker.getStyleClass().add(source.name().toLowerCase());
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
}
