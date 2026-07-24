package net.modtale.launcher;

import javafx.application.Application;
import javafx.stage.Stage;
import net.modtale.launcher.ui.shell.LauncherRuntime;

public final class ModtaleLauncher extends Application {

    private LauncherRuntime runtime;

    public static void main(String[] args) {
        LauncherMain.main(args);
    }

    @Override
    public void start(Stage stage) {
        runtime = LauncherRuntime.create();
        runtime.start(stage, getParameters());
    }

    @Override
    public void stop() {
        if (runtime != null) {
            runtime.shutdown();
        }
    }
}
