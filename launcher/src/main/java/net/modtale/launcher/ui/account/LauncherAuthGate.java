package net.modtale.launcher.ui.account;

import static net.modtale.launcher.ui.common.LauncherUi.primaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.secondaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.setVisibleManaged;
import static net.modtale.launcher.ui.common.LauncherUi.styleInput;

import java.util.List;
import java.util.Objects;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import net.modtale.launcher.ui.common.LauncherIcons;

public final class LauncherAuthGate {

    private static final List<OAuthProvider> OAUTH_PROVIDERS = List.of(
            new OAuthProvider("GitHub", "github", LauncherIcons.BrandGlyph.GITHUB),
            new OAuthProvider("Discord", "discord", LauncherIcons.BrandGlyph.DISCORD),
            new OAuthProvider("Google", "google", LauncherIcons.BrandGlyph.GOOGLE)
    );

    private final LauncherAccountController accountController;

    private Node view;
    private Label statusLabel;
    private Button signInButton;
    private Button browserSignInButton;
    private TextField usernameField;
    private PasswordField passwordField;
    private TextField mfaCodeField;
    private VBox credentialsPane;
    private VBox mfaPane;
    private String preAuthToken;

    public LauncherAuthGate(LauncherAccountController accountController) {
        this.accountController = accountController;
    }

    public Node view() {
        if (view == null) {
            view = buildView();
        }
        return view;
    }

    public void show(String status, boolean allowSignIn) {
        if (statusLabel != null) {
            statusLabel.setText(status == null || status.isBlank() ? "Sign in with Modtale to continue." : status);
        }
        if (credentialsPane != null) {
            credentialsPane.setDisable(!allowSignIn);
        }
        if (mfaPane != null) {
            mfaPane.setDisable(!allowSignIn);
        }
        if (signInButton != null) {
            signInButton.setDisable(!allowSignIn);
        }
        if (browserSignInButton != null) {
            browserSignInButton.setDisable(!allowSignIn);
        }
        if (usernameField != null) {
            usernameField.setDisable(!allowSignIn);
        }
        if (passwordField != null) {
            passwordField.setDisable(!allowSignIn);
        }
        if (mfaCodeField != null) {
            mfaCodeField.setDisable(!allowSignIn);
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

        Label title = new Label("Sign in with Modtale");
        title.getStyleClass().add("auth-title");

        statusLabel = new Label("Checking for an existing Modtale session...");
        statusLabel.getStyleClass().add("auth-status");
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(360);
        statusLabel.setAlignment(Pos.CENTER);
        statusLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);

        credentialsPane = credentialsPane();
        mfaPane = mfaPane();
        setVisibleManaged(mfaPane, false);

        VBox card = new VBox(16, logo, title, credentialsPane, mfaPane, statusLabel);
        card.getStyleClass().add("auth-card");
        card.setAlignment(Pos.CENTER);
        gate.getChildren().add(card);
        return gate;
    }

    private VBox credentialsPane() {
        usernameField = new TextField();
        usernameField.setPromptText("Email or username");

        passwordField = new PasswordField();
        passwordField.setPromptText("Password");
        styleInput(usernameField, passwordField);
        usernameField.setMaxWidth(Double.MAX_VALUE);
        passwordField.setMaxWidth(Double.MAX_VALUE);
        usernameField.setOnAction(event -> passwordField.requestFocus());
        passwordField.setOnAction(event -> submitCredentials());

        signInButton = primaryButton("Sign In");
        signInButton.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.USER, 16));
        signInButton.setMaxWidth(Double.MAX_VALUE);
        signInButton.setOnAction(event -> submitCredentials());

        browserSignInButton = secondaryButton("Use browser sign-in");
        browserSignInButton.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.GLOBE, 16));
        browserSignInButton.setMaxWidth(Double.MAX_VALUE);
        browserSignInButton.setOnAction(event -> {
            show("Opening Modtale sign-in in your browser...", false);
            accountController.signInWithBrowser();
        });

        VBox fields = new VBox(10, usernameField, passwordField);
        fields.setMaxWidth(Double.MAX_VALUE);

        VBox pane = new VBox(12, oauthPane(), separator(), fields, signInButton, browserSignInButton);
        pane.getStyleClass().add("auth-form");
        pane.setAlignment(Pos.CENTER);
        pane.setMaxWidth(360);
        return pane;
    }

    private Node oauthPane() {
        VBox providers = new VBox(10);
        providers.getStyleClass().add("auth-oauth-list");
        for (int index = 0; index < OAUTH_PROVIDERS.size(); index += 2) {
            HBox row = new HBox(10);
            row.getStyleClass().add("auth-oauth-row");
            row.getChildren().add(oauthButton(OAUTH_PROVIDERS.get(index)));
            if (index + 1 < OAUTH_PROVIDERS.size()) {
                row.getChildren().add(oauthButton(OAUTH_PROVIDERS.get(index + 1)));
            }
            providers.getChildren().add(row);
        }
        return providers;
    }

    private Button oauthButton(OAuthProvider provider) {
        Button button = secondaryButton(provider.label());
        button.getStyleClass().add("auth-oauth-button");
        button.setGraphic(LauncherIcons.brandIcon(provider.icon(), 16));
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> submitOAuth(provider));
        HBox.setHgrow(button, Priority.ALWAYS);
        return button;
    }

    private Node separator() {
        Label label = new Label("or use email");
        label.getStyleClass().add("auth-separator-label");
        label.getStyleClass().add("auth-separator");
        label.setAlignment(Pos.CENTER);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private VBox mfaPane() {
        Label title = new Label("Enter your two-factor code");
        title.getStyleClass().add("auth-step-title");

        mfaCodeField = new TextField();
        mfaCodeField.setPromptText("000000");
        mfaCodeField.getStyleClass().addAll("input", "auth-mfa-input");
        mfaCodeField.setMaxWidth(Double.MAX_VALUE);
        mfaCodeField.textProperty().addListener((observable, previous, current) -> {
            String digits = current == null ? "" : current.replaceAll("\\D", "");
            if (digits.length() > 6) {
                digits = digits.substring(0, 6);
            }
            if (!digits.equals(current)) {
                mfaCodeField.setText(digits);
            }
        });
        mfaCodeField.setOnAction(event -> submitMfa());

        Button verify = primaryButton("Verify");
        verify.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.CHECK, 16));
        verify.setMaxWidth(Double.MAX_VALUE);
        verify.setOnAction(event -> submitMfa());

        Button back = secondaryButton("Back");
        back.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.CHEVRON_LEFT, 16));
        back.setMaxWidth(Double.MAX_VALUE);
        back.setOnAction(event -> showCredentials("Sign in with Modtale to continue.", true));

        HBox actions = new HBox(10, back, verify);
        actions.setAlignment(Pos.CENTER);
        HBox.setHgrow(back, Priority.ALWAYS);
        HBox.setHgrow(verify, Priority.ALWAYS);

        VBox pane = new VBox(12, title, mfaCodeField, actions);
        pane.getStyleClass().add("auth-form");
        pane.setAlignment(Pos.CENTER);
        pane.setMaxWidth(360);
        VBox.setMargin(title, new Insets(0, 0, 2, 0));
        return pane;
    }

    private void submitCredentials() {
        String username = usernameField == null ? "" : usernameField.getText().trim();
        String password = passwordField == null ? "" : passwordField.getText();
        if (username.isBlank()) {
            show("Enter your email or username.", true);
            usernameField.requestFocus();
            return;
        }
        if (password.isBlank()) {
            show("Enter your password.", true);
            passwordField.requestFocus();
            return;
        }

        show("Signing in with Modtale...", false);
        accountController.signIn(username, password.toCharArray(), result -> {
            if (result.mfaRequired()) {
                preAuthToken = result.preAuthToken();
                if (passwordField != null) {
                    passwordField.clear();
                }
                showMfa();
                return;
            }
            if (result.failed()) {
                showCredentials(result.errorMessage(), true);
            } else if (result.success() && passwordField != null) {
                passwordField.clear();
            }
        });
    }

    private void submitOAuth(OAuthProvider provider) {
        show("Opening " + provider.label() + " sign-in...", false);
        accountController.signInWithOAuthProvider(provider.id(), provider.label(), result -> {
            if (result.failed()) {
                showCredentials(result.errorMessage(), true);
            }
        });
    }

    private void submitMfa() {
        String code = mfaCodeField == null ? "" : mfaCodeField.getText().trim();
        if (preAuthToken == null || preAuthToken.isBlank()) {
            showCredentials("Your two-factor login session expired. Sign in again.", true);
            return;
        }
        if (!code.matches("\\d{6}")) {
            show("Enter the 6-digit code from your authenticator app.", true);
            mfaCodeField.requestFocus();
            return;
        }

        show("Verifying two-factor code...", false);
        accountController.validateMfa(preAuthToken, code, result -> {
            if (result.failed()) {
                show(result.errorMessage(), true);
                if (mfaCodeField != null) {
                    mfaCodeField.selectAll();
                    mfaCodeField.requestFocus();
                }
            } else {
                preAuthToken = null;
                if (mfaCodeField != null) {
                    mfaCodeField.clear();
                }
            }
        });
    }

    private void showMfa() {
        setVisibleManaged(credentialsPane, false);
        setVisibleManaged(mfaPane, true);
        if (mfaCodeField != null) {
            mfaCodeField.clear();
            mfaCodeField.requestFocus();
        }
        show("Enter the 6-digit code from your authenticator app.", true);
    }

    private void showCredentials(String status, boolean allowSignIn) {
        preAuthToken = null;
        setVisibleManaged(mfaPane, false);
        setVisibleManaged(credentialsPane, true);
        show(status, allowSignIn);
        if (usernameField != null) {
            usernameField.requestFocus();
        }
    }

    private record OAuthProvider(String label, String id, LauncherIcons.BrandGlyph icon) {
    }
}
