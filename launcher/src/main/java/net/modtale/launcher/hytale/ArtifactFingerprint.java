package net.modtale.launcher.hytale;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ArtifactFingerprint {
    private static final int MURMUR_M = 0x5bd1e995;

    private ArtifactFingerprint() {}

    public static Result calculate(Path file) throws IOException {
        MessageDigest digest;
        try { digest = MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
        int normalizedLength = 0;
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
                for (int i = 0; i < read; i++) if (!isWhitespace(buffer[i])) normalizedLength++;
            }
        }
        int hash = 1 ^ normalizedLength;
        int pending = 0;
        int pendingCount = 0;
        try (InputStream input = Files.newInputStream(file)) {
            int value;
            while ((value = input.read()) >= 0) {
                if (isWhitespace((byte) value)) continue;
                pending |= value << (pendingCount * 8);
                if (++pendingCount == 4) {
                    hash = mix(hash, pending);
                    pending = 0;
                    pendingCount = 0;
                }
            }
        }
        if (pendingCount == 3) hash ^= pending & 0x00ff0000;
        if (pendingCount >= 2) hash ^= pending & 0x0000ff00;
        if (pendingCount >= 1) { hash ^= pending & 0x000000ff; hash *= MURMUR_M; }
        hash ^= hash >>> 13;
        hash *= MURMUR_M;
        hash ^= hash >>> 15;
        return new Result(HexFormat.of().formatHex(digest.digest()), Integer.toUnsignedLong(hash));
    }

    private static int mix(int hash, int block) {
        int k = block * MURMUR_M;
        k ^= k >>> 24;
        k *= MURMUR_M;
        hash *= MURMUR_M;
        return hash ^ k;
    }

    private static boolean isWhitespace(byte value) {
        int unsigned = value & 0xff;
        return unsigned == 0x09 || unsigned == 0x0a || unsigned == 0x0d || unsigned == 0x20;
    }

    public record Result(String sha256, long curseForgeFingerprint) {}
}
