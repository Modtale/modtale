package net.modtale.launcher.ui.project;

import static net.modtale.launcher.ui.common.LauncherUi.value;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import net.modtale.launcher.api.ModtaleApiClient;
import net.modtale.launcher.ui.common.LauncherIcons;

final class NativeReportModal {

    enum TargetType {
        PROJECT("PROJECT", "Project", "Content"),
        COMMENT("COMMENT", "Comment", "Comment"),
        USER("USER", "User", "User Profile");

        private final String apiValue;
        private final String label;
        private final String fallbackTitle;

        TargetType(String apiValue, String label, String fallbackTitle) {
            this.apiValue = apiValue;
            this.label = label;
            this.fallbackTitle = fallbackTitle;
        }
    }

    private static final double MODAL_WIDTH = 672;
    private static final List<ReportReason> REASONS = List.of(
            new ReportReason("MALWARE", "Malware / Virus"),
            new ReportReason("SPAM", "Spam / Misleading"),
            new ReportReason("INAPPROPRIATE", "Inappropriate Content"),
            new ReportReason("IP_INFRINGEMENT", "Intellectual Property Violation"),
            new ReportReason("HARASSMENT", "Harassment / Hate Speech"),
            new ReportReason("OTHER", "Other")
    );

    private final Supplier<StackPane> host;
    private final ModtaleApiClient apiClient;
    private final Executor executor;
    private final BiConsumer<String, String> toast;

    private StackPane overlay;
    private TargetType targetType = TargetType.PROJECT;
    private String targetId = "";
    private String targetTitle = "";
    private ReportReason selectedReason = REASONS.getFirst();
    private String description = "";
    private String errorMessage = "";
    private String reportId = "";
    private boolean dropdownOpen;
    private boolean submitting;
    private boolean submitted;

    NativeReportModal(
            Supplier<StackPane> host,
            ModtaleApiClient apiClient,
            Executor executor,
            BiConsumer<String, String> toast
    ) {
        this.host = host == null ? () -> null : host;
        this.apiClient = apiClient;
        this.executor = executor;
        this.toast = toast == null ? (title, message) -> {
        } : toast;
    }

    void show(TargetType targetType, String targetId, String targetTitle) {
        this.targetType = targetType == null ? TargetType.PROJECT : targetType;
        this.targetId = value(targetId, "");
        this.targetTitle = value(targetTitle, effectiveFallbackTitle(this.targetType));
        this.selectedReason = REASONS.getFirst();
        this.description = "";
        this.errorMessage = "";
        this.reportId = "";
        this.dropdownOpen = false;
        this.submitting = false;
        this.submitted = false;
        rebuildOverlay();
    }

    void hide() {
        if (overlay == null) {
            return;
        }
        Parent parent = overlay.getParent();
        if (parent instanceof StackPane stack) {
            stack.getChildren().remove(overlay);
        }
        overlay = null;
    }

    private void rebuildOverlay() {
        StackPane hostPane = host.get();
        if (hostPane == null) {
            toast.accept("Report unavailable", "The launcher window is not ready yet.");
            return;
        }
        if (overlay == null) {
            overlay = overlayShell();
            hostPane.getChildren().add(overlay);
        }
        overlay.getChildren().setAll(modal());
        Platform.runLater(overlay::requestFocus);
    }

    private StackPane overlayShell() {
        StackPane shell = new StackPane();
        shell.getStyleClass().add("report-modal-overlay");
        shell.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        shell.setFocusTraversable(true);
        shell.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                hide();
                event.consume();
            }
        });
        shell.setOnMouseClicked(event -> {
            if (event.getTarget() == shell) {
                hide();
            }
        });
        return shell;
    }

    private VBox modal() {
        VBox modal = new VBox(0);
        modal.getStyleClass().add("report-modal");
        modal.setMaxWidth(MODAL_WIDTH);
        modal.setPrefWidth(MODAL_WIDTH);
        modal.setMaxHeight(Region.USE_PREF_SIZE);
        modal.setOnMouseClicked(event -> event.consume());
        modal.getChildren().addAll(header(), body());
        return modal;
    }

    private HBox header() {
        HBox header = new HBox(16);
        header.getStyleClass().add("report-modal-header");
        header.setAlignment(Pos.CENTER_LEFT);

        HBox titleRow = new HBox(8, LauncherIcons.icon(LauncherIcons.Glyph.FLAG, 20),
                new Label("Report " + targetType.label));
        titleRow.getStyleClass().add("report-modal-title");
        titleRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleRow, Priority.ALWAYS);

        Button close = new Button(null, LauncherIcons.icon(LauncherIcons.Glyph.X, 16));
        close.getStyleClass().add("report-modal-close");
        close.setOnAction(event -> hide());
        header.getChildren().addAll(titleRow, close);
        return header;
    }

    private Node body() {
        VBox body = new VBox(0);
        body.getStyleClass().add("report-modal-body");
        if (submitted) {
            body.getChildren().add(submittedState());
        } else {
            body.getChildren().add(form());
        }
        return body;
    }

    private VBox submittedState() {
        VBox state = new VBox(0);
        state.getStyleClass().add("report-modal-submitted");
        state.setAlignment(Pos.CENTER);

        StackPane icon = new StackPane(LauncherIcons.icon(LauncherIcons.Glyph.CHECK, 32));
        icon.getStyleClass().add("report-modal-submitted-icon");

        Label title = new Label("Report Submitted");
        title.getStyleClass().add("report-modal-submitted-title");

        Label copy = new Label("Thank you for helping keep the community safe. A moderator will review this shortly.");
        copy.getStyleClass().add("report-modal-submitted-copy");
        copy.setWrapText(true);
        copy.setMaxWidth(420);
        copy.setAlignment(Pos.CENTER);

        state.getChildren().addAll(icon, title, copy);
        if (!reportId.isBlank()) {
            VBox report = new VBox(4);
            report.getStyleClass().add("report-modal-id-card");
            Label idLabel = new Label("Report ID");
            idLabel.getStyleClass().add("report-modal-id-label");
            Label id = new Label(reportId);
            id.getStyleClass().add("report-modal-id-value");
            report.getChildren().addAll(idLabel, id);
            VBox.setMargin(report, new Insets(24, 0, 0, 0));
            state.getChildren().add(report);
        }

        Button close = new Button("Close");
        close.getStyleClass().add("report-modal-secondary");
        close.setOnAction(event -> hide());
        VBox.setMargin(close, new Insets(32, 0, 0, 0));
        state.getChildren().add(close);
        return state;
    }

    private VBox form() {
        VBox form = new VBox(16);
        form.getChildren().add(targetNotice());
        if (!errorMessage.isBlank()) {
            form.getChildren().add(errorNotice());
        }
        form.getChildren().add(reasonField());
        form.getChildren().add(descriptionField());
        Button submit = submitButton();
        VBox.setMargin(submit, new Insets(22, 0, 0, 0));
        form.getChildren().add(submit);
        return form;
    }

    private HBox targetNotice() {
        HBox notice = new HBox();
        notice.getStyleClass().add("report-modal-target");
        Label label = new Label("Reporting ");
        label.getStyleClass().add("report-modal-target-copy");
        Label title = new Label(effectiveTitle());
        title.getStyleClass().add("report-modal-target-title");
        notice.getChildren().addAll(label, title);
        return notice;
    }

    private HBox errorNotice() {
        HBox notice = new HBox(8);
        notice.getStyleClass().add("report-modal-error");
        notice.setAlignment(Pos.CENTER_LEFT);
        Label message = new Label(errorMessage);
        message.getStyleClass().add("report-modal-error-copy");
        message.setWrapText(true);
        HBox.setHgrow(message, Priority.ALWAYS);
        notice.getChildren().addAll(LauncherIcons.icon(LauncherIcons.Glyph.ALERT_CIRCLE, 16), message);
        return notice;
    }

    private VBox reasonField() {
        VBox field = new VBox(8);
        field.getChildren().add(fieldLabel("Reason"));

        Button selector = new Button(null, reasonSelectorContent());
        selector.getStyleClass().add("report-modal-reason-button");
        selector.setMaxWidth(Double.MAX_VALUE);
        selector.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        selector.setAlignment(Pos.CENTER_LEFT);
        selector.setOnAction(event -> {
            dropdownOpen = !dropdownOpen;
            rebuildOverlay();
        });
        field.getChildren().add(selector);

        if (dropdownOpen) {
            VBox options = new VBox(0);
            options.getStyleClass().add("report-modal-reason-options");
            for (ReportReason reason : REASONS) {
                options.getChildren().add(reasonOption(reason));
            }
            field.getChildren().add(options);
        }
        return field;
    }

    private HBox reasonSelectorContent() {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        Label label = new Label(selectedReason.label());
        label.getStyleClass().add("report-modal-reason-selected-label");
        HBox.setHgrow(label, Priority.ALWAYS);
        row.getChildren().addAll(label, LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_DOWN, 16));
        return row;
    }

    private Button reasonOption(ReportReason reason) {
        HBox row = new HBox(8);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMaxWidth(Double.MAX_VALUE);
        Label label = new Label(reason.label());
        label.getStyleClass().add(reason.id().equals(selectedReason.id())
                ? "report-modal-reason-option-selected"
                : "report-modal-reason-option-copy");
        HBox.setHgrow(label, Priority.ALWAYS);
        row.getChildren().add(label);
        if (reason.id().equals(selectedReason.id())) {
            row.getChildren().add(LauncherIcons.icon(LauncherIcons.Glyph.CHECK, 16));
        }
        Button option = new Button(null, row);
        option.getStyleClass().add("report-modal-reason-option");
        option.setMaxWidth(Double.MAX_VALUE);
        option.setOnAction(event -> {
            selectedReason = reason;
            dropdownOpen = false;
            rebuildOverlay();
        });
        return option;
    }

    private VBox descriptionField() {
        VBox field = new VBox(8);
        field.getChildren().add(fieldLabel("Description"));
        TextArea area = new TextArea(description);
        area.getStyleClass().add("report-modal-description");
        area.setPromptText("Please provide details about the issue...");
        area.setWrapText(true);
        area.setPrefRowCount(4);
        area.textProperty().addListener((observable, previous, next) -> description = value(next, ""));
        field.getChildren().add(area);
        return field;
    }

    private Label fieldLabel(String text) {
        Label label = new Label(value(text, "").toUpperCase(Locale.ROOT));
        label.getStyleClass().add("report-modal-field-label");
        return label;
    }

    private Button submitButton() {
        Button submit = new Button(submitting ? "Submitting..." : "Submit Report");
        submit.getStyleClass().add("report-modal-submit");
        submit.setMaxWidth(Double.MAX_VALUE);
        submit.setDisable(submitting);
        submit.setOnAction(event -> submitReport());
        return submit;
    }

    private void submitReport() {
        if (targetId.isBlank()) {
            errorMessage = "We could not determine what you were trying to report. Please close this window and try again.";
            rebuildOverlay();
            return;
        }
        if (description.isBlank()) {
            errorMessage = "Please provide details about the issue.";
            rebuildOverlay();
            return;
        }
        if (apiClient == null || executor == null) {
            errorMessage = "The launcher is not ready to submit reports yet.";
            rebuildOverlay();
            return;
        }
        submitting = true;
        errorMessage = "";
        rebuildOverlay();

        StackPane expectedOverlay = overlay;
        String selectedTargetId = targetId;
        String selectedTargetType = targetType.apiValue;
        String selectedReasonId = selectedReason.id();
        String selectedDescription = description;
        CompletableFuture.supplyAsync(
                () -> apiClient.submitReport(selectedTargetId, selectedTargetType, selectedReasonId, selectedDescription),
                executor
        ).whenComplete((id, error) -> Platform.runLater(() -> {
            if (overlay == null || overlay != expectedOverlay) {
                return;
            }
            submitting = false;
            if (error != null) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                errorMessage = value(cause.getMessage(), "We could not submit your report.");
                rebuildOverlay();
                return;
            }
            reportId = value(id, "");
            submitted = true;
            rebuildOverlay();
        }));
    }

    private String effectiveTitle() {
        return value(targetTitle, effectiveFallbackTitle(targetType));
    }

    private static String effectiveFallbackTitle(TargetType targetType) {
        return targetType == null ? "Content" : targetType.fallbackTitle;
    }

    private record ReportReason(String id, String label) {
    }
}
