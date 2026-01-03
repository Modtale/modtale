package net.modtale.repository.analytics;

import net.modtale.model.analytics.ViewLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;

public interface ViewLogRepository extends MongoRepository<ViewLog, String> {
    long countByAuthorId(String authorId);
    long countByAuthorIdAndTimestampBetween(String authorId, LocalDateTime start, LocalDateTime end);
    long countByProjectId(String projectId);
}