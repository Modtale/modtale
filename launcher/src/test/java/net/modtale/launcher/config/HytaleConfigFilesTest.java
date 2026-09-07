package net.modtale.launcher.config;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.modtale.launcher.config.HytaleConfigFiles.ConfigFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HytaleConfigFilesTest {
    @TempDir Path directory;
    private final HytaleConfigFiles service = new HytaleConfigFiles();

    @Test
    void discoversGlobalAndWorldConfigsWithoutManifestsArchivesOrOtherWorlds() throws Exception {
        write("Mods/Example_Plugin/settings.toml", "count=2");
        write("Mods/Example/manifest.json", "{}");
        write("Mods/example.jar", "binary");
        write("Saves/My World/mods/Example_Plugin/config.json", "{}");
        write("Saves/My World/config.json", "{}");
        write("Saves/My World/universe/worlds/default/config.json", "{}");
        write("Saves/Other/mods/Example_Plugin/config.json", "{}");
        List<ConfigFile> files = discover();
        assertEquals(4, files.size());
        assertTrue(files.stream().anyMatch(file -> file.label().equals("World mods / Example_Plugin/config.json")));
    }

    @Test
    void saveValidatesJsonAndKeepsExactBackup() throws Exception {
        Path path = write("Saves/My World/mods/Example_Plugin/config.json", "{\r\n  \"value\": 1\r\n}\r\n");
        var snapshot = service.read(discover().getFirst());
        assertThrows(IOException.class, () -> service.save(snapshot, "{broken"));
        assertThrows(IOException.class, () -> service.save(snapshot, "{} {}"));
        assertThrows(IOException.class, () -> service.save(snapshot, "{\"x\":1,\"x\":2}"));
        assertEquals(snapshot.text(), Files.readString(path));
        var saved = service.save(snapshot, "{\"value\":2}");
        assertEquals(saved.text(), Files.readString(path));
        try (var children = Files.list(path.getParent())) {
            Path backup = children.filter(file -> file.toString().endsWith(".bak")).findFirst().orElseThrow();
            assertEquals(snapshot.text(), Files.readString(backup));
        }
    }

    @Test
    void rejectsExternalChangesAndDoesNotOverwriteThem() throws Exception {
        Path path = write("Saves/My World/mods/Example/config.json", "{}");
        var snapshot = service.read(discover().getFirst());
        Files.writeString(path, "{\"changedByGame\":true}");
        assertTrue(assertThrows(IOException.class, () -> service.save(snapshot, "{\"editor\":true}"))
                .getMessage().contains("changed outside"));
        assertEquals("{\"changedByGame\":true}", Files.readString(path));
    }

    @Test
    void rejectsSymlinksIncludingReplacementAfterRead() throws Exception {
        Path path = write("Saves/My World/mods/Example/config.json", "{}");
        var snapshot = service.read(discover().getFirst());
        Path outside = write("outside.json", "{}");
        Files.delete(path);
        Files.createSymbolicLink(path, outside);
        assertTrue(discover().isEmpty());
        assertThrows(IOException.class, () -> service.save(snapshot, "{\"edited\":true}"));
        assertEquals("{}", Files.readString(outside));
    }

    @Test
    void rejectsOversizedBinaryAndInvalidUtf8Files() throws Exception {
        Path path = write("Saves/My World/mods/Example/config.json", "{}");
        ConfigFile file = discover().getFirst();
        Files.write(path, new byte[HytaleConfigFiles.MAX_BYTES + 1]);
        assertTrue(discover().isEmpty());
        assertThrows(IOException.class, () -> service.read(file));
        Files.write(path, new byte[]{(byte) 0xff});
        assertThrows(IOException.class, () -> service.read(file));
        Files.write(path, new byte[]{0});
        assertThrows(IOException.class, () -> service.read(file));
    }

    @Test
    void savesTextFormatsAndBomJson() throws Exception {
        Path path = write("Mods/Example/settings.ini", "value=1\r\n");
        var snapshot = service.read(discover().getFirst());
        service.save(snapshot, "value=2\r\n");
        assertEquals("value=2\r\n", Files.readString(path));
        Path json = write("Saves/My World/config.json", "\uFEFF{}");
        ConfigFile file = discover().stream().filter(entry -> entry.path().equals(json)).findFirst().orElseThrow();
        service.save(service.read(file), "\uFEFF{\"x\":2}");
        assertEquals("\uFEFF{\"x\":2}", Files.readString(json));
    }

    private List<ConfigFile> discover() throws IOException {
        return service.discover(directory.resolve("Mods"), directory.resolve("Saves/My World"));
    }

    private Path write(String relative, String text) throws IOException {
        Path file = directory.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
        return file;
    }
}
