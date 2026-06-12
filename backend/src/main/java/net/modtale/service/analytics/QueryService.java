package net.modtale.service.analytics;

import java.util.List;
import net.modtale.model.analytics.CreatorAnalytics;
import net.modtale.model.analytics.PlatformAnalyticsSummary;
import net.modtale.model.analytics.ProjectAnalyticsDetail;
import net.modtale.repository.project.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

@Service
public class QueryService {

    private final PlatformAnalyticsQueryService platformAnalyticsQueryService;
    private final CreatorProjectAnalyticsQueryService creatorProjectAnalyticsQueryService;

    @Autowired
    public QueryService(
            PlatformAnalyticsQueryService platformAnalyticsQueryService,
            CreatorProjectAnalyticsQueryService creatorProjectAnalyticsQueryService
    ) {
        this.platformAnalyticsQueryService = platformAnalyticsQueryService;
        this.creatorProjectAnalyticsQueryService = creatorProjectAnalyticsQueryService;
    }

    public QueryService(MongoTemplate mongoTemplate, ProjectRepository projectRepository) {
        AnalyticsQuerySupportService supportService = new AnalyticsQuerySupportService(mongoTemplate);
        this.platformAnalyticsQueryService = new PlatformAnalyticsQueryService(mongoTemplate, supportService);
        this.creatorProjectAnalyticsQueryService = new CreatorProjectAnalyticsQueryService(projectRepository, supportService);
    }

    @Cacheable(value = "platformAnalytics", key = "#range", sync = true)
    public PlatformAnalyticsSummary getPlatformAnalytics(String range) {
        return platformAnalyticsQueryService.getPlatformAnalytics(range);
    }

    @Cacheable(value = "creatorAnalytics", key = "T(java.util.Arrays).asList(#userId, #range, #include)", sync = true)
    public CreatorAnalytics getCreatorDashboard(String userId, String range, List<String> include) {
        return creatorProjectAnalyticsQueryService.getCreatorDashboard(userId, range, include);
    }

    @Cacheable(value = "projectAnalytics", key = "T(java.util.Arrays).asList(#projectId, #userId, #range)", sync = true)
    public ProjectAnalyticsDetail getProjectAnalytics(String projectId, String userId, String range) {
        return creatorProjectAnalyticsQueryService.getProjectAnalytics(projectId, userId, range);
    }
}
