package net.modtale.service.project.version;

import java.util.List;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class VersionManifestCandidateQueryService {

    private final MongoTemplate mongoTemplate;

    public VersionManifestCandidateQueryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<Project> findPublishedPluginCandidates(String excludedProjectId) {
        Query query = new Query(Criteria.where("status").in(ProjectStatus.PUBLISHED, ProjectStatus.ARCHIVED)
                .and("deletedAt").is(null)
                .and("_id").ne(excludedProjectId)
                .and("classification").is(ProjectClassification.PLUGIN));
        query.fields().include("title").include("slug").include("versions");
        return mongoTemplate.find(query, Project.class);
    }
}
