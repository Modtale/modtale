package net.modtale.launcher.hytale;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import javax.imageio.ImageIO;

/** Local, deterministic profile icons. Names and profile activity never leave the device. */
public final class HytaleAvatarClient {
    private final Executor executor;

    public HytaleAvatarClient(HytaleAuthService auth, Executor executor) { this.executor = executor; }

    public static String usernameAvatarUrl(String username) {
        String name = username == null ? "" : username.trim();
        if (!name.matches("[a-zA-Z0-9_]{3,16}")) throw new IllegalArgumentException("Invalid avatar username");
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(name.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            // Session-private files are deleted on exit; no growing permanent profile archive.
            return Icons.url(hash);
        } catch (java.security.NoSuchAlgorithmException | IOException ex) {
            throw new IllegalStateException("Could not create local profile icon", ex);
        }
    }

    public CompletableFuture<String> avatarUrl(String username) {
        return CompletableFuture.supplyAsync(() -> usernameAvatarUrl(username), executor);
    }

    private static final class Icons {
        private static final java.util.Map<String, String> CACHE = new java.util.LinkedHashMap<>();
        private static Path directory;
        static synchronized String url(byte[] hash) throws IOException {
            String key = HexFormat.of().formatHex(hash);
            String cached = CACHE.get(key); if (cached != null) return cached;
            if (directory == null) { directory = Files.createTempDirectory("modtale-profile-icons-"); directory.toFile().deleteOnExit(); }
            BufferedImage image = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            try {
                graphics.setColor(new Color(0x17263c)); graphics.fillRect(0, 0, 128, 128);
                graphics.setColor(Color.getHSBColor((hash[0] & 255) / 255f, .55f, .95f));
                for (int row = 0; row < 5; row++) for (int col = 0; col < 3; col++) {
                    if ((hash[1 + row * 3 + col] & 1) == 0) continue;
                    graphics.fillRect(14 + col * 20, 14 + row * 20, 20, 20);
                    graphics.fillRect(14 + (4 - col) * 20, 14 + row * 20, 20, 20);
                }
            } finally { graphics.dispose(); }
            Path path = directory.resolve(key + ".png");
            ImageIO.write(image, "png", path.toFile()); path.toFile().deleteOnExit();
            if (CACHE.size() >= 256) {
                var first = CACHE.entrySet().iterator(); var entry = first.next();
                Files.deleteIfExists(Path.of(java.net.URI.create(entry.getValue()))); first.remove();
            }
            String url = path.toUri().toString(); CACHE.put(key, url); return url;
        }
    }
}
