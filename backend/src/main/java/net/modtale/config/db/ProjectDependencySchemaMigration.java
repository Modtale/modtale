package net.modtale.config.db;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import java.util.ArrayList;
import java.util.List;
import org.bson.conversions.Bson;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProjectDependencySchemaMigration {

    private static final Logger logger = LoggerFactory.getLogger(ProjectDependencySchemaMigration.class);

    private final MongoTemplate mongoTemplate;

    public ProjectDependencySchemaMigration(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrateLegacyDependencyDocuments() {
        MongoCollection<Document> projects = mongoTemplate.getCollection("projects");
        int changedProjects = 0;
        int changedDependencies = 0;

        for (Document project : projects.find(Filters.or(
                Filters.exists("versions.dependencies.0"),
                Filters.exists("modIds")
        ))) {
            Document original = Document.parse(project.toJson());
            int projectChanges = ProjectDependencyDocumentCompatibility.normalizeProject(project);
            if (projectChanges == 0) {
                continue;
            }

            List<Bson> updates = new ArrayList<>();
            for (String field : List.of("versions", "modIds", "childProjectIds")) {
                if (project.containsKey(field)) updates.add(Updates.set(field, project.get(field)));
            }
            var result = projects.updateOne(
                    Filters.and(Filters.eq("_id", project.get("_id")),
                            Filters.eq("versions", original.get("versions")),
                            Filters.eq("modIds", original.get("modIds")),
                            Filters.eq("childProjectIds", original.get("childProjectIds"))),
                    Updates.combine(updates)
            );
            if (result.getModifiedCount() == 0) continue;
            changedProjects++;
            changedDependencies += projectChanges;
        }

        if (changedProjects > 0) {
            logger.info("Migrated {} legacy dependency references across {} projects.", changedDependencies, changedProjects);
        }
    }

}
