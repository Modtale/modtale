package net.modtale.launcher.ui.shell;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import javafx.stage.Stage;

/** Hands custom chrome gestures to AppKit so Dock and Spaces own the transitions. */
final class MacWindowManager {
    private final Function message;
    private final Function selector;
    private final Pointer app;
    private final Pointer window;

    private MacWindowManager(Function message, Function selector, Pointer app, Pointer window) {
        this.message = message;
        this.selector = selector;
        this.app = app;
        this.window = window;
    }

    static MacWindowManager attach(Stage stage) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) return null;
        try {
            NativeLibrary objc = NativeLibrary.getInstance("objc");
            Function message = objc.getFunction("objc_msgSend");
            Function selector = objc.getFunction("sel_registerName");
            Pointer appClass = objc.getFunction("objc_getClass").invokePointer(new Object[]{"NSApplication"});
            Pointer app = message.invokePointer(new Object[]{appClass, selector.invokePointer(new Object[]{"sharedApplication"})});
            Pointer windows = message.invokePointer(new Object[]{app, selector.invokePointer(new Object[]{"windows"})});
            long count = message.invokeLong(new Object[]{windows, selector.invokePointer(new Object[]{"count"})});
            Pointer titleSelector = selector.invokePointer(new Object[]{"title"});
            Pointer utf8Selector = selector.invokePointer(new Object[]{"UTF8String"});
            Pointer atIndex = selector.invokePointer(new Object[]{"objectAtIndex:"});
            for (long i = 0; i < count; i++) {
                Pointer window = message.invokePointer(new Object[]{windows, atIndex, i});
                Pointer title = message.invokePointer(new Object[]{window, titleSelector});
                Pointer utf8 = title == null ? null : message.invokePointer(new Object[]{title, utf8Selector});
                if (utf8 != null && stage.getTitle().equals(utf8.getString(0))) {
                    return new MacWindowManager(message, selector, app, window);
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            System.err.println("Could not enable native macOS window management: " + failure);
        }
        return null;
    }

    boolean beginMove() {
        Pointer event = sendPointer(app, "currentEvent");
        if (event == null) return false;
        message.invokeVoid(new Object[]{window, sel("performWindowDragWithEvent:"), event});
        return true;
    }

    void minimize() {
        message.invokeVoid(new Object[]{window, sel("miniaturize:"), null});
    }

    void toggleMaximized(Stage stage) {
        boolean maximized = isMaximized() || stage.isMaximized();
        message.invokeVoid(new Object[]{window, sel("zoom:"), null});
        if (isMaximized() == maximized) stage.setMaximized(!maximized);
    }

    boolean isMaximized() {
        return (Byte) message.invoke(Byte.TYPE, new Object[]{window, sel("isZoomed")}) != 0;
    }

    private Pointer sendPointer(Pointer target, String method) {
        return message.invokePointer(new Object[]{target, sel(method)});
    }

    private Pointer sel(String name) {
        return selector.invokePointer(new Object[]{name});
    }
}
