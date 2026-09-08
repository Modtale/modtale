package net.modtale.launcher.wardrobe;

import java.util.List;

/** One exact supported selection; entitlement IDs describe requirements, not ownership. */
public record CosmeticOption(String category, String id, String assetId, String colorId, String variantId,
                             String label, List<String> swatches, List<String> entitlements) {
    public CosmeticOption {
        category = WardrobeStore.text(category, "category", 64, false);
        id = WardrobeStore.text(id, "selection id", 512, false);
        assetId = WardrobeStore.text(assetId, "asset id", 256, false);
        colorId = WardrobeStore.text(colorId, "color id", 128, true);
        variantId = WardrobeStore.text(variantId, "variant id", 128, true);
        label = WardrobeStore.text(label, "label", 256, false);
        swatches = List.copyOf(swatches);
        entitlements = List.copyOf(entitlements);
    }
}
