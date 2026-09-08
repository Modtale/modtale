package net.modtale.launcher.hytale;

import java.nio.file.Path;

public record HytaleLaunchResult(Process process, Path executable, String username, String uuid) {
}
