package net.modtale.launcher.ui.play;

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import javafx.beans.binding.Bindings;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/** Tries the username render once if an archived avatar cannot be decoded or downloaded. */
final class HytaleProfileAvatarImages {
    private HytaleProfileAvatarImages() { }

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
