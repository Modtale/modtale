package net.modtale.service.project.version;

import java.io.IOException;
import java.io.InputStream;

final class CurseForgeFingerprint {
    private static final int M = 0x5bd1e995;
    private CurseForgeFingerprint() {}

    static long calculate(org.springframework.web.multipart.MultipartFile file) throws IOException {
        int length = 0;
        try (InputStream input = file.getInputStream()) {
            int value;
            while ((value = input.read()) >= 0) if (!whitespace(value)) length++;
        }
        int hash = 1 ^ length;
        int block = 0;
        int count = 0;
        try (InputStream input = file.getInputStream()) {
            int value;
            while ((value = input.read()) >= 0) {
                if (whitespace(value)) continue;
                block |= value << (count * 8);
                if (++count == 4) {
                    int k = block * M;
                    k ^= k >>> 24;
                    k *= M;
                    hash = (hash * M) ^ k;
                    block = 0;
                    count = 0;
                }
            }
        }
        if (count == 3) hash ^= block & 0x00ff0000;
        if (count >= 2) hash ^= block & 0x0000ff00;
        if (count >= 1) { hash ^= block & 0x000000ff; hash *= M; }
        hash ^= hash >>> 13;
        hash *= M;
        hash ^= hash >>> 15;
        return Integer.toUnsignedLong(hash);
    }

    private static boolean whitespace(int value) {
        return value == 0x09 || value == 0x0a || value == 0x0d || value == 0x20;
    }
}
