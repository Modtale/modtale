package net.modtale.launcher;

import javafx.application.Application;
import net.modtale.launcher.logging.LauncherLogging;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class LauncherMain {

    private static final Logger LOG = LogManager.getLogger(LauncherMain.class);

    private LauncherMain() {
    }

    public static void main(String[] args) {
        LauncherLogging.initialize();
        LauncherRenderSettings.configure();
        LOG.info("Starting Modtale Launcher " + System.getProperty("modtale.launcherVersion", "dev"));
        Application.launch(ModtaleLauncher.class, args);
    }
}
