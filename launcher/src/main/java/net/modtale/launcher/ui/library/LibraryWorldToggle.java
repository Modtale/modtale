package net.modtale.launcher.ui.library;

import java.util.List;
import net.modtale.launcher.hytale.HytaleWorldManager.HytaleWorld;

record LibraryWorldToggle(
        HytaleWorld world,
        String meta,
        int enabledCount,
        int totalCount,
        boolean selected,
        boolean indeterminate,
        List<String> modIds
) {
    LibraryWorldToggle {
        modIds = modIds == null ? List.of() : List.copyOf(modIds);
    }
}
