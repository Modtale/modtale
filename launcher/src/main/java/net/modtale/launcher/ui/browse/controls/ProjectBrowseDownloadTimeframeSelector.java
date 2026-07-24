package net.modtale.launcher.ui.browse.controls;

import static net.modtale.launcher.ui.common.LauncherUi.pseudo;
import static net.modtale.launcher.ui.common.LauncherUi.setVisibleManaged;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

public final class ProjectBrowseDownloadTimeframeSelector {

    private final Consumer<String> onSelect;
    private final Supplier<String> selectedRange;
    private final Map<DownloadTimeframe, Button> buttons = new LinkedHashMap<>();
    private HBox view;

    public ProjectBrowseDownloadTimeframeSelector(Consumer<String> onSelect, Supplier<String> selectedRange) {
        this.onSelect = onSelect;
        this.selectedRange = selectedRange;
    }

    public Node view() {
        if (view == null) {
            view = new HBox(4);
            view.getStyleClass().add("download-timeframe-control");
            view.setAlignment(Pos.CENTER);
            addButton(DownloadTimeframe.SEVEN_DAYS);
            addButton(DownloadTimeframe.THIRTY_DAYS);
            addButton(DownloadTimeframe.NINETY_DAYS);
            addButton(DownloadTimeframe.ALL_TIME);
            refresh();
        }
        return view;
    }

    public void setVisible(boolean visible) {
        setVisibleManaged(view(), visible);
    }

    public void refresh() {
        DownloadTimeframe active = DownloadTimeframe.fromDateRange(selectedRange.get());
        buttons.forEach((range, button) -> pseudo(button, "selected", range.equals(active)));
    }

    private void addButton(DownloadTimeframe timeframe) {
        Button button = new Button(timeframe.label());
        button.getStyleClass().add("download-timeframe-button");
        button.setTooltip(new Tooltip(timeframe.tooltip()));
        button.setOnAction(event -> onSelect.accept(timeframe.dateRange()));
        buttons.put(timeframe, button);
        view.getChildren().add(button);
    }

    private enum DownloadTimeframe {
        SEVEN_DAYS("7d", "7d", "Downloads in the last 7 days"),
        THIRTY_DAYS("30d", "30d", "Downloads in the last 30 days"),
        NINETY_DAYS("90d", "90d", "Downloads in the last 90 days"),
        ALL_TIME("All", null, "All-time downloads");

        private final String label;
        private final String dateRange;
        private final String tooltip;

        DownloadTimeframe(String label, String dateRange, String tooltip) {
            this.label = label;
            this.dateRange = dateRange;
            this.tooltip = tooltip;
        }

        String label() {
            return label;
        }

        String dateRange() {
            return dateRange;
        }

        String tooltip() {
            return tooltip;
        }

        static DownloadTimeframe fromDateRange(String range) {
            if (range == null || range.isBlank()) {
                return ALL_TIME;
            }
            String normalized = range.trim();
            for (DownloadTimeframe timeframe : values()) {
                if (normalized.equals(timeframe.dateRange)) {
                    return timeframe;
                }
            }
            return ALL_TIME;
        }
    }
}
