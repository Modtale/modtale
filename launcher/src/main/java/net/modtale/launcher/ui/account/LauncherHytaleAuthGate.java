package net.modtale.launcher.ui.account;

import static net.modtale.launcher.ui.common.LauncherUi.primaryButton;

import java.util.Objects;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import net.modtale.launcher.hytale.HytaleAuthService;
import net.modtale.launcher.ui.common.LauncherIcons;
import net.modtale.launcher.ui.feedback.LauncherFeedback;
import net.modtale.launcher.ui.settings.LauncherSettingsController;

public final class LauncherHytaleAuthGate {

    private final HytaleAuthService hytaleAuthService;
    private final LauncherSettingsController settingsController;
    private final LauncherFeedback feedback;
    private final Runnable onLinked;

    private Node view;
    private Label statusLabel;
    private Button signInButton;

    public LauncherHytaleAuthGate(
            HytaleAuthService hytaleAuthService,
            LauncherSettingsController settingsController,
            LauncherFeedback feedback,
            Runnable onLinked
    ) {
        this.hytaleAuthService = hytaleAuthService;
        this.settingsController = settingsController;
        this.feedback = feedback;
        this.onLinked = onLinked == null ? () -> {
        } : onLinked;
    }

    public Node view() {
        if (view == null) {
            view = buildView();
        }
        return view;
    }

    public void show(String status, boolean allowSignIn) {
        if (statusLabel != null) {
            statusLabel.setText(status == null || status.isBlank()
                    ? "Sign in with Hytale to use Modtale Launcher."
                    : status);
        }
        if (signInButton != null) {
            signInButton.setDisable(!allowSignIn);
        }
    }

    private Node buildView() {
        VBox gate = new VBox(18);
        gate.getStyleClass().add("auth-gate");
        gate.setAlignment(Pos.CENTER);

        ImageView logo = new ImageView(new Image(Objects.requireNonNull(getClass()
                .getResource("/net/modtale/launcher/ui/nativefx/assets/logo_light.png")).toExternalForm(), true));
        logo.setFitHeight(42);
        logo.setPreserveRatio(true);

        Label title = new Label("Link a Hytale account");
        title.getStyleClass().add("auth-title");

        statusLabel = new Label("Sign in with Hytale to use Modtale Launcher.");
        statusLabel.getStyleClass().add("auth-status");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(360);
        statusLabel.setAlignment(Pos.CENTER);
        statusLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        signInButton = primaryButton("Sign In With Hytale");
        signInButton.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.USER, 16));
        signInButton.setMaxWidth(Double.MAX_VALUE);
        signInButton.setOnAction(event -> signInHytale());

        Label note = new Label("Modtale sign-in is optional for local play, but Hytale launch uses official Hytale authentication.");
        note.getStyleClass().add("auth-status");
        note.setWrapText(true);
        note.setMaxWidth(360);
        note.setAlignment(Pos.CENTER);
        note.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        VBox card = new VBox(16, logo, title, statusLabel, signInButton, note);
        card.getStyleClass().add("auth-card");
        card.setAlignment(Pos.CENTER);
        gate.getChildren().add(card);
        return gate;
    }

    private void signInHytale() {
        settingsController.saveFromFields(false);
        show("Opening Hytale sign-in in your browser...", false);
        feedback.runAsync("Opening Hytale sign-in in your browser...", () ->
                hytaleAuthService.loginAndSave(settingsController.settings()), session -> {
            settingsController.reloadFromStore();
            feedback.log("Signed in with Hytale as " + session + ".");
            feedback.showToast("Hytale ready", "Signed in as " + session + ".");
            show("Signed in with Hytale.", true);
            onLinked.run();
        }, error -> show("Hytale sign-in failed. Try again when Hytale authentication is available.", true));
    }
}
