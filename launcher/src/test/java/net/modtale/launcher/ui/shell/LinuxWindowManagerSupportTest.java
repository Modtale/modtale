package net.modtale.launcher.ui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import javafx.scene.Cursor;
import javafx.scene.ImageCursor;
import org.junit.jupiter.api.Test;

class LinuxWindowManagerSupportTest {

    @Test
    void mapsJavaFxCursorsToNativeThemeNames() {
        assertEquals("default", LinuxWindowManagerSupport.systemCursorName(Cursor.DEFAULT));
        assertEquals("pointer", LinuxWindowManagerSupport.systemCursorName(Cursor.HAND));
        assertEquals("text", LinuxWindowManagerSupport.systemCursorName(Cursor.TEXT));
        assertEquals("ew-resize", LinuxWindowManagerSupport.systemCursorName(Cursor.H_RESIZE));
        assertEquals("nwse-resize", LinuxWindowManagerSupport.systemCursorName(Cursor.NW_RESIZE));
        assertEquals("grab", LinuxWindowManagerSupport.systemCursorName(Cursor.OPEN_HAND));
    }

    @Test
    void leavesCustomImageCursorsToJavaFx() {
        assertNull(LinuxWindowManagerSupport.systemCursorName(new ImageCursor()));
    }
}
