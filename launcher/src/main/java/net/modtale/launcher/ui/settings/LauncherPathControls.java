package net.modtale.launcher.ui.settings;

import static net.modtale.launcher.ui.common.LauncherUi.miniIcon;
import static net.modtale.launcher.ui.common.LauncherUi.readableField;
import static net.modtale.launcher.ui.common.LauncherUi.secondaryButton;

import java.util.function.Supplier;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
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
            detectButton.setMinWidth(78);
            detectButton.setPrefWidth(78);
            detectButton.setOnAction(event -> field.setText(HytalePathDetector.defaultModsDirectory().toString()));
            row.getChildren().add(detectButton);
        }
        return row;
    }

    public static Node pathSummary(String title, TextField source) {
        HBox row = new HBox(12);
        row.getStyleClass().add("row-card");
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        StackPane icon = miniIcon("F");
        VBox copy = new VBox(4);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("row-title");
        Label value = new Label();
        value.textProperty().bind(source.textProperty());
        value.getStyleClass().add("row-subtitle");
        copy.getChildren().addAll(titleLabel, value);
        HBox.setHgrow(copy, Priority.ALWAYS);
        row.getChildren().addAll(icon, copy);
        return row;
    }
}
