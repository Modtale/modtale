package net.modtale.launcher.ui.wardrobe;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import net.modtale.launcher.wardrobe.CosmeticCatalogClient;
import net.modtale.launcher.wardrobe.LocalAvatarRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

class CosmeticFramingTest {
    @Test void nonHeadCategoriesKeepTheirExistingFraming() {
        for (String category : List.of("pants", "shoes", "overtop", "cape")) {
            var framing = CosmeticFraming.forCategory(category);
            assertSame(framing, framing.fit(new Group()));
        }
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "WARDROBE_ASSETS_ZIP", matches = ".+")
    void tallHairAndWideOrTallHatsStayInsideTheFrame() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        try { Platform.startup(() -> { Platform.setImplicitExit(false); started.countDown(); }); }
        catch (IllegalStateException alreadyStarted) { started.countDown(); }
        assertTrue(started.await(10, TimeUnit.SECONDS));
        Path assets = Path.of(System.getenv("WARDROBE_ASSETS_ZIP"));
        var catalog = new CosmeticCatalogClient(assets);
        for (String[] sample : List.of(new String[]{"headAccessory", "WitchHat"}, new String[]{"headAccessory", "StrawHat"},
                new String[]{"haircut", "Mohawk"}, new String[]{"haircut", "Quiff"})) {
            var option = catalog.browseAssets(sample[0], "", 1, 100).options().stream()
                    .filter(candidate -> candidate.assetId().equals(sample[1])).findFirst().orElseThrow();
            var skin = catalog.defaultSkin(); skin.put(sample[0], option.id());
            var model = LocalAvatarRenderer.load(assets, skin, catalog);
            var fitted = CosmeticFraming.forCategory(sample[0]).fit(model);
            var bounds = LocalAvatarRenderer.bounds(model);
            double radius = Math.sqrt(bounds.getWidth() * bounds.getWidth() + bounds.getHeight() * bounds.getHeight()
                    + bounds.getDepth() * bounds.getDepth()) / 2;
            for (double yaw : new double[]{-20, 0, 90, 180})
                verifyHead(model, bounds, radius, fitted, yaw, sample[1]);
        }
    }

    private static void verifyHead(Node node, javafx.geometry.Bounds bounds, double radius,
            CosmeticFraming fitted, double yaw, String item) {
        if (!node.isVisible()) return;
        if (node instanceof MeshView view && view.getMesh() instanceof TriangleMesh mesh) {
            String id = java.util.Objects.toString(node.getId(), "");
            String category = id.split(":", 2)[0];
            if (CosmeticFraming.forCategory(category).group().equals("Head")
                    || category.equals("bodyCharacteristic") && id.toLowerCase().contains("head")) {
                double distance = 1.1 / Math.sin(Math.toRadians(17.5)) * fitted.scale();
                var points = mesh.getPoints();
                for (int i = 0; i < points.size(); i += 3) {
                    var point = node.localToScene(points.get(i), points.get(i + 1), points.get(i + 2));
                    double x = (point.getX() - bounds.getCenterX()) / radius;
                    double y = -(point.getY() - bounds.getCenterY()) / radius - fitted.centerY();
                    double z = -(point.getZ() - bounds.getCenterZ()) / radius;
                    double angle = Math.toRadians(yaw);
                    double rotatedX = Math.cos(angle) * x + Math.sin(angle) * z;
                    double depth = -Math.sin(angle) * x + Math.cos(angle) * z + distance;
                    double limit = depth * Math.tan(Math.toRadians(17.5)) * .96;
                    assertTrue(Math.abs(rotatedX) < limit && Math.abs(y) < limit,
                            item + " clips " + id + " at yaw " + yaw);
                }
            }
        }
        if (node instanceof Parent parent)
            parent.getChildrenUnmodifiable().forEach(child -> verifyHead(child, bounds, radius, fitted, yaw, item));
    }
}
