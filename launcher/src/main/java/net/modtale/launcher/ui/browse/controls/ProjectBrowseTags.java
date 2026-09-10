package net.modtale.launcher.ui.browse.controls;

import static net.modtale.launcher.ui.common.LauncherUi.pseudo;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class ProjectBrowseTags {

    private final Runnable onSearch;
    private final Runnable onChange;
    private final Map<String, Button> tagButtons = new LinkedHashMap<>();
    private final Set<String> selectedTags = new LinkedHashSet<>();
    private final VBox section = new VBox(12);
    private final TextField search = new TextField();
    private final Label heading = new Label("Tags");

    public ProjectBrowseTags(Runnable onSearch, Runnable onChange) {
        this.onSearch = onSearch;
        this.onChange = onChange;
        configureSection();
    }

    public VBox section() {
        return section;
    }

    public boolean isEmpty() {
        return selectedTags.isEmpty();
    }

    public int selectedCount() {
        return selectedTags.size();
    }

    public String selectedQuery() {
        return selectedTags.isEmpty() ? null : String.join(",", selectedTags);
    }

    public String title() {
        String first = selectedTags.iterator().next();
        int remaining = selectedTags.size() - 1;
        return remaining > 0 ? "Tagged: " + first + " (+" + remaining + ")" : "Tagged: " + first;
    }

    public void clear() {
        selectedTags.clear();
        search.clear();
        updateButtons();
    }

    public void refresh() {
        updateButtons();
    }

    private void configureSection() {
        section.getStyleClass().add("filter-section");

        HBox header = new HBox(12);
        header.getStyleClass().add("tag-popover-header");
        header.setAlignment(Pos.CENTER_LEFT);
        heading.getStyleClass().add("filter-label");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button clear = new Button("Clear Tags");
        clear.getStyleClass().add("tag-clear-button");
        clear.setOnAction(event -> {
            clear();
            onSearch.run();
        });
        header.getChildren().addAll(heading, spacer, clear);

        FlowPane tags = new FlowPane(8, 8);
        tags.getStyleClass().add("tag-grid");
        for (String tag : BrowseOptions.GLOBAL_TAGS) {
            Button button = new Button(tag);
            button.getStyleClass().add("tag-chip");
            button.setOnAction(event -> {
                if (!selectedTags.add(tag)) {
                    selectedTags.remove(tag);
                }
                updateButtons();
                onSearch.run();
            });
            tagButtons.put(tag, button);
            tags.getChildren().add(button);
        }

        ScrollPane tagScroll = new ScrollPane(tags);
        tagScroll.getStyleClass().add("tag-scroll");
        tagScroll.setFitToWidth(true);
        tagScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        tagScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        tagScroll.setPrefViewportHeight(150);
        tagScroll.setMaxHeight(150);
        header.addEventFilter(ScrollEvent.SCROLL, ScrollEvent::consume);
        search.setPromptText("Search tags...");
        search.setAccessibleText("Search tags");
        net.modtale.launcher.ui.common.LauncherUi.styleInput(search);
        search.textProperty().addListener((observable, previous, query) -> {
            String normalized = query.trim().toLowerCase(java.util.Locale.ROOT);
            tagButtons.forEach((tag, button) -> {
                boolean matches = tag.toLowerCase(java.util.Locale.ROOT).contains(normalized);
                button.setVisible(matches);
                button.setManaged(matches);
            });
        });
        section.getChildren().setAll(header, search, tagScroll);
    }

    private void updateButtons() {
        heading.setText(isEmpty() ? "Tags" : "Tags (" + selectedCount() + ")");
        tagButtons.forEach((tag, button) -> pseudo(button, "selected", selectedTags.contains(tag)));
        onChange.run();
    }

}
