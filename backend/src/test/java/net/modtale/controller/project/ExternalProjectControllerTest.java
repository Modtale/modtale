package net.modtale.controller.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import net.modtale.service.project.version.CurseForgeApiClient;
import net.modtale.model.dto.project.CurseForgeCommentsDTO;
import net.modtale.service.project.version.ExternalProjectReferenceService;
import org.junit.jupiter.api.Test;

class ExternalProjectControllerTest {

    @Test
    void exposesNormalizedUncachedCurseForgeBrowseAndDetailResponses() {
        CurseForgeApiClient api = mock(CurseForgeApiClient.class);
        CurseForgeApiClient.CurseForgeFile file = new CurseForgeApiClient.CurseForgeFile(
                "8227810", "Simple Compost 1.2", "SimpleCompost.jar", "1.2", "RELEASE",
                "2026-09-01T00:00:00Z", 2048L, Map.of("sha1", "a".repeat(40)),
                List.of("2026.09"), 4, true, 12);
        CurseForgeApiClient.CurseForgeProject project = new CurseForgeApiClient.CurseForgeProject(
                "1450386", "simple-compost", "Simple Compost", "Compost things", "https://img.example/icon.png",
                true, List.of(file), "https://www.curseforge.com/hytale/mods/simple-compost",
                List.of("Builder"), List.of("Gameplay"), List.of("https://img.example/shot.png"),
                "2026-09-01T00:00:00Z", 321, "<p>Full description</p>");
        when(api.searchMods("compost", "2026.09", 0, 20, "downloads"))
                .thenReturn(new CurseForgeApiClient.CurseForgeSearchResult(List.of(project), 0, 20, 1));
        when(api.getProject(1450386)).thenReturn(java.util.Optional.of(project));
        net.modtale.service.project.version.ArtifactIdentityService identities =
                mock(net.modtale.service.project.version.ArtifactIdentityService.class);
        when(identities.removeModtaleAliases(org.mockito.ArgumentMatchers.any())).thenAnswer(call -> call.getArgument(0));
        ExternalProjectController controller = new ExternalProjectController(
                mock(ExternalProjectReferenceService.class), api, identities);

        var browse = controller.browseCurseForge("compost", "2026.09", 0, 20, "downloads");
        var detail = controller.getCurseForgeProject(1450386);

        assertEquals("no-store", browse.getHeaders().getCacheControl());
        assertEquals("curseforge:1450386", browse.getBody().content().getFirst().id());
        assertEquals("CURSEFORGE", browse.getBody().content().getFirst().source());
        assertEquals("8227810", browse.getBody().content().getFirst().versions().getFirst().id());
        assertTrue(detail.getBody().distributionAllowed());
        assertEquals(List.of("Gameplay"), detail.getBody().tags());
    }

    @Test
    void exposesOnlyTheProviderResolvedDownloadAndIntegrityMetadata() {
        CurseForgeApiClient api = mock(CurseForgeApiClient.class);
        when(api.getDownload(1450386, 8227810)).thenReturn(java.util.Optional.of(
                new CurseForgeApiClient.CurseForgeDownload(
                        "https://mediafilez.forgecdn.net/files/8227/810/SimpleCompost.jar",
                        "SimpleCompost.jar", 2048L, Map.of("sha1", "a".repeat(40)))));
        ExternalProjectController controller = new ExternalProjectController(
                mock(ExternalProjectReferenceService.class), api,
                mock(net.modtale.service.project.version.ArtifactIdentityService.class));

        var response = controller.getCurseForgeDownload(1450386, 8227810);

        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals("CURSEFORGE", response.getBody().source());
        assertEquals("a".repeat(40), response.getBody().hashes().get("sha1"));
    }

    @Test
    void exposesReadOnlyCurseForgeCommentsWithoutLocalDiscussionState() {
        CurseForgeApiClient api = mock(CurseForgeApiClient.class);
        when(api.getComments(1450386)).thenReturn(new CurseForgeCommentsDTO(List.of(
                new CurseForgeCommentsDTO.Comment("curseforge:7", "Builder",
                        new CurseForgeCommentsDTO.Author("Builder", null), "Pulled from CurseForge",
                        "2026-09-01T00:00:00Z", false, true, List.of())), 1));
        ExternalProjectController controller = new ExternalProjectController(
                mock(ExternalProjectReferenceService.class), api,
                mock(net.modtale.service.project.version.ArtifactIdentityService.class));

        var response = controller.getCurseForgeComments(1450386);

        assertEquals("no-store", response.getHeaders().getCacheControl());
        assertEquals("Pulled from CurseForge", response.getBody().comments().getFirst().content());
        assertTrue(response.getBody().comments().getFirst().readOnly());
    }
}
