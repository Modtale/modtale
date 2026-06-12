package net.modtale.service.project.access;

import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import org.springframework.stereotype.Service;

@Service
public class ProjectMutationGuard {

    public void ensureEditable(Project project) {
        if (project.getStatus() == ProjectStatus.PENDING) {
            throw new InvalidProjectRequestException("Pending projects cannot be modified. Revert to draft first.");
        }
        if (project.getStatus() == ProjectStatus.ARCHIVED) {
            throw new InvalidProjectRequestException("Archived projects are read-only.");
        }
    }
}
