package net.modtale.service.analytics;

import net.modtale.model.dto.response.analytics.PlatformStatsView;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.User;
import org.bson.Document;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class PlatformStatsService {

    private final MongoTemplate mongoTemplate;

    public PlatformStatsService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Cacheable(value = "platformStats", key = "'public'", sync = true)
    public PlatformStatsView getPublicStats() {
        long totalProjects = mongoTemplate.count(new Query(Criteria.where("status").is(ProjectStatus.PUBLISHED)), Project.class);
        long totalUsers = mongoTemplate.count(new Query(), User.class);

        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("status").is(ProjectStatus.PUBLISHED)),
                Aggregation.group().sum("downloadCount").as("totalDownloads")
        );

        AggregationResults<Document> results = mongoTemplate.aggregate(aggregation, Project.class, Document.class);
        long totalDownloads = 0;
        Document mappedResult = results.getUniqueMappedResult();
        if (mappedResult != null && mappedResult.get("totalDownloads") != null) {
            totalDownloads = ((Number) mappedResult.get("totalDownloads")).longValue();
        }

        return new PlatformStatsView(totalProjects, totalUsers, totalDownloads);
    }
}
