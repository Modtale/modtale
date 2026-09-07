package net.modtale.launcher.io;

import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Writes complete JSON before replacing a previously saved document. */
public final class AtomicJsonFile {
    private AtomicJsonFile() {
    }

    public static void write(Path path, ObjectWriter writer, Object value) throws IOException {
        Path destination = path.toAbsolutePath().normalize();
        Files.createDirectories(destination.getParent());
        Path temporary = Files.createTempFile(destination.getParent(), ".modtale-", ".tmp");
        try {
            writer.writeValue(temporary.toFile(), value);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                channel.force(true);
            }
            try {
                Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
