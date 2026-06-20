package net.modtale.launcher;

import javafx.application.Application;

public final class LauncherMain {

    private LauncherMain() {
    }

    public static void main(String[] args) {
        LauncherRenderSettings.configure();
        Application.launch(ModtaleLauncher.class, args);
    }
}
