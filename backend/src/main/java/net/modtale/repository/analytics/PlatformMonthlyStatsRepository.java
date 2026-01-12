package net.modtale.repository.analytics;

import net.modtale.model.analytics.PlatformMonthlyStats;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface PlatformMonthlyStatsRepository extends MongoRepository<PlatformMonthlyStats, String> {
    @Query("{ 'year': { $gte: ?0 }, 'month': { $gte: ?1 } }")
    List<PlatformMonthlyStats> findByDateRange(int startYear, int startMonth);
}