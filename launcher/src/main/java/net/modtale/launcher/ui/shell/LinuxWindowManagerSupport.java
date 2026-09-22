package net.modtale.launcher.ui.shell;

import javafx.scene.input.MouseEvent;
import javafx.stage.Stage;
import net.modtale.launcher.platform.LinuxDesktopBackend;

final class LinuxWindowManagerSupport {
    enum ResizeDirection {
        TOP_LEFT(0), TOP(1), TOP_RIGHT(2), RIGHT(3),
        BOTTOM_RIGHT(4), BOTTOM(5), BOTTOM_LEFT(6), LEFT(7);

        private final int nativeCode;
        ResizeDirection(int nativeCode) { this.nativeCode = nativeCode; }
    }

    private LinuxWindowManagerSupport() {}

    static boolean beginMove(Stage stage, MouseEvent event) {
        return stage != null && event != null && LinuxDesktopBackend.beginMoveResize(8);
    }

    static boolean beginResize(Stage stage, MouseEvent event, ResizeDirection direction) {
        return stage != null && event != null && direction != null
                && LinuxDesktopBackend.beginMoveResize(direction.nativeCode);
    }
}
