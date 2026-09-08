package net.modtale.launcher.ui.library;

import javafx.geometry.Rectangle2D;
import javafx.scene.image.ImageView;

final class LibraryWorldIcon {
    private LibraryWorldIcon() {
    }

    static void cropToSquare(ImageView view) {
        view.setPreserveRatio(false);
        view.imageProperty().addListener((observable, previous, image) -> {
            if (image == null || image.getWidth() <= 0 || image.getHeight() <= 0) {
                view.setViewport(null);
                return;
            }
            double side = Math.min(image.getWidth(), image.getHeight());
            view.setViewport(new Rectangle2D(
                    (image.getWidth() - side) / 2,
                    (image.getHeight() - side) / 2,
                    side, side));
        });
    }
}
