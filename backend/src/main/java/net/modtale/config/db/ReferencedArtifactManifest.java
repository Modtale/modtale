package net.modtale.config.db;

import net.modtale.model.project.SecurityManifest;
import java.util.*;

/** Existing Map-based consumers still inspect real verified entries before granting clearance. */
final class ReferencedArtifactManifest extends AbstractMap<String,String> {
    final ArtifactManifestStore store;
    final String identity;
    private volatile Map<String,String> loaded;
    ReferencedArtifactManifest(ArtifactManifestStore store, String identity) {
        if (store == null || !SecurityManifest.digest(identity)) throw new IllegalArgumentException("Invalid artifact manifest reference");
        this.store = store; this.identity = identity;
    }
    private Map<String,String> load() {
        var snapshot = loaded;
        if (snapshot != null) return snapshot;
        synchronized (this) {
            if (loaded == null) {
                try {
                    var entries = store.get(identity);
                    if (!SecurityManifest.valid(entries, false) || !identity.equals(SecurityManifest.identity(entries)))
                        throw new IllegalStateException("Artifact manifest integrity check failed");
                    loaded = Collections.unmodifiableMap(new TreeMap<>(entries));
                } catch (RuntimeException unavailable) { throw new SecurityManifest.Unavailable(unavailable); }
            }
            return loaded;
        }
    }
    @Override public Set<Entry<String,String>> entrySet() { return load().entrySet(); }
}
