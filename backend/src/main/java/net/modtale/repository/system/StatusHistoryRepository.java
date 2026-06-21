package net.modtale.repository.system;

import java.time.LocalDateTime;
import java.util.List;
import net.modtale.model.system.StatusHistory;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface StatusHistoryRepository extends MongoRepository<StatusHistory, String> {
    List<StatusHistory> findByTimestampAfterOrderByTimestampAsc(LocalDateTime timestamp);
    StatusHistory findTopByOrderByTimestampDesc();
}
