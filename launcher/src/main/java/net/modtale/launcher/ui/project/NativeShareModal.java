package net.modtale.launcher.ui.project;

import static net.modtale.launcher.ui.common.LauncherUi.value;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;
import net.modtale.launcher.ui.common.LauncherIcons;

final class NativeShareModal {

    private static final double MODAL_WIDTH = 448;

    private final Supplier<StackPane> host;
    private final Consumer<String> error;

    private StackPane overlay;
    private String url;
    private String title;
    private String author;
    private boolean copied;

    NativeShareModal(Supplier<StackPane> host, Consumer<String> error) {
        this.host = host == null ? () -> null : host;
        this.error = error == null ? ignored -> {
        } : error;
    }

    void show(String url, String title, String author) {
        this.url = value(url, "");
        this.title = value(title, "Project");
        this.author = value(author, "Unknown");
        this.copied = false;
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
            error.accept("The launcher window is not ready yet.");
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
        shell.getStyleClass().add("share-modal-overlay");
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
        modal.getStyleClass().add("share-modal");
        modal.setMaxWidth(MODAL_WIDTH);
        modal.setPrefWidth(MODAL_WIDTH);
        modal.setMaxHeight(Region.USE_PREF_SIZE);
        modal.setOnMouseClicked(event -> event.consume());
        modal.getChildren().addAll(header(), body());
        return modal;
    }

    private HBox header() {
        HBox header = new HBox(16);
        header.getStyleClass().add("share-modal-header");
        header.setAlignment(Pos.CENTER_LEFT);

        HBox titleRow = new HBox(8, LauncherIcons.icon(LauncherIcons.Glyph.SHARE_2, 20), new Label("Share Project"));
        titleRow.getStyleClass().add("share-modal-title");
        titleRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(titleRow, Priority.ALWAYS);

        Button close = new Button(null, LauncherIcons.icon(LauncherIcons.Glyph.X, 16));
        close.getStyleClass().add("share-modal-close");
        close.setOnAction(event -> hide());
        header.getChildren().addAll(titleRow, close);
        return header;
    }

    private VBox body() {
        VBox body = new VBox(24);
        body.getStyleClass().add("share-modal-body");
        body.getChildren().addAll(directLinkBlock(), shareViaBlock());
        return body;
    }

    private VBox directLinkBlock() {
        VBox block = new VBox(8);
        Label label = fieldLabel("Direct Link");

        HBox shell = new HBox(8);
        shell.getStyleClass().add("share-modal-link-shell");
        shell.setAlignment(Pos.CENTER_LEFT);

        HBox linkBox = new HBox(8);
        linkBox.getStyleClass().add("share-modal-link-box");
        linkBox.setAlignment(Pos.CENTER_LEFT);
        linkBox.setMinWidth(0);
        HBox.setHgrow(linkBox, Priority.ALWAYS);
        Label link = new Label(url);
        link.getStyleClass().add("share-modal-link-text");
        link.setTextOverrun(OverrunStyle.ELLIPSIS);
        link.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(link, Priority.ALWAYS);
        linkBox.getChildren().addAll(LauncherIcons.icon(LauncherIcons.Glyph.LINK, 16), link);

        Button copy = new Button(copied ? "Copied!" : "Copy",
                LauncherIcons.icon(copied ? LauncherIcons.Glyph.CHECK : LauncherIcons.Glyph.COPY, 16));
        copy.getStyleClass().add("share-modal-copy");
        copy.setOnAction(event -> copyLink());

        shell.getChildren().addAll(linkBox, copy);
        block.getChildren().addAll(label, shell);
        return block;
    }

    private VBox shareViaBlock() {
        VBox block = new VBox(8);
        Label label = fieldLabel("Share via");
        GridPane grid = new GridPane();
        grid.getStyleClass().add("share-modal-social-grid");
        grid.setHgap(12);
        Button twitter = socialButton("Twitter", LauncherIcons.brandIcon(LauncherIcons.BrandGlyph.TWITTER, 16), twitterUrl(), "twitter");
        Button facebook = socialButton("Facebook", LauncherIcons.brandIcon(LauncherIcons.BrandGlyph.FACEBOOK, 16), facebookUrl(), "facebook");
        grid.add(twitter, 0, 0);
        grid.add(facebook, 1, 0);
        GridPane.setHgrow(twitter, Priority.ALWAYS);
        GridPane.setHgrow(facebook, Priority.ALWAYS);
        block.getChildren().addAll(label, grid);
        return block;
    }

    private Label fieldLabel(String text) {
        Label label = new Label(value(text, "").toUpperCase(Locale.ROOT));
        label.getStyleClass().add("share-modal-field-label");
        return label;
    }

    private Button socialButton(String text, Node icon, String target, String style) {
        Button button = new Button(text, icon);
        button.getStyleClass().addAll("share-modal-social", style);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(event -> openUrl(target));
        return button;
    }

    private void copyLink() {
        ClipboardContent content = new ClipboardContent();
        content.putString(url);
        Clipboard.getSystemClipboard().setContent(content);
        copied = true;
        rebuildOverlay();

        PauseTransition reset = new PauseTransition(Duration.seconds(2));
        reset.setOnFinished(event -> {
            copied = false;
            if (overlay != null) {
                rebuildOverlay();
            }
        });
        reset.play();
    }

    private String twitterUrl() {
        String text = "Check out " + title + " by " + author + " on Modtale!";
        return "https://twitter.com/intent/tweet?text=" + encodeQuery(text) + "&url=" + encodeQuery(url);
    }

    private String facebookUrl() {
        return "https://www.facebook.com/sharer/sharer.php?u=" + encodeQuery(url);
    }

    private static String encodeQuery(String value) {
        return URLEncoder.encode(value(value, ""), StandardCharsets.UTF_8).replace("+", "%20");
    }

    private void openUrl(String rawUrl) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI.create(rawUrl));
                return;
            }
        } catch (IOException | IllegalArgumentException | SecurityException | UnsupportedOperationException ex) {
            error.accept(value(ex.getMessage(), "Could not open your browser."));
            return;
        }
        error.accept("Desktop browser integration is not available.");
    }

}
