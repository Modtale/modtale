package net.modtale.service.project.version;

import java.net.http.HttpClient;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.modtale.exception.InvalidVersionRequestException;
import net.modtale.model.project.ProjectDependency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ExternalDependencyArtifactServiceTest {

    private HttpClient httpClient;
    private ExternalDependencyArtifactService service;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        service = new ExternalDependencyArtifactService(httpClient);
    }

    @Test
    void curseForgeDependencyStaysAReferenceAndClearsLegacyCachedArtifact() {
        ProjectDependency dependency = curseForgeDependency(
                "https://www.curseforge.com/hytale/mods/simple-compost/files/8227810"
        );
        dependency.setCachedFileUrl("external-dependencies/curseforge/1450386/8227810/mod.jar");

        service.prepareExternalArtifacts(List.of(dependency));

        assertEquals("https://www.curseforge.com/hytale/mods/simple-compost/files/8227810", dependency.getExternalUrl());
        assertEquals("https://www.curseforge.com/hytale/mods/simple-compost/files/8227810", dependency.getExternalFileUrl());
        assertEquals("SimpleCompost-1.0.0.jar", dependency.getExternalFileName());
        assertNull(dependency.getCachedFileUrl());
        verifyNoInteractions(httpClient);
    }

    @Test
    void acceptsAFilePageSuppliedSeparatelyFromTheProjectPage() {
        ProjectDependency dependency = curseForgeDependency(
                "https://www.curseforge.com/hytale/mods/simple-compost"
        );
        dependency.setExternalFileUrl("https://www.curseforge.com/hytale/mods/simple-compost/files/8227810");

        service.prepareExternalArtifacts(List.of(dependency));

        assertEquals("https://www.curseforge.com/hytale/mods/simple-compost/files/8227810", dependency.getExternalFileUrl());
        verifyNoInteractions(httpClient);
    }

    @Test
    void rejectsCurseForgeProjectReferencesWithoutASpecificFile() {
        ProjectDependency dependency = curseForgeDependency(
                "https://www.curseforge.com/hytale/mods/simple-compost"
        );

        assertThrows(InvalidVersionRequestException.class, () -> service.prepareExternalArtifacts(List.of(dependency)));
        verifyNoInteractions(httpClient);
    }

    @Test
    void rejectsLookalikeAndNonHytaleFilePages() {
        ProjectDependency lookalike = curseForgeDependency(
                "https://curseforge.com.evil.example/hytale/mods/simple-compost/files/8227810"
        );
        ProjectDependency wrongGame = curseForgeDependency(
                "https://www.curseforge.com/minecraft/mc-mods/simple-compost/files/8227810"
        );

        assertThrows(InvalidVersionRequestException.class, () -> service.prepareExternalArtifacts(List.of(lookalike)));
        assertThrows(InvalidVersionRequestException.class, () -> service.prepareExternalArtifacts(List.of(wrongGame)));
        verifyNoInteractions(httpClient);
    }

    @Test
    void replacesClientMetadataWithTheDocumentedApiSnapshotWhenConfigured() {
        CurseForgeApiClient apiClient = mock(CurseForgeApiClient.class);
        when(apiClient.isConfigured()).thenReturn(true);
        when(apiClient.resolveProject("simple-compost", "8227810")).thenReturn(Optional.of(
                new CurseForgeApiClient.CurseForgeProject(
                        "1450386", "simple-compost", "Simple Compost", null, null, false,
                        List.of(new CurseForgeApiClient.CurseForgeFile(
                                "8227810", "Simple Compost 1.1.0", "SimpleCompost-1.1.0.jar", "1.1.0",
                                "RELEASE", "2026-09-01T00:00:00Z", 4096L,
                                Map.of("sha1", "a".repeat(40)), List.of("2026.09"), 4, true
                        ))
                )
        ));
        ProjectDependency dependency = curseForgeDependency(
                "https://www.curseforge.com/hytale/mods/simple-compost/files/8227810"
        );
        dependency.setExternalFileSize(1L);
        dependency.setExternalFileHashes(Map.of("sha1", "b".repeat(40)));

        new ExternalDependencyArtifactService(apiClient, httpClient).prepareExternalArtifacts(List.of(dependency));

        assertEquals("1.1.0", dependency.getVersionNumber());
        assertEquals("SimpleCompost-1.1.0.jar", dependency.getExternalFileName());
        assertEquals(4096L, dependency.getExternalFileSize());
        assertEquals("a".repeat(40), dependency.getExternalFileHashes().get("sha1"));
        assertEquals(List.of("2026.09"), dependency.getExternalGameVersions());
        assertEquals(4, dependency.getExternalFileStatus());
        assertEquals(false, dependency.getExternalDistributionAllowed());
        verifyNoInteractions(httpClient);
    }

    @Test
    void preservesTheCanonicalReferenceButDiscardsClientMetadataWhenTheApiIsUnavailable() {
        CurseForgeApiClient apiClient = mock(CurseForgeApiClient.class);
        when(apiClient.isConfigured()).thenReturn(true);
        when(apiClient.resolveProject("simple-compost", "8227810")).thenReturn(Optional.empty());
        ProjectDependency dependency = curseForgeDependency(
                "https://www.curseforge.com/hytale/mods/simple-compost/files/8227810"
        );
        dependency.setExternalFileSize(1L);
        dependency.setExternalFileHashes(Map.of("sha1", "b".repeat(40)));

        new ExternalDependencyArtifactService(apiClient, httpClient).prepareExternalArtifacts(List.of(dependency));

        assertEquals("https://www.curseforge.com/hytale/mods/simple-compost/files/8227810", dependency.getExternalFileUrl());
        assertNull(dependency.getExternalFileSize());
        assertNull(dependency.getExternalFileHashes());
        verifyNoInteractions(httpClient);
    }

    private static ProjectDependency curseForgeDependency(String url) {
        ProjectDependency dependency = ProjectDependency.curseForge(
                "1450386",
                "Simple Compost",
                "1.0.0",
                url,
                ProjectDependency.DependencyType.REQUIRED
        );
        dependency.setExternalFileName("SimpleCompost-1.0.0.jar");
        return dependency;
    }
}
