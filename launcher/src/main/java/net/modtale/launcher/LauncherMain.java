package net.modtale.launcher;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import net.modtale.launcher.logging.LauncherLogging;
import net.modtale.launcher.logging.LauncherLog;
import net.modtale.launcher.logging.LauncherLogger;

public final class LauncherMain {

    private static final LauncherLogger LOG = LauncherLog.getLogger(LauncherMain.class);

    private LauncherMain() {
    }

    public static void main(String[] args) {
        Map<String, String> cursorSettings = LinuxCursorSettings.configure();
        LauncherLogging.initialize();
        LauncherRenderSettings.configure();
        if (!cursorSettings.isEmpty()) {
            LOG.info("Applied native Linux cursor settings: " + cursorSettings);
        }
        LOG.info("Starting Modtale Launcher " + System.getProperty("modtale.launcherVersion", "dev"));
        launchJavaFx(args);
    }

    private static void launchJavaFx(String[] args) {
        try {
            Class<?> bootstrap = Class.forName("net.modtale.launcher.LauncherApplicationBootstrap");
            Method launch = bootstrap.getDeclaredMethod("launch", String[].class);
            launch.invoke(null, (Object) args);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("Could not launch JavaFX", cause);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Could not initialize JavaFX", ex);
        }
    }
}
