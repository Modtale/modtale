package net.modtale.service.project.version;

import net.modtale.exception.InvalidVersionRequestException;
import net.modtale.model.dto.project.ExternalProjectReferenceDTO;
import net.modtale.model.project.ProjectDependency;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExternalProjectReferenceServiceTest {

    private final CurseForgeApiClient apiClient = mock(CurseForgeApiClient.class);
    private final ExternalProjectReferenceService service = new ExternalProjectReferenceService(apiClient);

    @Test
    void resolvesSpecificCurseForgeFileWithoutCallingAnUndocumentedApi() {
        ExternalProjectReferenceDTO result = service.resolve(
                "https://www.curseforge.com/hytale/mods/simple-compost/files/8227810",
                ProjectDependency.Source.CURSEFORGE
        );

        assertEquals(ProjectDependency.Source.CURSEFORGE, result.source());
        assertEquals("simple-compost", result.externalId());
        assertEquals("Simple Compost", result.title());
        assertEquals("8227810", result.versionNumber());
        assertEquals("https://www.curseforge.com/hytale/mods/simple-compost/files/8227810", result.externalUrl());
        assertTrue(result.hytaleProjectConfirmed());
        assertEquals(1, result.files().size());
        assertEquals("8227810", result.files().getFirst().id());
        assertNull(result.files().getFirst().downloadUrl());
    }

    @Test
    void preservesNumericProjectIdWhenCurseForgeIncludesIt() {
        ExternalProjectReferenceDTO result = service.resolve(
                "https://www.curseforge.com/hytale/mods/simple-compost/files/8227810?projectId=1450386&utm_source=test",
                null
        );

        assertEquals("1450386", result.externalId());
        assertFalse(result.externalUrl().contains("utm_source"));
    }

    @Test
    void projectPageResolvesAsAReferenceButHasNoInventedFile() {
        ExternalProjectReferenceDTO result = service.resolve(
                "https://www.curseforge.com/hytale/mods/simple-compost",
                ProjectDependency.Source.CURSEFORGE
        );

        assertEquals("latest", result.versionNumber());
        assertTrue(result.files().isEmpty());
    }

    @Test
    void rejectsLookalikeHostsAndNonHytaleCurseForgePages() {
        assertThrows(InvalidVersionRequestException.class, () -> service.resolve(
                "https://curseforge.com.evil.example/hytale/mods/simple-compost/files/1",
                ProjectDependency.Source.CURSEFORGE
        ));
        assertThrows(InvalidVersionRequestException.class, () -> service.resolve(
                "https://www.curseforge.com/minecraft/mc-mods/simple-compost/files/1",
                ProjectDependency.Source.CURSEFORGE
        ));
        assertThrows(InvalidVersionRequestException.class, () -> service.resolve(
                "http://www.curseforge.com/hytale/mods/simple-compost/files/1",
                ProjectDependency.Source.CURSEFORGE
        ));
        assertThrows(InvalidVersionRequestException.class, () -> service.resolve(
                "https://attacker@www.curseforge.com/hytale/mods/simple-compost/files/1",
                ProjectDependency.Source.CURSEFORGE
        ));
        assertThrows(InvalidVersionRequestException.class, () -> service.resolve(
                "https://www.curseforge.com:444/hytale/mods/simple-compost/files/1",
                ProjectDependency.Source.CURSEFORGE
        ));
        assertThrows(InvalidVersionRequestException.class, () -> service.resolve(
                "https://www.curseforge.com/hytale/mods/simple-compost/files/not-a-number",
                ProjectDependency.Source.CURSEFORGE
        ));
    }

    @Test
    void rejectsMalformedUrlsWithoutMakingNetworkRequests() {
        assertThrows(InvalidVersionRequestException.class, () -> service.resolve(
                "not a URL",
                ProjectDependency.Source.CURSEFORGE
        ));
    }

    @Test
    void enrichesAStableReferenceFromTheDocumentedApiWhenConfigured() {
        CurseForgeApiClient apiClient = mock(CurseForgeApiClient.class);
        when(apiClient.resolveProject("simple-compost", "8227810")).thenReturn(java.util.Optional.of(
                new CurseForgeApiClient.CurseForgeProject(
                        "1450386", "simple-compost", "SimpleCompost", "Compost all the things", "https://example.test/icon.png", true,
                        List.of(new CurseForgeApiClient.CurseForgeFile(
                                "8227810", "SimpleCompost 1.0.0", "SimpleCompost-1.0.0.jar", "1.0.0", "RELEASE", "2026-08-01T00:00:00Z",
                                2048L, java.util.Map.of("sha1", "a".repeat(40)), List.of("2026.08"), 4, true
                        ))
                )
        ));

        ExternalProjectReferenceDTO result = new ExternalProjectReferenceService(apiClient).resolve(
                "https://www.curseforge.com/hytale/mods/simple-compost/files/8227810",
                ProjectDependency.Source.CURSEFORGE
        );

        assertEquals("1450386", result.externalId());
        assertEquals("SimpleCompost", result.title());
        assertEquals("1.0.0", result.versionNumber());
        assertEquals("SimpleCompost-1.0.0.jar", result.files().getFirst().fileName());
        assertEquals(2048L, result.files().getFirst().fileSize());
        assertEquals("a".repeat(40), result.files().getFirst().hashes().get("sha1"));
        assertEquals(true, result.distributionAllowed());
        assertNull(result.files().getFirst().downloadUrl());
    }
}
