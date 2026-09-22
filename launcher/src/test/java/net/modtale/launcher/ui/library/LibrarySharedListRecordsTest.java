package net.modtale.launcher.ui.library;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleInstalledMod;
import net.modtale.launcher.model.worldlist.*;
import org.junit.jupiter.api.Test;

class LibrarySharedListRecordsTest {
    @Test void retainsCanonicalIdentityAndPinnedCurseForgeFile() {
        var nativeFile = Path.of("/mods/LevelingCore.jar");
        var cfFile = Path.of("/mods/BetterMap.jar");
        var unrelated = Path.of("/mods/other.jar");
        var list = new WorldModList("list", "World", "World", "", "", null, null, null,
                0, 0, 2, 1, "", "", "", List.of(
                new WorldModListItem("a", "com.azuredoom:levelingcore", "canonical-uuid", "levelingcore", "LevelingCore", "1.1.4", "PLUGIN", "MODTALE", "", "", "", true, ""),
                new WorldModListItem("b", "dev.ninesliced:BetterMap", "", "bettermap", "BetterMap", "1.3.7", "PLUGIN", "CURSEFORGE", "1430352", "https://www.curseforge.com/hytale/mods/bettermap/files/8230539", "", false, "")));
        var scanned = List.of(new HytaleInstalledMod("com.azuredoom:levelingcore", "LevelingCore", "1.1.4", "", nativeFile),
                new HytaleInstalledMod("dev.ninesliced:BetterMap", "BetterMap", "1.3.7", "", cfFile),
                new HytaleInstalledMod("com.azuredoom:levelingcore", "Old copy", "1.0", "", unrelated));
        var records = LibrarySharedListRecords.merge(List.of(), list, scanned, List.of(nativeFile, cfFile));
        assertEquals(2, records.size());
        assertEquals("canonical-uuid", records.getFirst().projectId());
        assertEquals(List.of(nativeFile.toString()), records.getFirst().files());
        assertEquals("CURSEFORGE", records.get(1).source());
        assertEquals("8230539", records.get(1).installedVersionId());
        assertEquals(2, LibrarySharedListRecords.merge(records, list, scanned, List.of(nativeFile, cfFile)).size());
    }
}
