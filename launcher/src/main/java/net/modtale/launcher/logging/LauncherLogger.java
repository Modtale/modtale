package net.modtale.launcher.logging;

import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Lightweight facade with the parameterized messages used throughout the launcher. */
public final class LauncherLogger {

    private final Logger delegate;

    LauncherLogger(Class<?> owner) {
        delegate = Logger.getLogger(owner.getName());
    }

    public void info(String message) {
        delegate.log(Level.INFO, message);
    }

    public void info(String message, Object... arguments) {
        log(Level.INFO, message, arguments);
    }

    public void warn(String message) {
        delegate.log(Level.WARNING, message);
    }

    public void warn(String message, Object... arguments) {
        log(Level.WARNING, message, arguments);
    }

    public void error(String message) {
        delegate.log(Level.SEVERE, message);
    }

    public void error(String message, Object... arguments) {
        log(Level.SEVERE, message, arguments);
    }

    private void log(Level level, String pattern, Object[] arguments) {
        Object[] values = arguments == null ? new Object[0] : arguments;
        Throwable throwable = values.length > 0 && values[values.length - 1] instanceof Throwable cause
                ? cause
                : null;
        int valueCount = throwable == null ? values.length : values.length - 1;
        String message = format(pattern, values, valueCount);
        if (throwable == null) delegate.log(level, message);
        else delegate.log(level, message, throwable);
    }

    static String format(String pattern, Object[] values, int valueCount) {
        String source = pattern == null ? "null" : pattern;
        StringBuilder formatted = new StringBuilder(source.length() + valueCount * 12);
        int cursor = 0;
        int valueIndex = 0;
        while (valueIndex < valueCount) {
            int placeholder = source.indexOf("{}", cursor);
            if (placeholder < 0) break;
            formatted.append(source, cursor, placeholder).append(String.valueOf(values[valueIndex++]));
            cursor = placeholder + 2;
        }
        formatted.append(source, cursor, source.length());
        if (valueIndex < valueCount) {
            formatted.append(' ').append(Arrays.toString(Arrays.copyOfRange(values, valueIndex, valueCount)));
        }
        return formatted.toString();
    }
}
