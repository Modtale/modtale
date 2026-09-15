package net.modtale.service.project.team;

import java.util.*;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.model.project.Project;

public final class ProjectInvitationPolicy {
    private ProjectInvitationPolicy() {}
    public static Project.ProjectMember require(Project project, String userId, String requestId, boolean accepting) {
        var matches = project.getTeamInvites() == null ? List.<Project.ProjectMember>of() : project.getTeamInvites().stream()
                .filter(invite -> Objects.equals(userId, invite.getUserId())).toList();
        if (matches.size() != 1 || requestId == null || requestId.isBlank()) throw invalid();
        var invite = matches.getFirst();
        boolean legacyCancellation = !accepting && invite.getRequestId() == null && requestId.equals("legacy");
        if (!legacyCancellation && !requestId.equals(invite.getRequestId())) throw invalid();
        if (accepting) {
            if (invite.getRequestOwnerId() == null || !invite.getRequestOwnerId().equals(project.getAuthorId())
                    || invite.getRequestExpiresAt() <= System.currentTimeMillis() || invite.getRequestPermissions() == null) throw invalid();
            var roles = project.getProjectRoles() == null ? List.<Project.ProjectRole>of() : project.getProjectRoles().stream()
                    .filter(role -> Objects.equals(invite.getRoleId(), role.getId())).toList();
            if (roles.size() != 1 || !invite.getRequestPermissions().equals(
                    roles.getFirst().getPermissions() == null ? Set.of() : roles.getFirst().getPermissions())) throw invalid();
        }
        return invite;
    }
    private static InvalidProjectRequestException invalid() {
        return new InvalidProjectRequestException("This invitation is no longer current. Ask the project owner to send a new invitation.");
    }
}
