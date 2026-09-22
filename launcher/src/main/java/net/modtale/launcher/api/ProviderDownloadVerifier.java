package net.modtale.launcher.api;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

final class ProviderDownloadVerifier {

    private ProviderDownloadVerifier() {
    }

    static void verify(Path file, Long expectedSize, Map<String, String> hashes) throws IOException {
        if (expectedSize != null && expectedSize > 0 && Files.size(file) != expectedSize) {
            throw new IOException("Downloaded file size did not match CurseForge metadata.");
        }
        Map<String, String> expected = hashes == null ? Map.of() : hashes;
        String algorithm = expected.containsKey("sha1") ? "sha1" : expected.containsKey("md5") ? "md5" : null;
        if (algorithm == null) return;
        String actual = digest(file, algorithm);
        if (!actual.equalsIgnoreCase(expected.get(algorithm))) {
            throw new IOException("Downloaded file hash did not match CurseForge metadata.");
        }
    }

    private static String digest(Path file, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm.toUpperCase(Locale.ROOT).replace("SHA1", "SHA-1"));
            try (InputStream input = Files.newInputStream(file)) {
                byte[] buffer = new byte[16 * 1024];
                for (int read; (read = input.read(buffer)) >= 0;) {
                    if (read > 0) digest.update(buffer, 0, read);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException("Could not verify the provider download.", ex);
        }
    }
}
