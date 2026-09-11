package net.modtale.model.project;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.regex.Pattern;

/** Shared bounds and identity rules for artifact entry manifests. */
public final class SecurityManifest {
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");
    private SecurityManifest() {}
    public static boolean valid(Map<String,String> entries, boolean allowEmpty) {
        if (entries == null || entries.isEmpty()) return allowEmpty;
        if (entries.size() > 20_000) return false;
        int characters = 0;
        for (var entry : entries.entrySet()) {
            String path = entry.getKey();
            if (path == null || path.isBlank() || path.length() > 8192 || !digest(entry.getValue())) return false;
            characters += path.length();
            if (characters > 1_000_000) return false;
        }
        return true;
    }
    public static boolean digest(String value) { return value != null && SHA256.matcher(value).matches(); }
    public static String identity(Map<String,String> entries) {
        if (!valid(entries, false)) throw new IllegalArgumentException("Invalid artifact manifest");
        try {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            for (var entry : new TreeMap<>(entries).entrySet()) {
                hash.update((entry.getKey().length() + ":").getBytes(StandardCharsets.UTF_8));
                hash.update(entry.getKey().getBytes(StandardCharsets.UTF_8));
                hash.update((":" + entry.getValue() + "\n").getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(hash.digest());
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
