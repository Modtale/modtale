package net.modtale.launcher.ui.wardrobe;

/** Shared category navigation and framing for catalog cards and the fitting-room camera. */
record CosmeticFraming(String group, double centerY, double scale, double cropY, double cropHeight) {
    static CosmeticFraming forCategory(String category) {
        return switch (category) {
            case "face", "ears", "mouth", "haircut", "facialHair", "eyebrows", "eyes",
                    "headAccessory", "faceAccessory", "earAccessory" -> new CosmeticFraming("Head", -.43, .42, 0, .48);
            case "undertop", "overtop" -> new CosmeticFraming("Tops", -.12, .65, .22, .53);
            case "pants", "overpants" -> new CosmeticFraming("Bottoms", .45, .60, .48, .52);
            case "shoes" -> new CosmeticFraming("Bottoms", .78, .32, .73, .27);
            case "gloves" -> new CosmeticFraming("Accessories", .08, .7, .28, .52);
            case "cape" -> new CosmeticFraming("Accessories", 0, 1, 0, 1);
            default -> new CosmeticFraming("Body", 0, 1, 0, 1);
        };
    }
    CosmeticFraming fit(javafx.scene.Group model) {
        if (!group.equals("Head")) return this;
        double[] extent = {Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY};
        headBounds(model, new javafx.scene.transform.Affine(), extent);
        if (!Double.isFinite(extent[0])) return this;
        try {
            var all = net.modtale.launcher.wardrobe.LocalAvatarRenderer.bounds(model);
            double radius = Math.sqrt(all.getWidth() * all.getWidth() + all.getHeight() * all.getHeight()
                    + all.getDepth() * all.getDepth()) / 2;
            if (radius < 1e-8) return this;
            double middleY = (extent[1] + extent[4]) / 2;
            double x = Math.max(Math.abs(extent[0] - all.getCenterX()), Math.abs(extent[3] - all.getCenterX()));
            double y = (extent[4] - extent[1]) / 2;
            double z = Math.max(Math.abs(extent[2] - all.getCenterZ()), Math.abs(extent[5] - all.getCenterZ()));
            double headRadius = Math.sqrt(x * x + y * y + z * z);
            return new CosmeticFraming(group, -(middleY - all.getCenterY()) / radius,
                    Math.max(scale, headRadius / radius * 1.12), cropY, cropHeight);
        } catch (java.io.IOException error) { return this; }
    }

    private static void headBounds(javafx.scene.Node node, javafx.scene.transform.Transform parent, double[] extent) {
        if (!node.isVisible()) return;
        var transform = parent.createConcatenation(node.getLocalToParentTransform());
        if (node instanceof javafx.scene.shape.MeshView view && view.getMesh() instanceof javafx.scene.shape.TriangleMesh mesh) {
            String id = java.util.Objects.toString(node.getId(), "");
            String category = id.split(":", 2)[0];
            boolean head = forCategory(category).group().equals("Head")
                    || category.equals("bodyCharacteristic") && id.toLowerCase(java.util.Locale.ROOT).contains("head");
            if (head) {
                var points = mesh.getPoints();
                for (int i = 0; i < points.size(); i += 3) {
                    var point = transform.transform(points.get(i), points.get(i + 1), points.get(i + 2));
                    extent[0] = Math.min(extent[0], point.getX()); extent[3] = Math.max(extent[3], point.getX());
                    extent[1] = Math.min(extent[1], point.getY()); extent[4] = Math.max(extent[4], point.getY());
                    extent[2] = Math.min(extent[2], point.getZ()); extent[5] = Math.max(extent[5], point.getZ());
                }
            }
        }
        if (node instanceof javafx.scene.Parent children)
            children.getChildrenUnmodifiable().forEach(child -> headBounds(child, transform, extent));
    }

}
