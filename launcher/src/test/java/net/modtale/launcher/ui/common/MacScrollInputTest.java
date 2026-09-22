package net.modtale.launcher.ui.common;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

@EnabledOnOs(OS.MAC)
class MacScrollInputTest {
    @BeforeAll
    static void toolkit() {
        try { Platform.startup(() -> Platform.setImplicitExit(false)); }
        catch (IllegalStateException alreadyStarted) { }
    }

    @Test
    void appKitAdapterReadsActualQuartzWheelAndPreciseEvents() throws Exception {
        FutureTask<Void> task = new FutureTask<>(() -> {
            MacScrollInput.install();
            assertTrue(MacScrollInput.available(), "The shipped Objective-C bridge must initialize");
            MacScrollInput.animationsEnabled();
            CoreGraphics graphics = Native.load("/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", CoreGraphics.class);
            NativeLibrary objc = NativeLibrary.getInstance("objc");
            Pointer eventClass = objc.getFunction("objc_getClass").invokePointer(new Object[]{"NSEvent"});
            Pointer wrap = objc.getFunction("sel_registerName").invokePointer(new Object[]{"eventWithCGEvent:"});
            for (int unit : new int[]{0, 1}) {
                Pointer cgEvent = graphics.CGEventCreateScrollWheelEvent(null, unit, 1, -12);
                assertNotNull(cgEvent);
                try {
                    Pointer nsEvent = objc.getFunction("objc_msgSend")
                            .invokePointer(new Object[]{eventClass, wrap, cgEvent});
                    assertNotNull(nsEvent);
                    var sample = MacScrollInput.fromEvent(nsEvent, System.nanoTime());
                    assertNotNull(sample);
                    assertEquals(unit == 0, sample.precise());
                    assertEquals(0, sample.x(), 0.0);
                    assertTrue(sample.y() > 0, "Both native event types must scroll down");
                    if (unit == 0) assertEquals(12, sample.y());
                } finally {
                    graphics.CFRelease(cgEvent);
                }
            }
            return null;
        });
        Platform.runLater(task);
        task.get(15, TimeUnit.SECONDS);
    }

    interface CoreGraphics extends Library {
        Pointer CGEventCreateScrollWheelEvent(Pointer source, int unit, int wheelCount, int firstDelta, Object... additionalDeltas);
        void CFRelease(Pointer object);
    }
}
