package net.modtale.service.jam;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import net.modtale.model.jam.Modjam;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.service.project.catalog.GameVersionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ModjamCustomizationServiceTest {
    private ModjamCustomizationService service;

    @BeforeEach
    void setUp() {
        GameVersionService versions = mock(GameVersionService.class);
        when(versions.getCatalog()).thenReturn(new GameVersionService.GameVersionCatalog(
                List.of("0.3.0", "0.2.0", "0.1.0"), List.of(), List.of("0.3.0", "0.2.0", "0.1.0"), List.of()));
        service = new ModjamCustomizationService(versions);
    }

    @Test
    void acceptsSafeMarkdownPresentationAndRejectsDocumentControlAndNetworkLoads() {
        assertDoesNotThrow(() -> ModjamCustomizationService.validateCss("h1, h2 { color: #aabbcc; margin: 1rem 0; } p { line-height: 1.6; }"));
        for (String css : List.of(
                "body { color: red; }", "h1 { background-color: url(https://example.test/track); }",
                "@import 'https://example.test/track';", "h1 { position: fixed; }",
                "h1 { color: var(--secret); }", "h1 { color: red !important; }",
                "h1 { color: red; } trailing", "h1 { color: red; } </style>")) {
            assertThrows(IllegalArgumentException.class, () -> ModjamCustomizationService.validateCss(css), css);
        }
    }

    @Test
    void validatesInclusiveRangeUsingCanonicalCatalogRatherThanLexicalOrder() {
        Modjam.Restrictions range = range("0.1.0", "0.2.0");
        assertDoesNotThrow(() -> service.validateProjectVersions(range, project("0.1.0", ProjectVersion.ReviewStatus.APPROVED)));
        assertDoesNotThrow(() -> service.validateProjectVersions(range, project("0.2.0", ProjectVersion.ReviewStatus.APPROVED)));
        assertThrows(IllegalArgumentException.class, () -> service.validateProjectVersions(range, project("0.3.0", ProjectVersion.ReviewStatus.APPROVED)));
        assertThrows(IllegalArgumentException.class, () -> service.validateVersionRestriction(range("0.3.0", "0.1.0")));
        assertThrows(IllegalArgumentException.class, () -> service.validateVersionRestriction(range("unknown", "0.2.0")));
    }

    @Test
    void rejectsMixedExactAndRangeAndNonDownloadableReleases() {
        Modjam.Restrictions restriction = new Modjam.Restrictions();
        restriction.setAllowedGameVersions(List.of("0.2.0"));
        for (ProjectVersion.ReviewStatus status : List.of(ProjectVersion.ReviewStatus.REJECTED, ProjectVersion.ReviewStatus.SCHEDULED, ProjectVersion.ReviewStatus.PENDING)) {
            assertThrows(IllegalArgumentException.class, () -> service.validateProjectVersions(restriction, project("0.2.0", status)));
        }
        Project draft = project("0.2.0", ProjectVersion.ReviewStatus.PENDING);
        draft.setStatus(ProjectStatus.DRAFT);
        assertDoesNotThrow(() -> service.validateProjectVersions(restriction, draft));
        restriction.setMinimumGameVersion("0.1.0");
        assertThrows(IllegalArgumentException.class, () -> service.validateVersionRestriction(restriction));
    }

    @Test
    void validatesSlugLengthAndCategoriesEvenForDrafts() {
        Modjam jam = new Modjam();
        jam.setTitle("New Jam");
        jam.setSlug("new-jam");
        assertDoesNotThrow(() -> service.validate(jam));
        jam.setSlug("a");
        assertThrows(IllegalArgumentException.class, () -> service.validate(jam));
        jam.setSlug("new-jam");
        jam.setCategories(List.of(new Modjam.Category("same", "Fun", "", 10), new Modjam.Category("same", "Style", "", 10)));
        assertThrows(IllegalArgumentException.class, () -> service.validate(jam));
        jam.setCategories(List.of(new Modjam.Category("id", "Fun", "", 0)));
        assertThrows(IllegalArgumentException.class, () -> service.validate(jam));
    }

    private static Modjam.Restrictions range(String minimum, String maximum) {
        Modjam.Restrictions range = new Modjam.Restrictions();
        range.setMinimumGameVersion(minimum);
        range.setMaximumGameVersion(maximum);
        return range;
    }

    private static Project project(String gameVersion, ProjectVersion.ReviewStatus status) {
        Project project = new Project();
        project.setStatus(ProjectStatus.PUBLISHED);
        ProjectVersion version = new ProjectVersion();
        version.setGameVersions(List.of(gameVersion));
        version.setReviewStatus(status);
        version.setFileUrl("/uploads/project.jar");
        project.setVersions(List.of(version));
        return project;
    }
}
