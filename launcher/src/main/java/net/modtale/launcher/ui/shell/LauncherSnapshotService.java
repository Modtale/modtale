package net.modtale.launcher.ui.shell;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;
import javax.imageio.ImageIO;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class LauncherSnapshotService {

    private static final Logger LOG = LogManager.getLogger(LauncherSnapshotService.class);

    private LauncherSnapshotService() {
    }

    public static void scheduleIfRequested(String outputPath, Node root) {
        if (outputPath == null || outputPath.isBlank()) {
            return;
        }
        PauseTransition delay = new PauseTransition(Duration.seconds(2.5));
        delay.setOnFinished(event -> {
            try {
                WritableImage image = root.snapshot(new SnapshotParameters(), null);
                ImageIO.write(toBufferedImage(image), "png", Path.of(outputPath).toFile());
            } catch (IOException ex) {
                LOG.warn("Could not write launcher snapshot to {}", outputPath, ex);
            } finally {
                Platform.exit();
            }
        });
        delay.play();
    }

    private static BufferedImage toBufferedImage(WritableImage image) {
        int width = (int) image.getWidth();
        int height = (int) image.getHeight();
        BufferedImage bufferedImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                bufferedImage.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        return bufferedImage;
    }
}
