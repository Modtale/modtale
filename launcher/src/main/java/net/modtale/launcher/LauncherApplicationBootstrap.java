package net.modtale.launcher;

import javafx.application.Application;

final class LauncherApplicationBootstrap {

    private LauncherApplicationBootstrap() {
    }

    static void launch(String[] args) {
        Application.launch(ModtaleLauncher.class, args);
    }
}
