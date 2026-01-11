package net.modtale.repository.analytics;

import net.modtale.model.analytics.ProjectMonthlyStats;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface ProjectMonthlyStatsRepository extends MongoRepository<ProjectMonthlyStats, String> {

    @Query("{ 'authorId': ?0, 'year': { $gte: ?1 }, 'month': { $gte: ?2 } }")
    List<ProjectMonthlyStats> findByAuthorAndDateRange(String authorId, int startYear, int startMonth);

    @Query("{ 'projectId': ?0, 'year': { $gte: ?1 }, 'month': { $gte: ?2 } }")
    List<ProjectMonthlyStats> findByProjectAndDateRange(String projectId, int startYear, int startMonth);
}