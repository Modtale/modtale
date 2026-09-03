package net.modtale.launcher;

import javafx.application.Application;
import net.modtale.launcher.logging.LauncherLogging;
import net.modtale.launcher.logging.LauncherLog;
import net.modtale.launcher.logging.LauncherLogger;

public final class LauncherMain {

    private static final LauncherLogger LOG = LauncherLog.getLogger(LauncherMain.class);

    private LauncherMain() {
    }

    public static void main(String[] args) {
        LauncherLogging.initialize();
        LauncherRenderSettings.configure();
        LOG.info("Starting Modtale Launcher " + System.getProperty("modtale.launcherVersion", "dev"));
        Application.launch(ModtaleLauncher.class, args);
    }
}
