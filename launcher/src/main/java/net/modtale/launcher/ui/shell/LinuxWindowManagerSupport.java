package net.modtale.launcher.ui.shell;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.NativeLongByReference;
import com.sun.jna.ptr.PointerByReference;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.Set;
import javafx.scene.input.MouseEvent;
import javafx.stage.Screen;
import javafx.stage.Stage;

final class LinuxWindowManagerSupport {

    enum ResizeDirection {
        TOP_LEFT(0),
        TOP(1),
        TOP_RIGHT(2),
        RIGHT(3),
        BOTTOM_RIGHT(4),
        BOTTOM(5),
        BOTTOM_LEFT(6),
        LEFT(7);

        private final int ewmhCode;

        ResizeDirection(int ewmhCode) {
            this.ewmhCode = ewmhCode;
        }
    }

    private static final int CLIENT_MESSAGE = 33;
    private static final int MOVE = 8;
    private static final int SOURCE_INDICATION_APPLICATION = 1;
    private static final int BUTTON_PRIMARY = 1;
    private static final NativeLong CURRENT_TIME = new NativeLong(0);
    private static final NativeLong XA_ANY_PROPERTY_TYPE = new NativeLong(0);
    private static final NativeLong XA_CARDINAL = new NativeLong(6);
    private static final NativeLong XA_WINDOW = new NativeLong(33);
    private static final NativeLong SUBSTRUCTURE_NOTIFY_MASK = new NativeLong(1L << 19);
    private static final NativeLong SUBSTRUCTURE_REDIRECT_MASK = new NativeLong(1L << 20);
    private static final int MAX_WINDOW_SEARCH_DEPTH = 8;

    private LinuxWindowManagerSupport() {
    }

    static boolean beginMove(Stage stage, MouseEvent event) {
        return beginMoveResize(stage, event, MOVE);
    }

    static boolean beginResize(Stage stage, MouseEvent event, ResizeDirection direction) {
        return direction != null && beginMoveResize(stage, event, direction.ewmhCode);
    }

    private static boolean beginMoveResize(Stage stage, MouseEvent event, int direction) {
        if (!isLinux() || stage == null || event == null || System.getenv("DISPLAY") == null) {
            return false;
        }

        Pointer display = null;
        try {
            display = X11.INSTANCE.XOpenDisplay(null);
            if (display == null) {
                return false;
            }

            NativeLong root = X11.INSTANCE.XDefaultRootWindow(display);
            NativeLong moveresize = X11.INSTANCE.XInternAtom(display, "_NET_WM_MOVERESIZE", 0);
            if (moveresize.longValue() == 0) {
                return false;
            }

            OptionalLong window = findStageWindow(display, root, stage);
            if (window.isEmpty()) {
                return false;
            }

            XClientMessageEvent message = new XClientMessageEvent();
            message.type = CLIENT_MESSAGE;
            message.serial = new NativeLong(0);
            message.sendEvent = 1;
            message.display = display;
            message.window = new NativeLong(window.getAsLong());
            message.messageType = moveresize;
            message.format = 32;
            message.data[0] = new NativeLong(rootCoordinate(event, true));
            message.data[1] = new NativeLong(rootCoordinate(event, false));
            message.data[2] = new NativeLong(direction);
            message.data[3] = new NativeLong(BUTTON_PRIMARY);
            message.data[4] = new NativeLong(SOURCE_INDICATION_APPLICATION);
            message.write();

            X11.INSTANCE.XUngrabPointer(display, CURRENT_TIME);
            NativeLong mask = new NativeLong(SUBSTRUCTURE_REDIRECT_MASK.longValue() | SUBSTRUCTURE_NOTIFY_MASK.longValue());
            int sent = X11.INSTANCE.XSendEvent(display, root, false, mask, message);
            X11.INSTANCE.XFlush(display);
            return sent != 0;
        } catch (UnsatisfiedLinkError | RuntimeException ex) {
            return false;
        } finally {
            if (display != null) {
                X11.INSTANCE.XCloseDisplay(display);
            }
        }
    }

    private static long rootCoordinate(MouseEvent event, boolean horizontal) {
        double scale = Screen.getScreensForRectangle(event.getScreenX(), event.getScreenY(), 1, 1).stream()
                .findFirst()
                .map(screen -> horizontal ? screen.getOutputScaleX() : screen.getOutputScaleY())
                .filter(outputScale -> outputScale > 0)
                .orElse(1.0);
        return Math.round((horizontal ? event.getScreenX() : event.getScreenY()) * scale);
    }

    private static OptionalLong findStageWindow(Pointer display, NativeLong root, Stage stage) {
        NativeLong pidAtom = X11.INSTANCE.XInternAtom(display, "_NET_WM_PID", 1);
        NativeLong wmStateAtom = X11.INSTANCE.XInternAtom(display, "WM_STATE", 1);
        NativeLong netWmNameAtom = X11.INSTANCE.XInternAtom(display, "_NET_WM_NAME", 1);
        NativeLong utf8StringAtom = X11.INSTANCE.XInternAtom(display, "UTF8_STRING", 1);
        long pid = ProcessHandle.current().pid();
        String title = stage.getTitle();

        OptionalLong activeWindow = findActiveStageWindow(display, root, pidAtom, wmStateAtom,
                netWmNameAtom, utf8StringAtom, pid, title);
        if (activeWindow.isPresent()) {
            return activeWindow;
        }

        return findWindowRecursive(display, root, pidAtom, wmStateAtom, netWmNameAtom,
                utf8StringAtom, pid, title, 0, new HashSet<>());
    }

    private static OptionalLong findActiveStageWindow(
            Pointer display,
            NativeLong root,
            NativeLong pidAtom,
            NativeLong wmStateAtom,
            NativeLong netWmNameAtom,
            NativeLong utf8StringAtom,
            long pid,
            String title
    ) {
        NativeLong activeWindowAtom = X11.INSTANCE.XInternAtom(display, "_NET_ACTIVE_WINDOW", 1);
        Long activeWindowId = readWindowProperty(display, root, activeWindowAtom);
        if (activeWindowId == null || activeWindowId == 0) {
            return OptionalLong.empty();
        }

        NativeLong activeWindow = new NativeLong(activeWindowId);
        return windowMatches(display, activeWindow, pidAtom, wmStateAtom, netWmNameAtom, utf8StringAtom, pid, title)
                ? OptionalLong.of(activeWindowId)
                : OptionalLong.empty();
    }

    private static OptionalLong findWindowRecursive(
            Pointer display,
            NativeLong window,
            NativeLong pidAtom,
            NativeLong wmStateAtom,
            NativeLong netWmNameAtom,
            NativeLong utf8StringAtom,
            long pid,
            String title,
            int depth,
            Set<Long> visited
    ) {
        long windowId = window.longValue();
        if (!visited.add(windowId) || depth > MAX_WINDOW_SEARCH_DEPTH) {
            return OptionalLong.empty();
        }
        if (windowMatches(display, window, pidAtom, wmStateAtom, netWmNameAtom, utf8StringAtom, pid, title)) {
            return OptionalLong.of(windowId);
        }

        NativeLongByReference root = new NativeLongByReference();
        NativeLongByReference parent = new NativeLongByReference();
        PointerByReference children = new PointerByReference();
        IntByReference childCount = new IntByReference();
        int queried = X11.INSTANCE.XQueryTree(display, window, root, parent, children, childCount);
        if (queried == 0 || childCount.getValue() <= 0 || children.getValue() == null) {
            return OptionalLong.empty();
        }

        try {
            Pointer childPointer = children.getValue();
            for (int i = childCount.getValue() - 1; i >= 0; i--) {
                NativeLong child = childPointer.getNativeLong((long) i * NativeLong.SIZE);
                OptionalLong match = findWindowRecursive(display, child, pidAtom, wmStateAtom, netWmNameAtom,
                        utf8StringAtom, pid, title, depth + 1, visited);
                if (match.isPresent()) {
                    return match;
                }
            }
        } finally {
            X11.INSTANCE.XFree(children.getValue());
        }
        return OptionalLong.empty();
    }

    private static boolean windowMatches(
            Pointer display,
            NativeLong window,
            NativeLong pidAtom,
            NativeLong wmStateAtom,
            NativeLong netWmNameAtom,
            NativeLong utf8StringAtom,
            long pid,
            String title
    ) {
        if (wmStateAtom.longValue() == 0 || !hasProperty(display, window, wmStateAtom, XA_ANY_PROPERTY_TYPE)) {
            return false;
        }

        if (title != null && !title.isBlank()) {
            String windowName = readStringProperty(display, window, netWmNameAtom, utf8StringAtom);
            if (windowName == null) {
                windowName = fetchWindowName(display, window);
            }
            return title.equals(windowName);
        }

        Long windowPid = readCardinalProperty(display, window, pidAtom);
        return windowPid != null && windowPid == pid;
    }

    private static boolean hasProperty(Pointer display, NativeLong window, NativeLong property, NativeLong requestedType) {
        PointerByReference value = new PointerByReference();
        NativeLongByReference actualType = new NativeLongByReference();
        IntByReference actualFormat = new IntByReference();
        NativeLongByReference itemCount = new NativeLongByReference();
        NativeLongByReference bytesAfter = new NativeLongByReference();
        int result = X11.INSTANCE.XGetWindowProperty(display, window, property, new NativeLong(0), new NativeLong(1),
                false, requestedType, actualType, actualFormat, itemCount, bytesAfter, value);
        if (value.getValue() != null) {
            X11.INSTANCE.XFree(value.getValue());
        }
        return result == 0 && actualType.getValue().longValue() != 0;
    }

    private static Long readWindowProperty(Pointer display, NativeLong window, NativeLong property) {
        if (property.longValue() == 0) {
            return null;
        }
        PointerByReference value = new PointerByReference();
        NativeLongByReference actualType = new NativeLongByReference();
        IntByReference actualFormat = new IntByReference();
        NativeLongByReference itemCount = new NativeLongByReference();
        NativeLongByReference bytesAfter = new NativeLongByReference();
        int result = X11.INSTANCE.XGetWindowProperty(display, window, property, new NativeLong(0), new NativeLong(1),
                false, XA_WINDOW, actualType, actualFormat, itemCount, bytesAfter, value);
        Pointer pointer = value.getValue();
        try {
            if (result != 0 || pointer == null || itemCount.getValue().longValue() <= 0 || actualFormat.getValue() != 32) {
                return null;
            }
            return Integer.toUnsignedLong(pointer.getInt(0));
        } finally {
            if (pointer != null) {
                X11.INSTANCE.XFree(pointer);
            }
        }
    }

    private static Long readCardinalProperty(Pointer display, NativeLong window, NativeLong property) {
        if (property.longValue() == 0) {
            return null;
        }
        PointerByReference value = new PointerByReference();
        NativeLongByReference actualType = new NativeLongByReference();
        IntByReference actualFormat = new IntByReference();
        NativeLongByReference itemCount = new NativeLongByReference();
        NativeLongByReference bytesAfter = new NativeLongByReference();
        int result = X11.INSTANCE.XGetWindowProperty(display, window, property, new NativeLong(0), new NativeLong(1),
                false, XA_CARDINAL, actualType, actualFormat, itemCount, bytesAfter, value);
        Pointer pointer = value.getValue();
        try {
            if (result != 0 || pointer == null || itemCount.getValue().longValue() <= 0 || actualFormat.getValue() != 32) {
                return null;
            }
            return Integer.toUnsignedLong(pointer.getInt(0));
        } finally {
            if (pointer != null) {
                X11.INSTANCE.XFree(pointer);
            }
        }
    }

    private static String readStringProperty(
            Pointer display,
            NativeLong window,
            NativeLong property,
            NativeLong requestedType
    ) {
        if (property.longValue() == 0 || requestedType.longValue() == 0) {
            return null;
        }
        PointerByReference value = new PointerByReference();
        NativeLongByReference actualType = new NativeLongByReference();
        IntByReference actualFormat = new IntByReference();
        NativeLongByReference itemCount = new NativeLongByReference();
        NativeLongByReference bytesAfter = new NativeLongByReference();
        int result = X11.INSTANCE.XGetWindowProperty(display, window, property, new NativeLong(0), new NativeLong(1024),
                false, requestedType, actualType, actualFormat, itemCount, bytesAfter, value);
        Pointer pointer = value.getValue();
        try {
            if (result != 0 || pointer == null || actualFormat.getValue() != 8 || itemCount.getValue().longValue() <= 0) {
                return null;
            }
            byte[] bytes = pointer.getByteArray(0, (int) itemCount.getValue().longValue());
            int length = 0;
            while (length < bytes.length && bytes[length] != 0) {
                length++;
            }
            return new String(bytes, 0, length, StandardCharsets.UTF_8);
        } finally {
            if (pointer != null) {
                X11.INSTANCE.XFree(pointer);
            }
        }
    }

    private static String fetchWindowName(Pointer display, NativeLong window) {
        PointerByReference name = new PointerByReference();
        int result = X11.INSTANCE.XFetchName(display, window, name);
        Pointer pointer = name.getValue();
        try {
            return result == 0 || pointer == null ? null : pointer.getString(0);
        } finally {
            if (pointer != null) {
                X11.INSTANCE.XFree(pointer);
            }
        }
    }

    private static boolean isLinux() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("linux");
    }

    @Structure.FieldOrder({"type", "serial", "sendEvent", "display", "window", "messageType", "format", "data"})
    public static class XClientMessageEvent extends Structure {
        public int type;
        public NativeLong serial;
        public int sendEvent;
        public Pointer display;
        public NativeLong window;
        public NativeLong messageType;
        public int format;
        public NativeLong[] data = {
                new NativeLong(0),
                new NativeLong(0),
                new NativeLong(0),
                new NativeLong(0),
                new NativeLong(0)
        };
    }

    private interface X11 extends Library {
        X11 INSTANCE = Native.load("X11", X11.class);

        Pointer XOpenDisplay(String displayName);

        int XCloseDisplay(Pointer display);

        NativeLong XDefaultRootWindow(Pointer display);

        NativeLong XInternAtom(Pointer display, String atomName, int onlyIfExists);

        int XSendEvent(Pointer display, NativeLong window, boolean propagate, NativeLong eventMask, XClientMessageEvent event);

        int XFlush(Pointer display);

        int XUngrabPointer(Pointer display, NativeLong time);

        int XQueryTree(
                Pointer display,
                NativeLong window,
                NativeLongByReference rootReturn,
                NativeLongByReference parentReturn,
                PointerByReference childrenReturn,
                IntByReference childCountReturn
        );

        int XFree(Pointer data);

        int XGetWindowProperty(
                Pointer display,
                NativeLong window,
                NativeLong property,
                NativeLong longOffset,
                NativeLong longLength,
                boolean delete,
                NativeLong requestedType,
                NativeLongByReference actualTypeReturn,
                IntByReference actualFormatReturn,
                NativeLongByReference itemCountReturn,
                NativeLongByReference bytesAfterReturn,
                PointerByReference propertyReturn
        );

        int XFetchName(Pointer display, NativeLong window, PointerByReference windowNameReturn);
    }
}
