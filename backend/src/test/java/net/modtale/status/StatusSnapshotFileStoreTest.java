package net.modtale.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StatusSnapshotFileStoreTest {
    @TempDir
    Path directory;

    @Test
    void nullOrCorruptSnapshotDoesNotPreventStatusStartup() throws Exception {
        Path snapshot = directory.resolve("snapshot.json");
        var properties = new StatusServiceProperties();
        properties.setSnapshotPath(snapshot.toString());
        var store = new StatusSnapshotFileStore(properties);
        for (String content : List.of("null", "{", "{}")) {
            Files.writeString(snapshot, content);
            assertEquals(List.of(), store.readHistory());
        }
    }
}
