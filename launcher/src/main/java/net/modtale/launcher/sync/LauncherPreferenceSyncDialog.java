package net.modtale.launcher.sync;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.function.Supplier;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.modtale.launcher.ui.common.StatusModal;

final class LauncherPreferenceSyncDialog {

    private static final DateTimeFormatter SAVED_AT_FORMAT =
            DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a");

    private final int remoteProjects;
    private final int localProjects;
    private final String updatedAt;
    private final int remoteConfigs;
    private final int localConfigs;

    private LauncherPreferenceSyncDialog(
            int remoteProjects,
            int localProjects,
            String updatedAt, int remoteConfigs, int localConfigs
    ) {
        this.remoteConfigs = remoteConfigs;
        this.localConfigs = localConfigs;
        this.remoteProjects = Math.max(0, remoteProjects);
        this.localProjects = Math.max(0, localProjects);
        this.updatedAt = updatedAt == null ? "" : updatedAt.trim();
    }

    static StatusModal.Result showAndWait(
            Supplier<StackPane> host,
            int remoteProjects,
            int localProjects,
            String updatedAt, int remoteConfigs, int localConfigs
    ) {
        LauncherPreferenceSyncDialog dialog = new LauncherPreferenceSyncDialog(
                remoteProjects,
                localProjects,
                updatedAt, remoteConfigs, localConfigs
        );
        StatusModal.Result result = StatusModal.builder(host)
                .type(StatusModal.Type.INFO)
                .title("Different launcher settings found")
                .message("Close Hytale before loading. Your current configs will be backed up.")
                .secondaryLabel("Use this device")
                .actionLabel("Load from Modtale")
                .content(dialog.summaryCard())
                .showAndWait();
        return result;
    }

    private VBox summaryCard() {
        VBox summary = new VBox(8);
        summary.getStyleClass().add("preference-sync-summary");
        summary.setAlignment(Pos.CENTER);
        summary.getChildren().add(summaryLine("Modtale account",
                remoteProjects + " installed project" + plural(remoteProjects) + ", " + remoteConfigs + " configs"));
        summary.getChildren().add(summaryLine("This device",
                localProjects + " installed project" + plural(localProjects) + ", " + localConfigs + " configs"));
        summary.getChildren().add(summaryLine("Last saved", savedAtLabel()));
        return summary;
    }

    private HBox summaryLine(String labelText, String valueText) {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER);
        Label label = new Label(labelText);
        label.getStyleClass().add("preference-sync-summary-label");
        label.setAlignment(Pos.CENTER);
        Label value = new Label(valueText);
        value.getStyleClass().add("preference-sync-summary-value");
        value.setAlignment(Pos.CENTER);
        row.getChildren().addAll(label, value);
        return row;
    }

    private String savedAtLabel() {
        if (updatedAt.isBlank()) {
            return "Previously saved";
        }
        for (DateParser parser : new DateParser[]{
                this::parseLocalDateTime,
                this::parseOffsetDateTime,
                this::parseInstant
        }) {
            String formatted = parser.parse(updatedAt);
            if (!formatted.isBlank()) {
                return formatted;
            }
        }
        return updatedAt;
    }

    private String parseLocalDateTime(String value) {
        try {
            return SAVED_AT_FORMAT.format(LocalDateTime.parse(value));
        } catch (DateTimeParseException ignored) {
            return "";
        }
    }

    private String parseOffsetDateTime(String value) {
        try {
            return SAVED_AT_FORMAT.format(OffsetDateTime.parse(value).toLocalDateTime());
        } catch (DateTimeParseException ignored) {
            return "";
        }
    }

    private String parseInstant(String value) {
        try {
            return SAVED_AT_FORMAT.format(LocalDateTime.ofInstant(Instant.parse(value), ZoneId.systemDefault()));
        } catch (DateTimeParseException ignored) {
            return "";
        }
    }

    private static String plural(int count) {
        return count == 1 ? "" : "s";
    }

    @FunctionalInterface
    private interface DateParser {
        String parse(String value);
    }
}
