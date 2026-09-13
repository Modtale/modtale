package net.modtale.launcher.ui.library;

import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Skin;
import javafx.scene.control.SkinBase;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.StackPane;
import net.modtale.launcher.ui.common.LauncherIcons;

final class LibraryToggleBox extends CheckBox {
    LibraryToggleBox() {
        getStyleClass().setAll("library-toggle-box");
        setFocusTraversable(true);
        setOnMouseClicked(event -> {
            fire();
            event.consume();
        });
        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.SPACE || event.getCode() == KeyCode.ENTER) {
                fire();
                event.consume();
            }
        });
    }

    void setOnAction(Runnable action) {
        super.setOnAction(event -> {
            if (action != null) action.run();
        });
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new SkinBase<LibraryToggleBox>(this) {
            {
                Node check = LauncherIcons.icon(LauncherIcons.Glyph.CHECK, 13);
                Node minus = LauncherIcons.icon(LauncherIcons.Glyph.MINUS, 13);
                check.visibleProperty().bind(selectedProperty().and(indeterminateProperty().not()));
                minus.visibleProperty().bind(indeterminateProperty());
                StackPane marks = new StackPane(check, minus);
                marks.setMouseTransparent(true);
                getChildren().add(marks);
            }
        };
    }
}
