package net.modtale.service.project.access;

import java.util.List;
import net.modtale.exception.VersionNotFoundException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.service.project.validation.ValidationService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class ProjectVersionAccessServiceTest {

    private final ProjectVersionAccessService service =
            new ProjectVersionAccessService(mock(ValidationService.class));

    @Test
    void findsAProjectVersionByHashCaseInsensitively() {
        ProjectVersion version = new ProjectVersion();
        version.setId("version-1");
        version.setHash("abcdef1234");
        Project project = new Project();
        project.setVersions(List.of(version));

        assertEquals(version, service.findByHash(project, "  ABCDEF1234  "));
    }

    @Test
    void rejectsAHashThatDoesNotBelongToTheProject() {
        Project project = new Project();
        project.setVersions(List.of());

        assertThrows(VersionNotFoundException.class, () -> service.requireByHash(
                project,
                "missing",
                () -> new VersionNotFoundException("missing")
        ));
    }
}
