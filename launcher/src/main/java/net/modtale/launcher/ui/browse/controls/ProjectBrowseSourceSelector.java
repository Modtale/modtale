package net.modtale.launcher.ui.browse.controls;

import static net.modtale.launcher.ui.common.LauncherUi.pseudo;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;

public final class ProjectBrowseSourceSelector {

    private final Consumer<ProjectBrowseSource> onSelect;
    private final Map<ProjectBrowseSource, Button> buttons = new LinkedHashMap<>();
    private ProjectBrowseSource source = ProjectBrowseSource.MODTALE;
    private Node view;

    public ProjectBrowseSourceSelector(Consumer<ProjectBrowseSource> onSelect) {
        this.onSelect = onSelect;
    }

    public ProjectBrowseSource source() {
        return source;
    }

    public Node view() {
        if (view == null) {
            HBox selector = new HBox(4);
            selector.getStyleClass().add("provider-switch");
            selector.setAlignment(Pos.CENTER);
            addButton(selector, ProjectBrowseSource.MODTALE);
            addButton(selector, ProjectBrowseSource.CURSEFORGE);
            refresh();
            view = selector;
        }
        return view;
    }

    public void refresh() {
        buttons.forEach((candidate, button) -> pseudo(button, "selected", candidate == source));
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

    private void addButton(HBox selector, ProjectBrowseSource candidate) {
        Button button = new Button(candidate.label());
        button.getStyleClass().addAll("provider-switch-button", candidate.name().toLowerCase());
        button.setAccessibleText("Browse " + candidate.label() + " projects");
        button.setOnAction(event -> select(candidate));
        buttons.put(candidate, button);
        selector.getChildren().add(button);
    }
}
