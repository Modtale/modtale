package net.modtale.launcher;

import javafx.application.Application;
import javafx.stage.Stage;
import net.modtale.launcher.ui.shell.LauncherRuntime;
import net.modtale.launcher.ui.common.LauncherFonts;

public final class ModtaleLauncher extends Application {

    private LauncherRuntime runtime;

    public static void main(String[] args) {
        LauncherMain.main(args);
    }

    @Override
    public void start(Stage stage) {
        LauncherFonts.load();
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
