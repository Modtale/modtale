package net.modtale.service.project;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ValidationServiceTest {

    private final ValidationService validationService = new ValidationService();

    @Test
    void validateTagsNormalizesKnownTagsAndRejectsUnknownOnes() {
        assertIterableEquals(List.of("Adventure", "Admin Tools"), validationService.validateTags(List.of("adventure", "admin tools")));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateTags(List.of("adventure", "not-real"))
        );

        assertEquals("Invalid tags detected: not-real", error.getMessage());
    }

    @Test
    void validateVersionNumberAcceptsSemverAndRejectsInvalidFormats() {
        assertDoesNotThrow(() -> validationService.validateVersionNumber("1.2.3-rc.1+build.5"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateVersionNumber("1.2")
        );

        assertEquals("Version number must follow SemVer format (e.g., 1.0.0, 1.0.0-rc.1, 1.0.0+build).", error.getMessage());
    }

    @Test
    void validateSlugEnforcesLowercaseDashSeparatedSlugsBetweenThreeAndFiftyCharacters() {
        assertDoesNotThrow(() -> validationService.validateSlug("sky-tools"));

        IllegalArgumentException tooShort = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateSlug("ab")
        );
        assertEquals("Invalid URL Slug. Must be 3-50 characters, lowercase alphanumeric with dashes, and cannot start or end with a dash.", tooShort.getMessage());

        assertThrows(IllegalArgumentException.class, () -> validationService.validateSlug("Bad-Slug"));
        assertThrows(IllegalArgumentException.class, () -> validationService.validateSlug("-starts-with-dash"));
    }

    @Test
    void validateRepositoryUrlAllowsSupportedHostsOnly() {
        assertDoesNotThrow(() -> validationService.validateRepositoryUrl("https://github.com/modtale/project"));
        assertDoesNotThrow(() -> validationService.validateRepositoryUrl("https://gitlab.com/modtale/project"));
        assertDoesNotThrow(() -> validationService.validateRepositoryUrl("https://codeberg.org/modtale/project"));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> validationService.validateRepositoryUrl("http://example.com/modtale/project")
        );

        assertEquals("Invalid Repository URL. Must be a valid HTTPS link to GitHub, GitLab, or Codeberg.", error.getMessage());
    }

    @Test
    void returnsAllowedVersionsAndClassifications() {
        GameVersionService gameVersionService = mock(GameVersionService.class);
        when(gameVersionService.getCatalog()).thenReturn(
                new GameVersionService.GameVersionCatalog(
                        List.of("1.1.0"),
                        List.of("1.2.0-pre.1"),
                        List.of("1.2.0-pre.1", "1.1.0"),
                        List.of()
                )
        );
        ReflectionTestUtils.setField(validationService, "gameVersionService", gameVersionService);

        assertIterableEquals(List.of("1.2.0-pre.1", "1.1.0"), validationService.getAllowedGameVersions());
        assertIterableEquals(List.of("1.1.0"), validationService.getAllowedReleaseGameVersions());
        assertIterableEquals(List.of("1.2.0-pre.1"), validationService.getAllowedPreReleaseGameVersions());
        assertIterableEquals(List.of("PLUGIN", "DATA", "ART", "SAVE", "MODPACK"), validationService.getAllowedClassifications());
    }
}
