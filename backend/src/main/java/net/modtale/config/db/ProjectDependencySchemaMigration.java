package net.modtale.config.db;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import java.util.List;
import java.util.UUID;
import net.modtale.model.project.ProjectDependency;
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
                Filters.exists("versions.dependencies.modId"),
                Filters.exists("versions.dependencies.id", false),
                Filters.exists("modIds")
        ))) {
            int projectChanges = normalizeProject(project);
            if (projectChanges == 0) {
                continue;
            }

            projects.replaceOne(
                    Filters.eq("_id", project.get("_id")),
                    project,
                    new ReplaceOptions().upsert(false)
            );
            changedProjects++;
            changedDependencies += projectChanges;
        }

        if (changedProjects > 0) {
            logger.info("Migrated {} legacy dependency references across {} projects.", changedDependencies, changedProjects);
        }
    }

    private int normalizeProject(Document project) {
        int changes = normalizeProjectDependencyIndex(project);
        Object rawVersions = project.get("versions");
        if (!(rawVersions instanceof List<?> versions)) {
            return changes;
        }

        for (Object versionObj : versions) {
            if (!(versionObj instanceof Document version)) {
                continue;
            }
            Object rawDependencies = version.get("dependencies");
            if (!(rawDependencies instanceof List<?> dependencies)) {
                continue;
            }
            for (Object dependencyObj : dependencies) {
                if (dependencyObj instanceof Document dependency && normalizeDependency(dependency)) {
                    changes++;
                }
            }
        }
        return changes;
    }

    private int normalizeProjectDependencyIndex(Document project) {
        if (!project.containsKey("modIds")) {
            return 0;
        }
        Object legacyModIds = project.get("modIds");
        if (!project.containsKey("childProjectIds") && legacyModIds instanceof List<?>) {
            project.put("childProjectIds", legacyModIds);
        }
        project.remove("modIds");
        return 1;
    }

    private boolean normalizeDependency(Document dependency) {
        if (!dependency.containsKey("modId")
                && dependency.containsKey("projectId")
                && dependency.containsKey("dependencyType")
                && dependency.containsKey("id")) {
            return false;
        }

        Object projectId = firstPresent(dependency, "projectId", "modId");
        Object projectTitle = firstPresent(dependency, "projectTitle", "modTitle");
        dependency.putIfAbsent("id", UUID.randomUUID().toString());
        if (projectId != null) {
            dependency.put("projectId", projectId);
        }
        if (projectTitle != null) {
            dependency.put("projectTitle", projectTitle);
        }
        dependency.putIfAbsent("source", ProjectDependency.Source.MODTALE.name());
        dependency.putIfAbsent("hytaleProjectConfirmed", false);
        dependency.put("dependencyType", inferDependencyType(dependency));

        dependency.remove("modId");
        dependency.remove("modTitle");
        dependency.remove("isOptional");
        dependency.remove("isEmbedded");
        return true;
    }

    private Object firstPresent(Document document, String primary, String fallback) {
        Object primaryValue = document.get(primary);
        return primaryValue != null ? primaryValue : document.get(fallback);
    }

    private String inferDependencyType(Document dependency) {
        if (Boolean.TRUE.equals(dependency.getBoolean("isEmbedded"))) {
            return ProjectDependency.DependencyType.EMBEDDED.name();
        }
        if (Boolean.TRUE.equals(dependency.getBoolean("isOptional"))) {
            return ProjectDependency.DependencyType.OPTIONAL.name();
        }
        Object existingType = dependency.get("dependencyType");
        if (existingType != null) {
            return existingType.toString();
        }
        return ProjectDependency.DependencyType.REQUIRED.name();
    }
}
