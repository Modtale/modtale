package net.modtale.launcher.ui.project;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.modtale.launcher.model.project.ProjectDependency;
import net.modtale.launcher.model.project.ProjectMeta;
import org.junit.jupiter.api.Test;

class ProjectDependencyMetadataTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void legacyIdentityAndInlineMetadataSurviveDeserialization() throws Exception {
        var dependency = read("""
                {"modId":"voile-id","modTitle":"Voile","imageUrl":"https://cdn.test/voile.png",
                 "versionNumber":"1.10.2","isOptional":true}
                """);
        assertEquals("voile-id", ProjectDependencyMetadata.lookupKey(dependency));
        assertEquals("Voile", ProjectDependencyMetadata.title(dependency, null));
        assertEquals("https://cdn.test/voile.png", ProjectDependencyMetadata.icon(dependency, null));
        assertTrue(dependency.isOptional());
        assertFalse(dependency.isExternal());
    }

    @Test
    void slugOnlyReferenceResolvesWithoutUsingDependencyRowId() throws Exception {
        var dependency = read("""
                {"id":"dependency-row-id","slug":"hexcode","versionNumber":"0.8.6-Beta"}
                """);
        assertEquals("hexcode", ProjectDependencyMetadata.lookupKey(dependency));
        var meta = meta("Hexcode");
        assertEquals("Hexcode", ProjectDependencyMetadata.title(dependency, meta));
        assertEquals("https://cdn.test/Hexcode.png", ProjectDependencyMetadata.icon(dependency, meta));
    }

    @Test
    void curseForgeUsesProviderKeyAndKeepsExternalIcons() throws Exception {
        for (String json : List.of(
                "{\"source\":\" CURSEFORGE \",\"externalId\":\"1234\"}",
                "{\"projectId\":\"curseforge:1234\"}")) {
            var dependency = read(json);
            assertTrue(dependency.isExternal());
            assertTrue(dependency.isCurseForge());
            assertEquals("curseforge:1234", ProjectDependencyMetadata.lookupKey(dependency));
            assertEquals("Dynamic Tooltips", ProjectDependencyMetadata.title(dependency, meta("Dynamic Tooltips")));
            assertFalse(ProjectDependencyMetadata.icon(dependency, meta("Dynamic Tooltips")).isBlank());
        }
    }

    @Test
    void otherProvidersKeepInlineMetadataWithoutSendingExternalIdsToModtale() throws Exception {
        var dependency = read("""
                {"source":"GITHUB","projectId":"github:owner/repo","projectTitle":"Library",
                 "icon":"https://cdn.test/library.png","isEmbedded":true}
                """);
        assertEquals("", ProjectDependencyMetadata.lookupKey(dependency));
        assertEquals("Library", ProjectDependencyMetadata.title(dependency, null));
        assertEquals("https://cdn.test/library.png", ProjectDependencyMetadata.icon(dependency, null));
        assertTrue(dependency.isEmbedded());
        assertEquals("", ProjectDependencyMetadata.lookupKey(read("{\"source\":\"CURSEFORGE\",\"externalId\":\"invalid\"}")));
    }

    @Test
    void partialBatchFallsBackPerReferenceAndRetainsSuccessfulMetadata() {
        List<String> singles = new ArrayList<>();
        var result = ProjectDependencyMetadata.load(List.of("id", "hexcode", "missing"),
                keys -> Map.of("id", meta("Voile")), key -> {
                    singles.add(key);
                    if (key.equals("missing")) throw new IllegalStateException("Unavailable");
                    return meta("Hexcode");
                });
        assertEquals(List.of("hexcode", "missing"), singles);
        assertEquals("Voile", result.get("id").title());
        assertEquals("Hexcode", result.get("hexcode").title());
        assertFalse(result.containsKey("missing"));
        assertEquals("Voile", ProjectDependencyMetadata.load(List.of("id"), keys -> {
            throw new IllegalStateException("Batch unavailable");
        }, key -> meta("Voile")).get("id").title());
    }

    @Test
    void liveApiShapeWithoutIdentityCannotBeMistakenForAnExternalProject() throws Exception {
        var dependency = read("""
                {"versionNumber":"1.10.2","isOptional":false,"isEmbedded":false}
                """);
        assertEquals("", ProjectDependencyMetadata.lookupKey(dependency));
        assertEquals("Unknown dependency", ProjectDependencyMetadata.title(dependency, null));
        assertFalse(dependency.isExternal());
        assertEquals("", ProjectDependencyMetadata.icon(dependency, null));
    }

    private ProjectDependency read(String json) throws Exception {
        return mapper.readValue(json, ProjectDependency.class);
    }

    private static ProjectMeta meta(String title) {
        return new ProjectMeta(title, "", "https://cdn.test/" + title + ".png", "", "PLUGIN", 0, "", "");
    }
}
