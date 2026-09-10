package net.modtale.config.db;

import net.modtale.model.project.Project;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.AfterLoadEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;
import org.springframework.stereotype.Component;

@Component
public class ProjectDependencyCompatibilityListener extends AbstractMongoEventListener<Project> {
    @Override
    public void onAfterLoad(AfterLoadEvent<Project> event) {
        // Read legacy references even before the startup backfill has completed.
        ProjectDependencyDocumentCompatibility.normalizeProject(event.getDocument());
    }

    @Override
    public void onBeforeSave(BeforeSaveEvent<Project> event) {
        // MappingMongoConverter omits fields unknown to ProjectDependency; restore
        // aliases on every save so an older backend can still enrich dependencies.
        if (event.getDocument() != null) {
            ProjectDependencyDocumentCompatibility.normalizeProject(event.getDocument());
        }
    }
}
