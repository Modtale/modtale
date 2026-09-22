package net.modtale.config.db;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import net.modtale.mapper.ProjectMapper;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectDependency;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.mapping.event.AfterLoadEvent;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveEvent;

class ProjectDependencyCompatibilityTest {
    // Exact references from the public database artifact, not version-number matching.
    private static final List<String> IDS = List.of("9e3f3c46-92e1-4f57-8702-4ec11d7d10b4",
            "2ebf130e-2189-4e90-9323-803a374d05ce", "9edc380c-42f2-4a78-ad80-dc7a399a7699");
    private static final List<String> TITLES = List.of("Voile", "Dynamic Tooltips Lib", "Hexcode");
    private static final List<String> VERSIONS = List.of("1.10.2", "1.5.2", "0.8.6-Beta");

    @Test
    void canonicalLevelingCoreReferencesRemainReadableByBothBackendModels() throws Exception {
        var converter = converter();
        var dependencies = java.util.stream.IntStream.range(0, 3).mapToObj(i ->
                new Document("projectId", IDS.get(i)).append("projectTitle", TITLES.get(i))
                        .append("versionNumber", VERSIONS.get(i)).append("dependencyType", "OPTIONAL")
                        .append("source", "MODTALE")).toList();
        Document project = project(dependencies);
        assertEquals(3, ProjectDependencyDocumentCompatibility.normalizeProject(project));
        for (int i = 0; i < dependencies.size(); i++) {
            Document document = dependencies.get(i);
            LegacyDependency legacy = converter.read(LegacyDependency.class, document);
            assertEquals(IDS.get(i), legacy.modId);
            assertEquals(TITLES.get(i), legacy.modTitle);
            assertTrue(legacy.isOptional);
            var current = ProjectMapper.toDependencyDTO(converter.read(ProjectDependency.class, document));
            assertEquals(IDS.get(i), current.projectId());
            assertEquals(TITLES.get(i), current.projectTitle());
            assertEquals(ProjectDependency.Source.MODTALE, current.source());
            assertTrue(current.isOptional());
        }
        assertEquals(0, ProjectDependencyDocumentCompatibility.normalizeProject(project), "Backfill is idempotent");
    }

    @Test
    void legacyDocumentsHydrateBeforeStartupMigrationAndSavesRetainAliases() throws Exception {
        Document dependency = new Document("modId", IDS.getFirst()).append("modTitle", "Voile")
                .append("isEmbedded", true).append("versionNumber", "1.10.2");
        Document project = project(List.of(dependency));
        var listener = new ProjectDependencyCompatibilityListener();
        listener.onAfterLoad(new AfterLoadEvent<>(project, Project.class, "projects"));
        var converter = converter();
        ProjectDependency mapped = converter.read(ProjectDependency.class, dependency);
        assertEquals(IDS.getFirst(), mapped.getProjectId());
        assertTrue(mapped.isEmbedded());
        Document savedDependency = new Document();
        converter.write(mapped, savedDependency);
        assertFalse(savedDependency.containsKey("modId"), "Unknown aliases are omitted by the Mongo mapper");
        listener.onBeforeSave(new BeforeSaveEvent<>(new Project(), project(List.of(savedDependency)), "projects"));
        assertEquals(IDS.getFirst(), savedDependency.getString("modId"));
        assertEquals("Voile", savedDependency.getString("modTitle"));
        assertTrue(savedDependency.getBoolean("isEmbedded"));
    }

    @Test
    void preservesExternalSourceAndCanonicalFlagsWithoutInventingMissingIdentity() {
        Document external = new Document("source", "CURSEFORGE").append("externalId", "1234")
                .append("dependencyType", "REQUIRED").append("isOptional", true);
        Document unidentified = new Document("versionNumber", "1.10.2");
        ProjectDependencyDocumentCompatibility.normalizeProject(project(List.of(external, unidentified)));
        assertEquals("CURSEFORGE", external.getString("source"));
        assertEquals("1234", external.getString("externalId"));
        assertFalse(external.getBoolean("isOptional"));
        assertFalse(unidentified.containsKey("projectId"));
        assertFalse(unidentified.containsKey("modId"));
    }

    private static Document project(List<Document> dependencies) {
        return new Document("_id", "5e9bbea3-0d7f-4365-93df-5e7acfadf0e7")
                .append("versions", List.of(new Document("versionNumber", "1.1.3").append("dependencies", dependencies)));
    }

    private static MappingMongoConverter converter() throws Exception {
        var context = new MongoMappingContext();
        context.afterPropertiesSet();
        var converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.afterPropertiesSet();
        return converter;
    }

    static class LegacyDependency {
        String modId;
        String modTitle;
        boolean isOptional;
        boolean isEmbedded;
    }
}
