package net.modtale.launcher.ui.wardrobe;

import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import net.modtale.launcher.ui.common.LauncherIcons;

final class WardrobePagination extends FlowPane {
    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private final HBox pageButtons = new HBox(4);
    private final TextField jumpPage = new TextField();
    private final Button previous = pageIcon(LauncherIcons.Glyph.CHEVRON_LEFT, "Previous Page");
    private final Button next = pageIcon(LauncherIcons.Glyph.CHEVRON_RIGHT, "Next Page");
    private final java.util.function.IntConsumer navigate;
    private int page = 1, totalPages = 1;

    WardrobePagination(java.util.function.IntConsumer navigate) {
        super(24, 12);
        this.navigate = navigate; this.getStyleClass().add("pagination-nav");
        this.setAlignment(Pos.CENTER); this.setMaxWidth(Double.MAX_VALUE);
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
        this.getChildren().setAll(shell, jumpShell); update(1, 1, false);
    }

    void update(int currentPage, int pages, boolean busy) {
        page = currentPage; totalPages = Math.max(1, pages);
        setDisable(busy);
        this.setVisible(totalPages > 1); this.setManaged(totalPages > 1);
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
        if (isDisabled() || target < 1 || target > totalPages || target == page) return;
        navigate.accept(target);
    }

    private static Button pageIcon(LauncherIcons.Glyph icon, String label) {
        Button button = new Button(); button.getStyleClass().addAll("pagination-button", "pagination-icon-button");
        button.setGraphic(LauncherIcons.icon(icon, 16)); button.setAccessibleText(label); button.setTooltip(new Tooltip(label));
        button.setMinSize(36, 36); button.setPrefSize(36, 36); button.setMaxSize(36, 36); return button;
    }

    private static Label text(String value, String style) {
        Label label = new Label(value); label.getStyleClass().add(style); return label;
    }
}
