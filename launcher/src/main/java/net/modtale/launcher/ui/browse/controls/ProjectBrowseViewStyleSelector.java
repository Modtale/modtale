package net.modtale.launcher.ui.browse.controls;

import static net.modtale.launcher.ui.common.LauncherUi.pseudo;

import java.util.LinkedHashMap;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import net.modtale.launcher.ui.browse.card.ProjectCardViewStyle;
import net.modtale.launcher.ui.common.LauncherIcons;

public final class ProjectBrowseViewStyleSelector {

    private final Runnable onRender;
    private final Map<ProjectCardViewStyle, Button> buttons = new LinkedHashMap<>();
    private ProjectCardViewStyle style = ProjectCardViewStyle.GRID;
    private Node view;

    public ProjectBrowseViewStyleSelector(Runnable onRender) {
        this.onRender = onRender;
    }

    public ProjectCardViewStyle style() {
        return style;
    }

    public Node view() {
        if (view == null) {
            HBox selector = new HBox(4);
            selector.getStyleClass().add("segmented-control");
            selector.setAlignment(Pos.CENTER);
            addButton(selector, ProjectCardViewStyle.GRID, LauncherIcons.Glyph.GRID, "Grid");
            addButton(selector, ProjectCardViewStyle.LIST, LauncherIcons.Glyph.LIST, "List");
            addButton(selector, ProjectCardViewStyle.COMPACT, LauncherIcons.Glyph.ALIGN_JUSTIFY, "Compact");
            refresh();
            view = selector;
        }
        return view;
    }

    public void refresh() {
        buttons.forEach((candidate, button) -> pseudo(button, "selected", candidate == style));
    }

    private void addButton(HBox selector, ProjectCardViewStyle candidate, LauncherIcons.Glyph icon, String label) {
        Button button = new Button();
        button.getStyleClass().add("segmented-button");
        button.setGraphic(LauncherIcons.icon(icon, 16));
        button.setTooltip(new Tooltip(label));
        button.setOnAction(event -> {
            style = candidate;
            refresh();
            onRender.run();
        });
        buttons.put(candidate, button);
        selector.getChildren().add(button);
    }
}
