package net.modtale.config.db;

import java.util.Map;

public interface ArtifactManifestStore {
    String put(Map<String,String> entries);
    Map<String,String> get(String identity);
}
