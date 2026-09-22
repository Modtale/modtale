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
}
