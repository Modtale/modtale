package net.modtale.service.project.version;

import java.net.http.HttpClient;
import java.util.List;
import net.modtale.exception.InvalidVersionRequestException;
import net.modtale.model.project.ProjectDependency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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
