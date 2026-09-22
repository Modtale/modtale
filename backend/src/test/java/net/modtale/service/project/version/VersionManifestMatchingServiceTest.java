package net.modtale.service.project.version;

import java.util.List;
import net.modtale.model.dto.project.ManifestDependencySuggestion;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.service.security.validation.FileValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VersionManifestMatchingServiceTest {

    private VersionManifestMatchingService service;

    @BeforeEach
    void setUp() {
        service = new VersionManifestMatchingService();
    }

    @Test
    void resolveManifestGameVersionHandlesExactMatchesRangesCaretAndInvalidInputs() {
        List<String> allowed = List.of("1.20.1", "1.20.4", "1.21.0", "2.0.0");

        assertEquals("1.20.4", service.resolveManifestGameVersion("1.20.4", allowed));
        assertEquals("1.20.4", service.resolveManifestGameVersion(">=1.20.0 <1.21.0", allowed));
        assertEquals("1.20.1", service.resolveManifestGameVersion(">=1.20.0", allowed));
        assertEquals("1.21.0", service.resolveManifestGameVersion("^1.20.0", allowed));
        assertNull(service.resolveManifestGameVersion("latest", allowed));
        assertNull(service.resolveManifestGameVersion("1.20.1", List.of()));
    }

    @Test
    void resolveManifestGameVersionFallsBackToPrefixWhenNoCandidateSatisfiesParsedConstraint() {
        assertEquals(
                "1.20.1-custom",
                service.resolveManifestGameVersion("=1.20.1", List.of("1.20.1-custom", "1.19.4"))
        );
    }

    @Test
    void suggestDependenciesPicksBestCandidateAndExactRequestedVersion() {
        Project exact = project("project-1", "Sky Library", "sky-library",
                version("1.0.0", "2026-01-01"),
                version("2.0.0", "2026-02-01"));
        Project weaker = project("project-2", "Sky Lib Fork", "sky-lib-fork", version("2.0.0", "2026-02-01"));

        List<ManifestDependencySuggestion> suggestions = service.suggestDependencies(
                List.of(new FileValidationService.ManifestDependency("com.example:sky-library", "2.0.0", true)),
                List.of(weaker, exact)
        );

        assertEquals(1, suggestions.size());
        ManifestDependencySuggestion suggestion = suggestions.getFirst();
        assertEquals("com.example:sky-library", suggestion.getManifestKey());
        assertEquals("project-1", suggestion.getProjectId());
        assertEquals("2.0.0", suggestion.getVersionNumber());
        assertTrue(suggestion.isOptional());
        assertEquals(100, suggestion.getConfidence());
    }

    @Test
    void suggestDependenciesUsesLatestReleaseDateWhenRequestedVersionIsRangeOrWildcard() {
        Project project = project("project-1", "Core Utils", "core-utils",
                version("1.0.0", "2026-01-01"),
                version("1.1.0", "2026-03-01"),
                version("0.9.0", null));

        List<ManifestDependencySuggestion> suggestions = service.suggestDependencies(
                List.of(new FileValidationService.ManifestDependency("core-utils", ">=1.0.0", false)),
                List.of(project)
        );

        assertEquals("1.1.0", suggestions.getFirst().getVersionNumber());
        assertEquals("project-1", suggestions.getFirst().getProjectId());
    }

    @Test
    void suggestDependenciesSkipsWeakMatchesAndProjectsWithoutVersions() {
        Project weak = project("project-1", "Tiny", "tiny", version("1.0.0", "2026-01-01"));
        Project noVersions = project("project-2", "Big Dependency", "big-dependency");

        List<ManifestDependencySuggestion> suggestions = service.suggestDependencies(
                List.of(
                        new FileValidationService.ManifestDependency("different-package", "1.0.0", false),
                        new FileValidationService.ManifestDependency("big-dependency", "1.0.0", false)
                ),
                List.of(weak, noVersions)
        );

        assertTrue(suggestions.isEmpty());
    }

    private static Project project(String id, String title, String slug, ProjectVersion... versions) {
        Project project = new Project();
        project.setId(id);
        project.setTitle(title);
        project.setSlug(slug);
        project.setVersions(List.of(versions));
        return project;
    }

    private static ProjectVersion version(String versionNumber, String releaseDate) {
        ProjectVersion version = new ProjectVersion();
        version.setVersionNumber(versionNumber);
        version.setReleaseDate(releaseDate);
        return version;
    }
}
