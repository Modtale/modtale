package net.modtale.launcher.wardrobe;

import javafx.scene.Group;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalAvatarThumbnailTest {
    @Test void depthAndTransparentTexelsPreserveUnderlyingOutfitRegardlessOfDrawOrder() throws Exception {
        Image first = LocalAvatarThumbnail.render(new Group(plane(0, false), plane(1, true)), 128, 0, 0, 1);
        Image second = LocalAvatarThumbnail.render(new Group(plane(1, true), plane(0, false)), 128, 0, 0, 1);
        int red = 0, green = 0, transparent = 0;
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            int value = first.getPixelReader().getArgb(x, y);
            assertEquals(value, second.getPixelReader().getArgb(x, y));
            if (value == 0xffff0000) red++;
            if (value == 0xff00ff00) green++;
            if (value == 0) transparent++;
        }
        assertTrue(red > 100); assertTrue(green > 100); assertTrue(transparent > 100);
    }

    @Test void skeletonKeepsModelCutoutsButReplacesTextureWithTranslucentGrey() throws Exception {
        Group model = new Group(plane(0, true));
        Image original = LocalAvatarThumbnail.render(model, 128, 0, 0, 1);
        Image skeleton = LocalAvatarThumbnail.renderSkeleton(model, 128, 0, 0, 1);
        int visible = 0;
        for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
            int pixel = skeleton.getPixelReader().getArgb(x, y);
            assertEquals(original.getPixelReader().getArgb(x, y) != 0, pixel != 0);
            if (pixel != 0) {
                visible++;
                assertEquals(120, pixel >>> 24);
                assertEquals(pixel & 255, (pixel >> 8) & 255);
                assertEquals(pixel & 255, (pixel >> 16) & 255);
            }
        }
        assertTrue(visible > 100);
    }

    @Test void framingAndLimitsAreExplicit() throws Exception {
        Group model = new Group(plane(0, false));
        assertThrows(IllegalArgumentException.class, () -> LocalAvatarThumbnail.render(model, 4096, 0, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> LocalAvatarThumbnail.render(model, 128, Double.NaN, 0, 1));
        assertThrows(java.io.IOException.class, () -> LocalAvatarThumbnail.render(new Group(), 128, 0, 0, 1));
        Image centered = LocalAvatarThumbnail.render(model, 128, 0, 0, 1);
        Image shifted = LocalAvatarThumbnail.render(model, 128, 0, .5, 1);
        boolean changed = false;
        for (int y = 0; y < 128; y++) if (centered.getPixelReader().getArgb(64, y) != shifted.getPixelReader().getArgb(64, y)) changed = true;
        assertTrue(changed);
    }

    private static MeshView plane(float z, boolean cutout) {
        TriangleMesh mesh = new TriangleMesh();
        mesh.getPoints().setAll(-1, -1, z, 1, -1, z, 1, 1, z, -1, 1, z);
        mesh.getTexCoords().setAll(0, 0, 1, 0, 1, 1, 0, 1);
        mesh.getFaces().setAll(0, 0, 1, 1, 2, 2, 0, 0, 2, 2, 3, 3);
        WritableImage texture = new WritableImage(2, 2);
        for (int y = 0; y < 2; y++) for (int x = 0; x < 2; x++)
            texture.getPixelWriter().setArgb(x, y, cutout ? (x == 0 ? 0 : 0xffff0000) : 0xff00ff00);
        PhongMaterial material = new PhongMaterial(); material.setDiffuseMap(texture);
        MeshView view = new MeshView(mesh); view.setMaterial(material); return view;
    }
}
