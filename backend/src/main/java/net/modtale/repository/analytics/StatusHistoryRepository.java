package net.modtale.repository.analytics;

import net.modtale.model.analytics.StatusHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface StatusHistoryRepository extends MongoRepository<StatusHistory, String> {
    List<StatusHistory> findByTimestampAfterOrderByTimestampAsc(LocalDateTime timestamp);
    StatusHistory findTopByOrderByTimestampDesc();
}