package net.modtale.repository;

import net.modtale.model.StatusHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface StatusHistoryRepository extends MongoRepository<StatusHistory, String> {
    List<StatusHistory> findByTimestampAfterOrderByTimestampAsc(LocalDateTime timestamp);
    StatusHistory findTopByOrderByTimestampDesc();
}