package net.modtale.launcher.platform;

import com.sun.jna.Native;
import com.sun.jna.WString;
import com.sun.jna.win32.StdCallLibrary;

/** Matches the Java process to the installed launcher shortcut before JavaFX opens a window. */
public final class WindowsAppIdentity {
    public static final String APP_USER_MODEL_ID = "net.modtale.launcher";

    private WindowsAppIdentity() {}

    public static void initialize() {
        if (!System.getProperty("os.name", "").startsWith("Windows")) return;
        try {
            Shell32 shell32 = Native.load("shell32", Shell32.class);
            int result = shell32.SetCurrentProcessExplicitAppUserModelID(new WString(APP_USER_MODEL_ID));
            if (result != 0) {
                System.err.println("Could not set Windows launcher app identity: HRESULT " + result);
            }
        } catch (RuntimeException | LinkageError failure) {
            System.err.println("Could not set Windows launcher app identity: " + failure);
        }
    }

    private interface Shell32 extends StdCallLibrary {
        int SetCurrentProcessExplicitAppUserModelID(WString appId);
    }
}
