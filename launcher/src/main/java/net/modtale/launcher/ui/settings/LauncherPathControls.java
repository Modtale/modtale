package net.modtale.launcher.ui.settings;

import net.modtale.launcher.ui.common.LauncherTooltips;

import static net.modtale.launcher.ui.common.LauncherUi.readableField;
import static net.modtale.launcher.ui.common.LauncherUi.secondaryButton;

import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import net.modtale.launcher.settings.HytalePathDetector;

public final class LauncherPathControls {

    private LauncherPathControls() {
    }

    public static Node pathRow(Supplier<Stage> stage, TextField field, String fieldName, boolean directory, boolean detect) {
        HBox row = new HBox(8);
        row.getStyleClass().add("path-row");
        row.setMaxWidth(Double.MAX_VALUE);
        field.setPrefWidth(420);
        field.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(field, Priority.ALWAYS);
        Button browse = secondaryButton("Browse");
        LauncherTooltips.install(browse, "Choose the " + readableField(fieldName));
        LauncherTooltips.install(field, javafx.beans.binding.Bindings.createStringBinding(
                () -> readableField(fieldName) + (field.getText().isBlank() ? "" : ": " + field.getText()), field.textProperty()));
        browse.setMinWidth(86);
        browse.setPrefWidth(86);
        browse.setOnAction(event -> {
            if (directory) {
                PathChooserSupport.chooseDirectory(stage.get(), field, readableField(fieldName));
            } else {
                PathChooserSupport.chooseFile(stage.get(), field, readableField(fieldName));
            }
        });
        row.getChildren().addAll(field, browse);
        if (detect) {
            Button detectButton = secondaryButton("Detect");
            LauncherTooltips.install(detectButton, "Use the default Hytale mods folder");
            detectButton.setMinWidth(78);
            detectButton.setPrefWidth(78);
            detectButton.setOnAction(event -> field.setText(HytalePathDetector.defaultModsDirectory().toString()));
            row.getChildren().add(detectButton);
        }
        return row;
    }


}
