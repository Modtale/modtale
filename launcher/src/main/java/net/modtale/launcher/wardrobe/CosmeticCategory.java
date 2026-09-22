package net.modtale.launcher.wardrobe;

/** A verified Hytale skin wire key and its display label. */
public record CosmeticCategory(String key, String label) {
    public CosmeticCategory {
        key = WardrobeStore.text(key, "category key", 64, false);
        label = WardrobeStore.text(label, "category label", 120, false);
    }
}
