package net.modtale.launcher.ui.play;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/** Tries the username render once if an archived avatar cannot be decoded or downloaded. */
final class HytaleProfileAvatarImages {
    private HytaleProfileAvatarImages() { }

    static void fitVisibleContent(ImageView view) {
        Runnable fit = () -> {
            Image image = view.getImage();
            view.setViewport(null);
            if (image == null || image.getPixelReader() == null) return;
            var pixels = image.getPixelReader();
            int width = (int) image.getWidth(), height = (int) image.getHeight();
            int left = width, top = height, right = -1, bottom = -1;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if ((pixels.getArgb(x, y) >>> 24) < 16) continue;
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
            if (right >= left && bottom >= top) {
                view.setViewport(new Rectangle2D(left, top, right - left + 1, bottom - top + 1));
            }
        };
        javafx.beans.value.ChangeListener<Number> loaded = (observable, previous, progress) -> {
            if (progress.doubleValue() == 1) fit.run();
        };
        view.imageProperty().addListener((observable, previous, image) -> {
            if (previous != null) previous.progressProperty().removeListener(loaded);
            if (image != null) image.progressProperty().addListener(loaded);
            fit.run();
        });
        if (view.getImage() != null) view.getImage().progressProperty().addListener(loaded);
        fit.run();
    }

    static void render(ImageView view, Node initial, CompletableFuture<JsonNode> skin,
                       Function<JsonNode, CompletableFuture<Image>> renderer, BooleanSupplier current) {
        skin.thenAccept(definition -> Platform.runLater(() -> {
            if (!current.getAsBoolean()) return;
            renderer.apply(definition).thenAccept(rendered -> Platform.runLater(() -> {
                if (!current.getAsBoolean()) return;
                view.setUserData("local-outfit");
                view.setImage(rendered);
                initial.visibleProperty().unbind();
                initial.setVisible(false);
            }));
        }));
    }

    static void load(ImageView view, Node initial, String url, String fallbackUrl,
                     Function<String, Image> images, BooleanSupplier current) {
        if (!current.getAsBoolean()) return;
        Image image = images.apply(url);
        view.setImage(image);
        initial.visibleProperty().unbind();
        initial.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> image.getProgress() < 1 || image.isError(),
                image.progressProperty(), image.errorProperty()));
        if (url.equals(fallbackUrl)) return;
        Runnable fallback = () -> {
            if (current.getAsBoolean() && view.getImage() == image && image.isError()) {
                load(view, initial, fallbackUrl, fallbackUrl, images, current);
            }
        };
        image.errorProperty().addListener((observable, previous, failed) -> {
            if (failed) fallback.run();
        });
        fallback.run();
    }
}
