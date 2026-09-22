package net.modtale.service.project.version;

import java.util.List;
import net.modtale.service.project.lifecycle.ProjectDeletionService;
import net.modtale.service.project.validation.ValidationService;
import net.modtale.service.security.scan.ScanService;
import net.modtale.service.security.validation.SanitizationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VersionMutationOrchestrationServiceTest {

    @Test
    void checksTheLiveCatalogBeforeRejectingAnUnknownGameVersion() {
        ValidationService validationService = mock(ValidationService.class);
        when(validationService.getAllowedGameVersions()).thenReturn(List.of("0.5.4"));
        when(validationService.isGameVersionSupported("0.5.5")).thenReturn(true);
        VersionMutationOrchestrationService service = service(validationService);

        assertDoesNotThrow(() -> service.validateGameVersions(List.of("0.5.5")));
        verify(validationService).isGameVersionSupported("0.5.5");
    }

    @Test
    void rejectsAnUnknownGameVersionWhenTheLiveCatalogDoesNotContainIt() {
        ValidationService validationService = mock(ValidationService.class);
        when(validationService.getAllowedGameVersions()).thenReturn(List.of("0.5.4"));
        when(validationService.isGameVersionSupported("0.7.0")).thenReturn(false);
        VersionMutationOrchestrationService service = service(validationService);

        assertThrows(IllegalArgumentException.class, () -> service.validateGameVersions(List.of("0.7.0")));
        verify(validationService).isGameVersionSupported("0.7.0");
    }

    private static VersionMutationOrchestrationService service(ValidationService validationService) {
        return new VersionMutationOrchestrationService(
                validationService,
                mock(ScanService.class),
                mock(SanitizationService.class),
                mock(VersionArtifactService.class),
                mock(VersionDependencyService.class),
                mock(ProjectDeletionService.class)
        );
    }
}
