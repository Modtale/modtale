package net.modtale.launcher.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LauncherLoggerTest {

    @Test
    void formatsParameterizedMessagesWithoutRegexOrTemporaryReplacementStrings() {
        assertEquals("GET /projects/42 -> HTTP 200",
                LauncherLogger.format("GET {} -> HTTP {}", new Object[]{"/projects/42", 200}, 2));
        assertEquals("plain", LauncherLogger.format("plain", new Object[0], 0));
        assertEquals("one [two, 3]", LauncherLogger.format("one", new Object[]{"two", 3}, 2));
    }
}
