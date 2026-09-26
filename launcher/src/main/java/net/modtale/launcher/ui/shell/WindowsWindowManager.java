package net.modtale.launcher.ui.shell;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

/** Gives the custom JavaFX frame native Windows move, resize, maximize, and Snap behavior. */
final class WindowsWindowManager {
    private static final int GWL_STYLE = -16;
    private static final long WS_POPUP = 0x8000_0000L;
    private static final long WS_CAPTION = 0x00C0_0000L;
    private static final long WS_THICKFRAME = 0x0004_0000L;
    private static final int WM_NCCALCSIZE = 0x0083;
    private static final int WM_GETMINMAXINFO = 0x0024;
    private static final int WM_NCLBUTTONDOWN = 0x00A1;
    private static final int HTCAPTION = 2;
    private static final int SW_MINIMIZE = 6;
    private static final int SW_MAXIMIZE = 3;
    private static final int SW_RESTORE = 9;
    private static final int SWP_FRAMECHANGED = 0x0020;
    private static final int SWP_NOACTIVATE = 0x0010;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final long SUBCLASS_ID = 0x4d4f4454L;

    private final User32 user32;
    private final ComCtl32 comctl32;
    private final Pointer window;
    private final ComCtl32.SubclassProc subclass;

    private WindowsWindowManager(User32 user32, ComCtl32 comctl32, Pointer window) {
        this.user32 = user32;
        this.comctl32 = comctl32;
        this.window = window;
        this.subclass = this::windowProc;
    }

    static WindowsWindowManager attach(Stage stage) {
        if (!System.getProperty("os.name", "").startsWith("Windows")) return null;
        try {
            User32 user32 = Native.load("user32", User32.class);
            ComCtl32 comctl32 = Native.load("comctl32", ComCtl32.class);
            Pointer window = primaryWindow(user32, stage.getTitle());
            if (window == null) return null;
            WindowsWindowManager manager = new WindowsWindowManager(user32, comctl32, window);
            if (!comctl32.SetWindowSubclass(window, manager.subclass, SUBCLASS_ID, 0)) return null;
            long style = user32.GetWindowLongPtrW(window, GWL_STYLE);
            user32.SetWindowLongPtrW(window, GWL_STYLE, (style & ~WS_POPUP) | WS_CAPTION | WS_THICKFRAME);
            user32.SetWindowPos(window, null, 0, 0, 0, 0,
                    SWP_FRAMECHANGED | SWP_NOACTIVATE | SWP_NOZORDER | SWP_NOSIZE | SWP_NOMOVE);
            stage.addEventHandler(WindowEvent.WINDOW_HIDING, event -> manager.detach());
            return manager;
        } catch (RuntimeException | LinkageError failure) {
            System.err.println("Could not enable native Windows window management: " + failure);
            return null;
        }
    }

    boolean beginMove() {
        return beginNonClientMove(HTCAPTION);
    }

    void minimize() {
        user32.ShowWindow(window, SW_MINIMIZE);
    }

    void toggleMaximized() {
        user32.ShowWindow(window, user32.IsZoomed(window) != 0 ? SW_RESTORE : SW_MAXIMIZE);
    }

    boolean beginResize(LinuxWindowManagerSupport.ResizeDirection direction) {
        int hitTest = switch (direction) {
            case LEFT -> 10;
            case RIGHT -> 11;
            case TOP -> 12;
            case TOP_LEFT -> 13;
            case TOP_RIGHT -> 14;
            case BOTTOM -> 15;
            case BOTTOM_LEFT -> 16;
            case BOTTOM_RIGHT -> 17;
        };
        return beginNonClientMove(hitTest);
    }

    private boolean beginNonClientMove(int hitTest) {
        if (user32.IsWindow(window) == 0) return false;
        user32.ReleaseCapture();
        user32.SendMessageW(window, WM_NCLBUTTONDOWN, hitTest, 0);
        return true;
    }

    private long windowProc(Pointer hwnd, int message, long wParam, long lParam,
                            long subclassId, long referenceData) {
        if (message == WM_NCCALCSIZE && wParam != 0) {
            // Preserve JavaFX's client area while retaining a resizable native frame.
            return 0;
        }
        if (message == WM_GETMINMAXINFO && lParam != 0) {
            setMaximizedWorkArea(hwnd, new Pointer(lParam));
        }
        return comctl32.DefSubclassProc(hwnd, message, wParam, lParam);
    }

    private void setMaximizedWorkArea(Pointer hwnd, Pointer minMaxInfo) {
        Pointer monitor = user32.MonitorFromWindow(hwnd, 2);
        if (monitor == null) return;
        com.sun.jna.Memory info = new com.sun.jna.Memory(40);
        info.setInt(0, 40);
        if (user32.GetMonitorInfoW(monitor, info) == 0) return;
        int monitorLeft = info.getInt(4);
        int monitorTop = info.getInt(8);
        int workLeft = info.getInt(20);
        int workTop = info.getInt(24);
        int workRight = info.getInt(28);
        int workBottom = info.getInt(32);
        minMaxInfo.setInt(8, workRight - workLeft);
        minMaxInfo.setInt(12, workBottom - workTop);
        minMaxInfo.setInt(16, workLeft - monitorLeft);
        minMaxInfo.setInt(20, workTop - monitorTop);
    }

    private void detach() {
        if (user32.IsWindow(window) != 0) {
            comctl32.RemoveWindowSubclass(window, subclass, SUBCLASS_ID);
        }
    }

    private static Pointer primaryWindow(User32 user32, String title) {
        long processId = ProcessHandle.current().pid();
        Pointer[] found = new Pointer[1];
        user32.EnumWindows((window, ignored) -> {
            IntByReference windowProcessId = new IntByReference();
            user32.GetWindowThreadProcessId(window, windowProcessId);
            if (windowProcessId.getValue() != processId || user32.IsWindowVisible(window) == 0) return true;
            char[] windowTitle = new char[256];
            user32.GetWindowTextW(window, windowTitle, windowTitle.length);
            if (!title.equals(Native.toString(windowTitle))) return true;
            found[0] = window;
            return false;
        }, null);
        return found[0];
    }

    private interface User32 extends StdCallLibrary {
        interface EnumWindowsProc extends StdCallCallback {
            boolean callback(Pointer window, Pointer data);
        }
        boolean EnumWindows(EnumWindowsProc callback, Pointer data);
        int GetWindowThreadProcessId(Pointer window, IntByReference processId);
        int IsWindowVisible(Pointer window);
        int IsWindow(Pointer window);
        int GetWindowTextW(Pointer window, char[] title, int maxCount);
        long GetWindowLongPtrW(Pointer window, int index);
        long SetWindowLongPtrW(Pointer window, int index, long value);
        boolean SetWindowPos(Pointer window, Pointer after, int x, int y, int width, int height, int flags);
        boolean ReleaseCapture();
        boolean ShowWindow(Pointer window, int command);
        int IsZoomed(Pointer window);
        long SendMessageW(Pointer window, int message, long wParam, long lParam);
        Pointer MonitorFromWindow(Pointer window, int flags);
        int GetMonitorInfoW(Pointer monitor, Pointer info);
    }

    private interface ComCtl32 extends StdCallLibrary {
        interface SubclassProc extends StdCallCallback {
            long callback(Pointer window, int message, long wParam, long lParam,
                          long subclassId, long referenceData);
        }
        boolean SetWindowSubclass(Pointer window, SubclassProc callback, long id, long data);
        boolean RemoveWindowSubclass(Pointer window, SubclassProc callback, long id);
        long DefSubclassProc(Pointer window, int message, long wParam, long lParam);
    }
}
