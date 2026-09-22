package net.modtale.service.user.organization;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.modtale.exception.InvalidOrganizationRequestException;
import net.modtale.exception.OrganizationOperationForbiddenException;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.NotificationType;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.communication.NotificationService;
import org.springframework.stereotype.Service;

@Service
public class OrganizationInviteService {

    private final OrganizationInvitePersistence persistence;
    private final UserRepository userRepository;
    private final OrganizationAccessService organizationAccessService;
    private final NotificationService notificationService;

    public OrganizationInviteService(
            UserRepository userRepository,
            OrganizationAccessService organizationAccessService,
            NotificationService notificationService,
            OrganizationInvitePersistence persistence
    ) {
        this.persistence = persistence;
        this.userRepository = userRepository;
        this.organizationAccessService = organizationAccessService;
        this.notificationService = notificationService;
    }

    public void inviteOrganizationMember(String orgId, String targetUserId, String roleId, User requester) {
        var snapshot = persistence.capture(orgId);
        User org = snapshot.organization();
        organizationAccessService.requireOrgPermission(
                org,
                requester,
                ApiKey.ApiPermission.ORG_MEMBER_INVITE,
                "You do not have permission to invite members to this organization."
        );

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new InvalidOrganizationRequestException("We couldn't find the user you tried to invite."));
        if (org.getOrganizationMembers() != null && org.getOrganizationMembers().stream().anyMatch(member -> member.getUserId().equals(target.getId()))) {
            throw new InvalidOrganizationRequestException("That user is already a member of this organization.");
        }
        if (org.getPendingOrgInvites() != null && org.getPendingOrgInvites().stream().anyMatch(member -> member.getUserId().equals(target.getId()))) {
            throw new InvalidOrganizationRequestException("That user has already been invited to this organization.");
        }

        User.OrganizationRole role = organizationAccessService.getOrganizationRoleOrThrow(org, roleId);
        if (role.isOwner()) {
            throw new OrganizationOperationForbiddenException("You cannot invite someone directly to the Owner role. Transfer ownership instead.");
        }

        if (org.getPendingOrgInvites() == null) {
            org.setPendingOrgInvites(new ArrayList<>());
        }
        if (target.isDeleted() || target.getAccountType() == User.AccountType.ORGANIZATION)
            throw new InvalidOrganizationRequestException("Invite an active personal account.");
        var invitation = new User.OrganizationMember(target.getId(), roleId);
        invitation.setRequestId(java.util.UUID.randomUUID().toString());
        invitation.setRequestExpiresAt(System.currentTimeMillis() + java.time.Duration.ofDays(7).toMillis());
        invitation.setRequestPermissions(role.getPermissions() == null ? java.util.Set.of() : java.util.Set.copyOf(role.getPermissions()));
        invitation.setRequestOwnerIds(OrganizationInvitationPolicy.owners(org));
        if (invitation.getRequestOwnerIds().isEmpty()) throw new InvalidOrganizationRequestException("Organization ownership must be repaired before inviting members.");
        org.getPendingOrgInvites().add(invitation);
        if (!persistence.create(snapshot, invitation)) throw conflict();

        Map<String, String> metadata = new HashMap<>();
        metadata.put("orgId", org.getId());
        metadata.put("action", "ORG_INVITE");
        metadata.put("requestId", invitation.getRequestId());
        notificationService.sendNotifcation(
                List.of(target.getId()),
                "Organization Invite",
                "You have been invited to join " + org.getUsername() + " as " + role.getName() + ".",
                URI.create("/dashboard/orgs"),
                org.getAvatarUrl(),
                NotificationType.ORG_INVITE,
                metadata
        );
    }

    public void resolveOrgInvite(String orgId, boolean accept, String requestId, User responder) {
        var snapshot = persistence.capture(orgId);
        User org = snapshot.organization();
        if (responder.isDeleted() || responder.getAccountType() == User.AccountType.ORGANIZATION)
            throw new InvalidOrganizationRequestException("An active personal account is required.");
        var invite = OrganizationInvitationPolicy.require(org, responder.getId(), requestId, accept);

        if (accept) {
            org.getOrganizationMembers().add(new User.OrganizationMember(invite.getUserId(), invite.getRoleId()));
            org.getPendingOrgInvites().remove(invite);
            if (!persistence.resolve(snapshot, responder.getId(), requestId, accept)) throw conflict();

            String msg = responder.getUsername() + " accepted the invitation to join " + org.getUsername();
            org.getOrganizationMembers().stream()
                    .filter(member -> organizationAccessService.hasOrgPermission(org, member.getUserId(), ApiKey.ApiPermission.ORG_MEMBER_READ)
                            && !member.getUserId().equals(responder.getId()))
                    .forEach(member -> notificationService.sendNotifcation(
                            List.of(member.getUserId()),
                            "Invite Accepted",
                            msg,
                            URI.create("/dashboard/orgs"),
                            responder.getAvatarUrl()
                    ));
        } else {
            org.getPendingOrgInvites().remove(invite);
            if (!persistence.resolve(snapshot, responder.getId(), requestId, accept)) throw conflict();
        }
    }

    public void voidOrgInvite(String orgId, String userId, String requestId, User requester) {
        var snapshot = persistence.capture(orgId);
        User org = snapshot.organization();
        organizationAccessService.requireOrgPermission(
                org,
                requester,
                ApiKey.ApiPermission.ORG_MEMBER_INVITE,
                "You do not have permission to cancel invites for this organization."
        );

        var invite = OrganizationInvitationPolicy.require(org, userId, requestId, false);
        org.getPendingOrgInvites().remove(invite);
        if (!persistence.resolve(snapshot, userId, requestId, false)) throw conflict();
    }
    private org.springframework.web.server.ResponseStatusException conflict() {
        return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                "The organization changed. Refresh before responding to the invitation.");
    }
}
