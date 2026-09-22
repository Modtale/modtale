package net.modtale.launcher.wardrobe;

import java.util.Objects;
import java.util.UUID;

/** A named local cosmetic. The payload is a JSON object, not a remote resource to fetch. */
public record WardrobeItem(UUID id, Kind kind, String name, boolean favorite, String collection, String payload) {
    public enum Kind { SKIN, CAPE }

    public WardrobeItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(kind, "kind");
        name = WardrobeStore.text(name, "name", 120, false);
        collection = WardrobeStore.text(collection, "collection", 120, true);
        payload = WardrobeStore.validatePayload(payload);
    }
}
