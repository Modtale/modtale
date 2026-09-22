package net.modtale.launcher.ui.common;

import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

/** Honors the same Windows animation preference Chromium uses. */
final class WindowsScrollInput {
    private static User32 user32;
    private static boolean unavailable;

    private WindowsScrollInput() {}

    static boolean animationsEnabled() {
        if (unavailable) return true;
        try {
            if (user32 == null) user32 = Native.load("user32", User32.class);
            IntByReference enabled = new IntByReference(1);
            return user32.SystemParametersInfoW(0x1042, 0, enabled, 0) == 0 || enabled.getValue() != 0;
        } catch (RuntimeException | LinkageError failure) {
            unavailable = true;
            return true;
        }
    }

    interface User32 extends StdCallLibrary {
        int SystemParametersInfoW(int action, int parameter, IntByReference value, int flags);
    }
}
