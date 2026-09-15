package net.modtale.service.user.organization;
import java.util.*;
import java.util.stream.Collectors;
import net.modtale.model.user.User;
import net.modtale.exception.InvalidOrganizationRequestException;

public final class OrganizationInvitationPolicy {
    private OrganizationInvitationPolicy() {}
    public static Set<String> owners(User org) {
        if (org.getOrganizationRoles() == null || org.getOrganizationMembers() == null) return Set.of();
        var ownerRoles = org.getOrganizationRoles().stream().filter(User.OrganizationRole::isOwner).map(User.OrganizationRole::getId).filter(id -> id != null && !id.isBlank()).collect(Collectors.toSet());
        return org.getOrganizationMembers().stream().filter(member -> ownerRoles.contains(member.getRoleId()))
                .map(User.OrganizationMember::getUserId).filter(Objects::nonNull).collect(Collectors.toSet());
    }
    public static User.OrganizationMember require(User org, String userId, String requestId, boolean accepting) {
        var matches = org.getPendingOrgInvites() == null ? List.<User.OrganizationMember>of() : org.getPendingOrgInvites().stream()
                .filter(invite -> Objects.equals(userId, invite.getUserId())).toList();
        if (matches.size() != 1 || requestId == null || requestId.isBlank()) throw invalid();
        var invite = matches.getFirst();
        if (!("legacy".equals(requestId) && invite.getRequestId() == null && !accepting) && !requestId.equals(invite.getRequestId())) throw invalid();
        if (accepting) {
            if (invite.getRequestExpiresAt() <= System.currentTimeMillis() || invite.getRequestPermissions() == null
                    || invite.getRequestOwnerIds() == null || invite.getRequestOwnerIds().isEmpty()
                    || !invite.getRequestOwnerIds().equals(owners(org))) throw invalid();
            var roles = org.getOrganizationRoles() == null ? List.<User.OrganizationRole>of() : org.getOrganizationRoles().stream()
                    .filter(role -> Objects.equals(invite.getRoleId(), role.getId())).toList();
            if (roles.size() != 1 || roles.getFirst().isOwner() || !invite.getRequestPermissions().equals(
                    roles.getFirst().getPermissions() == null ? Set.of() : roles.getFirst().getPermissions())) throw invalid();
            if (org.getOrganizationMembers() != null && org.getOrganizationMembers().stream().anyMatch(member -> Objects.equals(userId, member.getUserId()))) throw invalid();
        }
        return invite;
    }
    private static InvalidOrganizationRequestException invalid() {
        return new InvalidOrganizationRequestException("This invitation is no longer current. Ask the organization owner to send a new invitation.");
    }
}
