package net.modtale.service.analytics;

import net.modtale.model.analytics.CreatorAnalytics;
import net.modtale.model.analytics.PlatformAnalyticsSummary;
import net.modtale.model.analytics.ProjectAnalyticsDetail;
import net.modtale.repository.project.ProjectRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

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

    public PlatformAnalyticsSummary getPlatformAnalytics(String range) {
        return platformAnalyticsQueryService.getPlatformAnalytics(range);
    }

    public CreatorAnalytics getCreatorDashboard(String userId, String range, List<String> include) {
        return creatorProjectAnalyticsQueryService.getCreatorDashboard(userId, range, include);
    }

    public ProjectAnalyticsDetail getProjectAnalytics(String projectId, String userId, String range) {
        return creatorProjectAnalyticsQueryService.getProjectAnalytics(projectId, userId, range);
    }
}
