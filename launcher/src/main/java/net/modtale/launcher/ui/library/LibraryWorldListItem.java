package net.modtale.launcher.ui.library;

import net.modtale.launcher.hytale.HytaleWorldManager.HytaleWorld;

record LibraryWorldListItem(
        HytaleWorld world,
        String meta,
        int enabledProjectCount,
        int totalProjectCount
) {
    LibraryWorldListItem {
        meta = meta == null ? "" : meta.trim();
        totalProjectCount = Math.max(0, totalProjectCount);
        enabledProjectCount = Math.max(0, Math.min(enabledProjectCount, totalProjectCount));
    }
}
