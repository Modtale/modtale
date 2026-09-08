package net.modtale.service.project.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;
import java.util.Map;
import net.modtale.model.dto.project.ArtifactIdentityDTO;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectVersion;
import net.modtale.repository.project.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArtifactIdentityServiceTest {
    private ProjectRepository repository;
    private CurseForgeApiClient curseForge;
    private ArtifactIdentityService service;

    @BeforeEach
    void setUp() {
        repository = mock(ProjectRepository.class);
        curseForge = mock(CurseForgeApiClient.class);
        when(repository.findPublishedIdentityIndex()).thenReturn(List.of());
        when(repository.findPublishedByVersionHashes(anyList())).thenReturn(List.of());
        when(repository.findPublishedByManifestIds(anyList())).thenReturn(List.of());
        when(repository.findPublishedByCurseForgeFingerprints(anyList())).thenReturn(List.of());
        when(curseForge.matchArtifacts(anyList())).thenReturn(Map.of());
        service = new ArtifactIdentityService(repository, curseForge);
    }

    @Test
    void exactSha256WinsAndCarriesTheInstalledVersionId() {
        Project project = project("p1", "real-mod", "Real Mod");
        ProjectVersion version = version("v1", "1.2.3", "a".repeat(64), "author:real", 123L);
        project.setVersions(List.of(version));
        when(repository.findPublishedByVersionHashes(anyList())).thenReturn(List.of(project));

        ArtifactIdentityDTO.Response response = service.identify(request("a".repeat(64), 123L, "author:real", ""));

        assertEquals(1, response.matches().size());
        assertEquals("p1", response.matches().getFirst().projectId());
        assertEquals("v1", response.matches().getFirst().versionId());
        assertEquals("sha256", response.matches().getFirst().evidence());
    }

    @Test
    void duplicateManifestIdsAreTreatedAsAmbiguous() {
        Project one = project("p1", "one", "Same");
        Project two = project("p2", "two", "Same");
        one.setVersions(List.of(version("v1", "1", null, "author:same", null)));
        two.setVersions(List.of(version("v2", "1", null, "author:same", null)));
        when(repository.findPublishedByManifestIds(anyList())).thenReturn(List.of(one, two));
        assertTrue(service.identify(request(null, null, "author:same", "")).matches().isEmpty());
    }

    @Test
    void removesCurseForgeCatalogDuplicateByExactBinaryFingerprint() {
        Project project = project("p1", "real-mod", "Real Mod");
        project.setVersions(List.of(version("v1", "1", null, "author:real", 777L)));
        when(repository.findPublishedIdentityIndex()).thenReturn(List.of(project));
        CurseForgeApiClient.CurseForgeFile file = new CurseForgeApiClient.CurseForgeFile("9", "1", "mod.jar", "1",
                "RELEASE", null, 2L, Map.of(), List.of(), 4, true, 2, 777L);
        CurseForgeApiClient.CurseForgeProject cf = new CurseForgeApiClient.CurseForgeProject("42", "real-mod", "Real Mod",
                "", "", true, List.of(file));
        CurseForgeApiClient.CurseForgeSearchResult filtered = service.removeModtaleAliases(
                new CurseForgeApiClient.CurseForgeSearchResult(List.of(cf), 0, 20, 1));
        assertTrue(filtered.projects().isEmpty());
        assertEquals(0, filtered.totalCount());
    }

    private static ArtifactIdentityDTO.Request request(String hash, Long fingerprint, String manifest, String website) {
        return new ArtifactIdentityDTO.Request(List.of(new ArtifactIdentityDTO.Artifact("file.jar", hash, fingerprint,
                manifest, "1.2.3", website)));
    }

    private static Project project(String id, String slug, String title) {
        Project project = new Project();
        project.setId(id); project.setSlug(slug); project.setTitle(title); project.setClassification(ProjectClassification.PLUGIN);
        return project;
    }

    private static ProjectVersion version(String id, String number, String hash, String manifest, Long fingerprint) {
        ProjectVersion version = new ProjectVersion();
        version.setId(id); version.setVersionNumber(number); version.setHash(hash); version.setManifestId(manifest);
        version.setManifestVersion(number); version.setCurseForgeFingerprint(fingerprint);
        return version;
    }
}
