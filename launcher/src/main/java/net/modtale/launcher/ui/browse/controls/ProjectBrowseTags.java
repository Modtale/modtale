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
    private final VBox popover = new VBox(12);

    public ProjectBrowseTags(Runnable onSearch, Runnable onChange) {
        this.onSearch = onSearch;
        this.onChange = onChange;
        configurePopover();
    }

    public VBox popover() {
        return popover;
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
        updateButtons();
    }

    public void refresh() {
        updateButtons();
    }

    private void configurePopover() {
        popover.getStyleClass().addAll("filter-popover", "tag-popover");
        popover.setPrefWidth(288);
        popover.setMaxWidth(288);
        popover.setVisible(false);
        popover.setManaged(false);
        popover.addEventHandler(ScrollEvent.SCROLL, ScrollEvent::consume);

        HBox header = new HBox(12);
        header.getStyleClass().add("tag-popover-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Filter by Tag");
        title.getStyleClass().add("popover-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button clear = new Button("Clear All");
        clear.getStyleClass().add("tag-clear-button");
        clear.setOnAction(event -> {
            selectedTags.clear();
            updateButtons();
            onSearch.run();
        });
        header.getChildren().addAll(title, spacer, clear);

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
        tagScroll.setMaxHeight(240);
        tagScroll.addEventFilter(ScrollEvent.SCROLL, event -> scrollTags(tagScroll, event));
        header.addEventFilter(ScrollEvent.SCROLL, ScrollEvent::consume);
        popover.getChildren().setAll(header, tagScroll);
    }

    private void updateButtons() {
        tagButtons.forEach((tag, button) -> pseudo(button, "selected", selectedTags.contains(tag)));
        onChange.run();
    }

    private static void scrollTags(ScrollPane scrollPane, ScrollEvent event) {
        double scrollable = scrollPane.getContent().getLayoutBounds().getHeight()
                - scrollPane.getViewportBounds().getHeight();
        if (scrollable > 1) {
            double pixels = switch (event.getTextDeltaYUnits()) {
                case LINES -> event.getTextDeltaY() * 48;
                case PAGES -> event.getTextDeltaY() * Math.max(120, scrollPane.getViewportBounds().getHeight() * 0.86);
                case NONE -> event.getDeltaY();
            };
            double next = scrollPane.getVvalue() - pixels / scrollable;
            scrollPane.setVvalue(Math.max(scrollPane.getVmin(), Math.min(next, scrollPane.getVmax())));
        }
        event.consume();
    }
}
