package net.modtale.service.project;

import net.modtale.model.project.Project;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ScheduledReleaseQueryService {

    private final MongoTemplate mongoTemplate;

    public ScheduledReleaseQueryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<Project> findProjectsWithReleasesDueAt(LocalDateTime publishTime) {
        return mongoTemplate.find(
                new Query(Criteria.where("versions").elemMatch(
                        Criteria.where("reviewStatus").is("SCHEDULED")
                                .and("scheduledPublishDate").lte(publishTime.toString())
                )),
                Project.class
        );
    }
}
