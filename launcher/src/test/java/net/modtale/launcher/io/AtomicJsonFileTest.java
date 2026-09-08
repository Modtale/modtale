package net.modtale.launcher.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AtomicJsonFileTest {
    @TempDir
    Path directory;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void replacesExistingJsonAndLeavesNoTemporaryFiles() throws Exception {
        Path destination = directory.resolve("nested/settings.json");
        AtomicJsonFile.write(destination, mapper.writer(), Map.of("value", "old"));
        AtomicJsonFile.write(destination, mapper.writer(), Map.of("value", "new"));
        assertEquals("new", mapper.readTree(destination.toFile()).path("value").asText());
        try (var files = Files.list(destination.getParent())) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void serializationFailurePreservesPreviousDocumentAndCleansTemporaryFile() throws Exception {
        Path destination = directory.resolve("settings.json");
        String original = "{\"value\":\"previous\"}";
        Files.writeString(destination, original);
        assertThrows(IOException.class, () -> AtomicJsonFile.write(destination, mapper.writer(), new BrokenDocument()));
        assertEquals(original, Files.readString(destination));
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    public static class BrokenDocument {
        public String getValue() throws IOException {
            throw new IOException("Simulated serialization failure");
        }
    }
}
