package net.modtale.service.user;

import net.modtale.exception.InvalidOrganizationRequestException;
import net.modtale.exception.OrganizationOperationForbiddenException;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.NotificationType;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.communication.NotificationService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class OrganizationInviteService {

    private final UserRepository userRepository;
    private final OrganizationAccessService organizationAccessService;
    private final NotificationService notificationService;

    public OrganizationInviteService(
            UserRepository userRepository,
            OrganizationAccessService organizationAccessService,
            NotificationService notificationService
    ) {
        this.userRepository = userRepository;
        this.organizationAccessService = organizationAccessService;
        this.notificationService = notificationService;
    }

    public void inviteOrganizationMember(String orgId, String targetUserId, String roleId, User requester) {
        User org = organizationAccessService.getOrganizationOrThrow(orgId);
        organizationAccessService.requireOrgPermission(
                org,
                requester,
                ApiKey.ApiPermission.ORG_MEMBER_INVITE,
                "You do not have permission to invite members to this organization."
        );

        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new InvalidOrganizationRequestException("We couldn't find the user you tried to invite."));
        if (org.getOrganizationMembers().stream().anyMatch(member -> member.getUserId().equals(target.getId()))) {
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
        org.getPendingOrgInvites().add(new User.OrganizationMember(target.getId(), roleId));
        userRepository.save(org);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("orgId", org.getId());
        metadata.put("action", "ORG_INVITE");
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

    public void resolveOrgInvite(String orgId, boolean accept, User responder) {
        User org = organizationAccessService.getOrganizationOrThrow(orgId);
        User.OrganizationMember invite = org.getPendingOrgInvites() != null
                ? org.getPendingOrgInvites().stream()
                .filter(member -> member.getUserId().equals(responder.getId()))
                .findFirst()
                .orElse(null)
                : null;

        if (invite == null) {
            throw new InvalidOrganizationRequestException("We couldn't find a pending invite for this organization.");
        }

        if (accept) {
            org.getOrganizationMembers().add(invite);
            org.getPendingOrgInvites().remove(invite);
            userRepository.save(org);

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
            userRepository.save(org);
        }
    }

    public void voidOrgInvite(String orgId, String userId, User requester) {
        User org = organizationAccessService.getOrganizationOrThrow(orgId);
        organizationAccessService.requireOrgPermission(
                org,
                requester,
                ApiKey.ApiPermission.ORG_MEMBER_INVITE,
                "You do not have permission to cancel invites for this organization."
        );

        if (org.getPendingOrgInvites() == null || !org.getPendingOrgInvites().removeIf(member -> member.getUserId().equals(userId))) {
            throw new InvalidOrganizationRequestException("We couldn't find that pending organization invite.");
        }
        userRepository.save(org);
    }
}
