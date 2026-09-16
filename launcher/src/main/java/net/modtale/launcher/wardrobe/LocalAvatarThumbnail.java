package net.modtale.launcher.wardrobe;

import java.io.IOException;
import java.util.Arrays;
import javafx.geometry.Bounds;
import javafx.geometry.Point3D;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.PhongMaterial;
import javafx.scene.shape.MeshView;
import javafx.scene.shape.TriangleMesh;

/** Bounded CPU rasterizer for detached avatar meshes; works without a JavaFX 3D pipeline. */
public final class LocalAvatarThumbnail {
    private LocalAvatarThumbnail() {}

    public static Image render(Group model, int size, double yaw, double centerY, double scale) throws IOException {
        if (size < 32 || size > 1024 || !Double.isFinite(yaw) || !Double.isFinite(centerY) || !Double.isFinite(scale) || scale <= 0)
            throw new IllegalArgumentException("Invalid thumbnail framing");
        Bounds bounds = LocalAvatarRenderer.bounds(model);
        double radius = Math.sqrt(bounds.getWidth() * bounds.getWidth() + bounds.getHeight() * bounds.getHeight() + bounds.getDepth() * bounds.getDepth()) / 2;
        if (!Double.isFinite(radius) || radius < 1e-8) throw new IOException("Empty avatar bounds");
        var raster = new Raster(size, bounds, radius, yaw, centerY, scale);
        raster.visit(model);
        WritableImage image = new WritableImage(size, size);
        image.getPixelWriter().setPixels(0, 0, size, size, PixelFormat.getIntArgbInstance(), raster.pixels, 0, size);
        return image;
    }

    private static final class Raster {
        final int size;
        final int[] pixels;
        final double[] depth;
        final Bounds bounds;
        final double radius, sin, cos, centerY, distance, focal;
        int triangles;
        Raster(int size, Bounds bounds, double radius, double yaw, double centerY, double scale) {
            this.size = size; this.bounds = bounds; this.radius = radius; this.centerY = centerY;
            sin = Math.sin(Math.toRadians(yaw)); cos = Math.cos(Math.toRadians(yaw));
            distance = 1.1 / Math.sin(Math.toRadians(17.5)) * scale;
            focal = size / (2 * Math.tan(Math.toRadians(17.5)));
            pixels = new int[size * size]; depth = new double[pixels.length]; Arrays.fill(depth, Double.POSITIVE_INFINITY);
        }

        void visit(Node node) throws IOException {
            if (Thread.currentThread().isInterrupted()) throw new IOException("Thumbnail cancelled");
            if (!node.isVisible()) return;
            if (node instanceof MeshView view && view.getMesh() instanceof TriangleMesh mesh) draw(view, mesh);
            if (node instanceof Parent parent) for (Node child : parent.getChildrenUnmodifiable()) visit(child);
        }

        void draw(MeshView view, TriangleMesh mesh) throws IOException {
            var material = view.getMaterial() instanceof PhongMaterial value ? value : null;
            Image texture = material == null ? null : material.getDiffuseMap();
            var reader = texture == null ? null : texture.getPixelReader();
            float[] points = mesh.getPoints().toArray(null), uv = mesh.getTexCoords().toArray(null);
            int[] faces = mesh.getFaces().toArray(null);
            int stride = mesh.getVertexFormat().getVertexIndexSize();
            int uvOffset = mesh.getVertexFormat().getTexCoordIndexOffset();
            double[][] projected = new double[points.length / 3][];
            for (int i = 0; i < projected.length; i++) {
                Point3D point = view.localToScene(points[i * 3], points[i * 3 + 1], points[i * 3 + 2]);
                double x = (point.getX() - bounds.getCenterX()) / radius;
                double y = -(point.getY() - bounds.getCenterY()) / radius;
                double z = -(point.getZ() - bounds.getCenterZ()) / radius;
                double rotatedX = cos * x + sin * z, rotatedZ = -sin * x + cos * z;
                double depth = rotatedZ + distance;
                projected[i] = new double[]{size / 2.0 + rotatedX * focal / depth, size / 2.0 + (y - centerY) * focal / depth, depth};
            }
            for (int f = 0; f < faces.length; f += stride * 3) {
                if (++triangles > 200_000) throw new IOException("Avatar exceeds thumbnail triangle limit");
                if ((triangles & 255) == 0 && Thread.currentThread().isInterrupted()) throw new IOException("Thumbnail cancelled");
                double[] a = projected[faces[f]], b = projected[faces[f + stride]], c = projected[faces[f + stride * 2]];
                if (a[2] <= .01 || b[2] <= .01 || c[2] <= .01) continue;
                double area = edge(a, b, c[0], c[1]);
                if (!Double.isFinite(area) || Math.abs(area) < 1e-8) continue;
                int minX = Math.max(0, (int)Math.floor(Math.min(a[0], Math.min(b[0], c[0]))));
                int maxX = Math.min(size - 1, (int)Math.ceil(Math.max(a[0], Math.max(b[0], c[0]))));
                int minY = Math.max(0, (int)Math.floor(Math.min(a[1], Math.min(b[1], c[1]))));
                int maxY = Math.min(size - 1, (int)Math.ceil(Math.max(a[1], Math.max(b[1], c[1]))));
                int ta = faces[f + uvOffset] * 2, tb = faces[f + stride + uvOffset] * 2, tc = faces[f + stride * 2 + uvOffset] * 2;
                for (int y = minY; y <= maxY; y++) for (int x = minX; x <= maxX; x++) {
                    double wa = edge(b, c, x + .5, y + .5) / area;
                    double wb = edge(c, a, x + .5, y + .5) / area;
                    double wc = 1 - wa - wb;
                    if (wa < -1e-7 || wb < -1e-7 || wc < -1e-7) continue;
                    double inverse = wa / a[2] + wb / b[2] + wc / c[2], z = 1 / inverse;
                    int index = y * size + x;
                    if (z >= depth[index]) continue;
                    int color = 0xffffffff;
                    if (reader != null) {
                        double u = (wa * uv[ta] / a[2] + wb * uv[tb] / b[2] + wc * uv[tc] / c[2]) / inverse;
                        double v = (wa * uv[ta + 1] / a[2] + wb * uv[tb + 1] / b[2] + wc * uv[tc + 1] / c[2]) / inverse;
                        int tx = Math.clamp((int)(u * texture.getWidth()), 0, (int)texture.getWidth() - 1);
                        int ty = Math.clamp((int)(v * texture.getHeight()), 0, (int)texture.getHeight() - 1);
                        color = reader.getArgb(tx, ty);
                    }
                    // Cosmetic textures use cutout alpha; transparent texels must not occlude layers behind them.
                    if ((color >>> 24) < 128) continue;
                    pixels[index] = color; depth[index] = z;
                }
            }
        }
        static double edge(double[] a, double[] b, double x, double y) { return (x - a[0]) * (b[1] - a[1]) - (y - a[1]) * (b[0] - a[0]); }
    }
}
