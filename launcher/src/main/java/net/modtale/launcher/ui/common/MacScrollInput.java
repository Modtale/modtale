package net.modtale.launcher.ui.common;

import com.sun.jna.Function;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import net.modtale.launcher.logging.LauncherLog;

/** Reads AppKit's current event without installing or replacing any event handlers. */
final class MacScrollInput {
    private static Cocoa cocoa;
    private static boolean attempted;

    private MacScrollInput() {}

    static void install() {
        if (attempted) return;
        attempted = true;
        try {
            cocoa = new Cocoa();
        } catch (RuntimeException | LinkageError failure) {
            unavailable(failure);
        }
    }

    static NativeScrollInput.Sample current() {
        if (cocoa == null) return null;
        try {
            Pointer app = cocoa.pointer(cocoa.application, cocoa.sharedApplication);
            Pointer event = cocoa.pointer(app, cocoa.currentEvent);
            return fromEvent(event, System.nanoTime());
        } catch (RuntimeException | LinkageError failure) {
            cocoa = null;
            unavailable(failure);
            return null;
        }
    }

    static boolean available() { return cocoa != null; }

    static NativeScrollInput.Sample fromEvent(Pointer event, long now) {
        if (event == null || cocoa.message.invokeLong(new Object[]{event, cocoa.type}) != 22) return null;
        boolean precise = (Byte) cocoa.message.invoke(Byte.TYPE, new Object[]{event, cocoa.precise}) != 0;
        double x = cocoa.number(event, precise ? cocoa.preciseX : cocoa.deltaX);
        double y = cocoa.number(event, precise ? cocoa.preciseY : cocoa.deltaY);
        double timestamp = cocoa.number(event, cocoa.timestamp);
        return sample(precise, x, y, timestamp, now);
    }

    static boolean animationsEnabled() {
        if (cocoa == null) return false;
        Pointer defaults = cocoa.pointer(cocoa.defaultsClass, cocoa.standardDefaults);
        return (Byte) cocoa.message.invoke(Byte.TYPE,
                new Object[]{defaults, cocoa.boolForKey, cocoa.animationKey}) != 0;
    }

    static NativeScrollInput.Sample sample(boolean precise, double x, double y, double timestamp, long now) {
        // AppKit deltas are points, including on Retina displays. Chromium uses
        // 40 points per coarse Cocoa tick and preserves precise/momentum pixels.
        double multiplier = precise ? 1 : 40;
        long delay = now - Math.round(timestamp * 1_000_000_000.0);
        if (delay < 0 || delay > 250_000_000) delay = 0;
        return new NativeScrollInput.Sample(-x * multiplier, -y * multiplier, precise, delay);
    }

    private static void unavailable(Throwable failure) {
        LauncherLog.getLogger(MacScrollInput.class).warn("Native AppKit scroll metadata unavailable: " + failure);
    }

    private static final class Cocoa {
        private final NativeLibrary library = NativeLibrary.getInstance("objc");
        private final Function message = library.getFunction("objc_msgSend");
        private final Function selector = library.getFunction("sel_registerName");
        private final Pointer application = library.getFunction("objc_getClass")
                .invokePointer(new Object[]{"NSApplication"});
        private final Pointer sharedApplication = selector("sharedApplication");
        private final Pointer currentEvent = selector("currentEvent");
        private final Pointer type = selector("type");
        private final Pointer precise = selector("hasPreciseScrollingDeltas");
        private final Pointer preciseX = selector("scrollingDeltaX");
        private final Pointer preciseY = selector("scrollingDeltaY");
        private final Pointer deltaX = selector("deltaX");
        private final Pointer deltaY = selector("deltaY");
        private final Pointer timestamp = selector("timestamp");
        private final Pointer defaultsClass = library.getFunction("objc_getClass")
                .invokePointer(new Object[]{"NSUserDefaults"});
        private final Pointer standardDefaults = selector("standardUserDefaults");
        private final Pointer boolForKey = selector("boolForKey:");
        private final Pointer animationKey;

        private Cocoa() {
            Pointer stringClass = library.getFunction("objc_getClass").invokePointer(new Object[]{"NSString"});
            animationKey = message.invokePointer(new Object[]{stringClass, selector("stringWithUTF8String:"),
                    "NSScrollAnimationEnabled"});
            // Keep the autoreleased key alive for this process-wide adapter.
            pointer(animationKey, selector("retain"));
        }

        private Pointer selector(String name) { return selector.invokePointer(new Object[]{name}); }
        private Pointer pointer(Pointer receiver, Pointer method) {
            return message.invokePointer(new Object[]{receiver, method});
        }
        private double number(Pointer receiver, Pointer method) {
            return message.invokeDouble(new Object[]{receiver, method});
        }
    }
}
