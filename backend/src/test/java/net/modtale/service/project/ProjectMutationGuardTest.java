package net.modtale.service.project;

import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProjectMutationGuardTest {

    private final ProjectMutationGuard projectMutationGuard = new ProjectMutationGuard();

    @Test
    void ensureEditableRejectsPendingAndArchivedProjects() {
        Project pending = project(ProjectStatus.PENDING);
        Project archived = project(ProjectStatus.ARCHIVED);

        InvalidProjectRequestException pendingError = assertThrows(
                InvalidProjectRequestException.class,
                () -> projectMutationGuard.ensureEditable(pending)
        );
        InvalidProjectRequestException archivedError = assertThrows(
                InvalidProjectRequestException.class,
                () -> projectMutationGuard.ensureEditable(archived)
        );

        assertEquals("Pending projects cannot be modified. Revert to draft first.", pendingError.getMessage());
        assertEquals("Archived projects are read-only.", archivedError.getMessage());
    }

    private static Project project(ProjectStatus status) {
        Project project = new Project();
        project.setStatus(status);
        return project;
    }
}
