package net.modtale.launcher.ui.library;

import javafx.css.PseudoClass;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import net.modtale.launcher.ui.common.LauncherIcons;

final class LibraryToggleBox extends StackPane {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");
    private static final PseudoClass INDETERMINATE = PseudoClass.getPseudoClass("indeterminate");

    private boolean selected;
    private boolean indeterminate;
    private Runnable action = () -> {
    };

    LibraryToggleBox() {
        getStyleClass().add("library-toggle-box");
        setFocusTraversable(true);
        setOnMouseClicked(event -> {
            activate();
            event.consume();
        });
        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SPACE || event.getCode() == KeyCode.ENTER) {
                activate();
                event.consume();
            }
        });
        updateState();
    }

    boolean isSelected() {
        return selected;
    }

    void setSelected(boolean selected) {
        this.selected = selected;
        updateState();
    }

    void setIndeterminate(boolean indeterminate) {
        this.indeterminate = indeterminate;
        updateState();
    }

    void setTooltip(Tooltip tooltip) {
        if (tooltip != null) {
            Tooltip.install(this, tooltip);
        }
    }

    void setOnAction(Runnable action) {
        this.action = action == null ? () -> {
        } : action;
    }

    private void activate() {
        if (isDisabled()) {
            return;
        }
        selected = !selected;
        indeterminate = false;
        updateState();
        action.run();
    }

    private void updateState() {
        pseudoClassStateChanged(SELECTED, selected);
        pseudoClassStateChanged(INDETERMINATE, indeterminate);
        getChildren().clear();
        if (indeterminate) {
            getChildren().add(LauncherIcons.icon(LauncherIcons.Glyph.MINUS, 13));
        } else if (selected) {
            getChildren().add(LauncherIcons.icon(LauncherIcons.Glyph.CHECK, 13));
        }
    }
}
