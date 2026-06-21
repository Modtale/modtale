package net.modtale.model.dto.project;

import java.util.List;
import net.modtale.model.project.Project;

public record ProjectTeamDTO(
        List<Project.ProjectRole> projectRoles,
        List<Project.ProjectMember> teamMembers,
        List<Project.ProjectMember> teamInvites
) {
}
