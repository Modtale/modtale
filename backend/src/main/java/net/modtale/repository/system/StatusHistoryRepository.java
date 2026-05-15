package net.modtale.repository.system;

import net.modtale.model.system.StatusHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface StatusHistoryRepository extends MongoRepository<StatusHistory, String> {
    List<StatusHistory> findByTimestampAfterOrderByTimestampAsc(LocalDateTime timestamp);
    StatusHistory findTopByOrderByTimestampDesc();
}