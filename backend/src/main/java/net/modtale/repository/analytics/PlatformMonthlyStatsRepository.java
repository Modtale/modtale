package net.modtale.repository.analytics;

import java.util.List;
import net.modtale.model.analytics.PlatformMonthlyStats;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

// Repository shape changes intentionally trigger the mock database refresh workflow.
public interface PlatformMonthlyStatsRepository extends MongoRepository<PlatformMonthlyStats, String> {
    @Query("{ 'year': { $gte: ?0 }, 'month': { $gte: ?1 } }")
    List<PlatformMonthlyStats> findByDateRange(int startYear, int startMonth);
}
