package net.modtale.launcher.ui.browse.card;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import net.modtale.launcher.model.project.ProjectClassification;

public final class ProjectCardFormatter {

    private static final NumberFormat NUMBER_FORMAT = NumberFormat.getIntegerInstance(Locale.US);

    private ProjectCardFormatter() {
    }

    public static String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public static String number(int value) {
        return NUMBER_FORMAT.format(value);
    }

    public static String classificationLabel(String classification) {
        return ProjectClassification.compactLabelFor(classification);
    }

    public static String timeAgo(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return "Unknown";
        }
        try {
            Instant instant = Instant.parse(rawDate);
            return timeAgo(instant);
        } catch (DateTimeParseException ex) {
            try {
                return timeAgo(LocalDateTime.parse(rawDate).atZone(ZoneId.systemDefault()).toInstant());
            } catch (DateTimeParseException ignored) {
                try {
                    return timeAgo(LocalDate.parse(rawDate).atStartOfDay(ZoneId.systemDefault()).toInstant());
                } catch (DateTimeParseException ignoredAgain) {
                    return rawDate;
                }
            }
        }
    }

    private static String timeAgo(Instant instant) {
        Duration age = Duration.between(instant, Instant.now());
        if (age.isNegative()) {
            return "Just now";
        }
        long seconds = age.toSeconds();
        double interval = seconds / 31_536_000.0;
        if (interval > 1) {
            return (long) interval + "y ago";
        }
        interval = seconds / 2_592_000.0;
        if (interval > 1) {
            return (long) interval + "mo ago";
        }
        interval = seconds / 86_400.0;
        if (interval > 1) {
            return (long) interval + "d ago";
        }
        interval = seconds / 3_600.0;
        if (interval > 1) {
            return (long) interval + "h ago";
        }
        interval = seconds / 60.0;
        if (interval > 1) {
            return (long) interval + "m ago";
        }
        return "Just now";
    }
}
