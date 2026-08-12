package net.modtale.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import net.modtale.status.StatusModels.StatusHistoryEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

@Repository
public class StatusSnapshotFileStore {

    private static final Logger logger = LoggerFactory.getLogger(StatusSnapshotFileStore.class);

    private final StatusServiceProperties properties;
    private final ObjectMapper objectMapper;

    public StatusSnapshotFileStore(StatusServiceProperties properties) {
        this.properties = properties;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    public List<StatusHistoryEntry> readHistory() {
        Path path = Path.of(properties.getSnapshotPath());
        if (!Files.isRegularFile(path)) {
            return List.of();
        }

        try {
            SnapshotState state = objectMapper.readValue(path.toFile(), SnapshotState.class);
            return state.history() != null ? state.history() : List.of();
        } catch (IOException e) {
            logger.warn("Could not read detached status snapshot cache at {}", path, e);
            return List.of();
        }
    }

    public void writeHistory(List<StatusHistoryEntry> history) {
        Path path = Path.of(properties.getSnapshotPath());
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            Path temp = path.resolveSibling(path.getFileName() + ".tmp");
            objectMapper.writeValue(temp.toFile(), new SnapshotState(history));
            Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            logger.warn("Could not write detached status snapshot cache at {}", path, e);
        }
    }

    public record SnapshotState(List<StatusHistoryEntry> history) {
    }
}
