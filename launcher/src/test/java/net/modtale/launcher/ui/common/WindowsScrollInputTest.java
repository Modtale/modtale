package net.modtale.launcher.ui.common;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.jna.Native;
import com.sun.jna.ptr.IntByReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.WINDOWS)
class WindowsScrollInputTest {
    @Test
    void animationPolicyReadsTheActualWindowsPreference() {
        var api = Native.load("user32", WindowsScrollInput.User32.class);
        IntByReference expected = new IntByReference();
        assertNotEquals(0, api.SystemParametersInfoW(0x1042, 0, expected, 0));
        assertEquals(expected.getValue() != 0, WindowsScrollInput.animationsEnabled());
    }
}
