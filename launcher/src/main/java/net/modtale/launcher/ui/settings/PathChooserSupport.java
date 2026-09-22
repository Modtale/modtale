package net.modtale.launcher.ui.settings;

import java.io.File;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

public final class PathChooserSupport {

    private PathChooserSupport() {
    }

    public static void chooseDirectory(Stage stage, TextField field, String title) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(title);
        setInitialDirectory(chooser, field.getText());
        File selected = chooser.showDialog(stage);
        if (selected != null) {
            field.setText(selected.toPath().toString());
        }
    }

    public static void chooseFile(Stage stage, TextField field, String title) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(title);
        setInitialDirectory(chooser, field.getText());
        File selected = chooser.showOpenDialog(stage);
        if (selected != null) {
            field.setText(selected.toPath().toString());
        }
    }

    private static void setInitialDirectory(DirectoryChooser chooser, String rawPath) {
        File directory = initialDirectory(rawPath);
        if (directory != null) {
            chooser.setInitialDirectory(directory);
        }
    }

    private static void setInitialDirectory(FileChooser chooser, String rawPath) {
        File directory = initialDirectory(rawPath);
        if (directory != null) {
            chooser.setInitialDirectory(directory);
        }
    }

    private static File initialDirectory(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return null;
        }
        File file = new File(rawPath);
        File directory = file.isDirectory() ? file : file.getParentFile();
        return directory != null && directory.isDirectory() ? directory : null;
    }
}
