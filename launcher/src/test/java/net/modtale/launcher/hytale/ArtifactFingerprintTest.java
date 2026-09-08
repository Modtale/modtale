package net.modtale.launcher.hytale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ArtifactFingerprintTest {
    @TempDir Path temp;

    @Test
    void calculatesSha256AndCurseForgeMurmur2() throws Exception {
        Path file = temp.resolve("mod.jar");
        Files.writeString(file, "foo");
        ArtifactFingerprint.Result result = ArtifactFingerprint.calculate(file);
        assertEquals("2c26b46b68ffc68ff99b453c1d30413413422d706483bfa0f98a5e886266e7ae", result.sha256());
        assertEquals(197930586L, result.curseForgeFingerprint());
    }

    @Test
    void curseForgeFingerprintIgnoresOnlyProviderWhitespace() throws Exception {
        Path clean = temp.resolve("clean.jar");
        Path spaced = temp.resolve("spaced.jar");
        Files.writeString(clean, "foo");
        Files.writeString(spaced, "f\to o\r\n");
        assertEquals(ArtifactFingerprint.calculate(clean).curseForgeFingerprint(),
                ArtifactFingerprint.calculate(spaced).curseForgeFingerprint());
    }
}
