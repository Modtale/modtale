package net.modtale.service.user;

import net.modtale.exception.InvalidOrganizationRequestException;
import net.modtale.exception.OrganizationOperationForbiddenException;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.communication.NotificationService;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class OrganizationRoleService {

    private final UserRepository userRepository;
    private final OrganizationAccessService organizationAccessService;
    private final OrganizationApiKeyContextService organizationApiKeyContextService;
    private final NotificationService notificationService;

    public OrganizationRoleService(
            UserRepository userRepository,
            OrganizationAccessService organizationAccessService,
            OrganizationApiKeyContextService organizationApiKeyContextService,
            NotificationService notificationService
    ) {
        this.userRepository = userRepository;
        this.organizationAccessService = organizationAccessService;
        this.organizationApiKeyContextService = organizationApiKeyContextService;
        this.notificationService = notificationService;
    }

    public User createOrganizationRole(String orgId, String name, String color, Set<ApiKey.ApiPermission> perms, User requester) {
        User org = organizationAccessService.getOrganizationOrThrow(orgId);
        organizationAccessService.requireOrgPermission(
                org,
                requester,
                ApiKey.ApiPermission.ORG_MEMBER_EDIT_ROLE,
                "You do not have permission to create roles for this organization."
        );

        if (name == null || name.isBlank()) {
            throw new InvalidOrganizationRequestException("A role name is required before we can create an organization role.");
        }

        if (org.getOrganizationRoles() == null) {
            org.setOrganizationRoles(new ArrayList<>());
        }
        if (org.getOrganizationRoles().size() >= 20) {
            throw new InvalidOrganizationRequestException("Organizations can have at most 20 roles.");
        }

        org.getOrganizationRoles().add(new User.OrganizationRole(UUID.randomUUID().toString(), name.trim(), color, perms));
        return userRepository.save(org);
    }

    public User updateOrganizationRole(String orgId, String roleId, String name, String color, Set<ApiKey.ApiPermission> perms, User requester) {
        User org = organizationAccessService.getOrganizationOrThrow(orgId);
        organizationAccessService.requireOrgPermission(
                org,
                requester,
                ApiKey.ApiPermission.ORG_MEMBER_EDIT_ROLE,
                "You do not have permission to update roles for this organization."
        );

        User.OrganizationRole role = organizationAccessService.getOrganizationRoleOrThrow(org, roleId);
        if (role.isOwner()) {
            throw new OrganizationOperationForbiddenException("The Owner role cannot be modified.");
        }

        if (name != null) {
            role.setName(name.trim());
        }
        if (color != null) {
            role.setColor(color);
        }
        if (perms != null) {
            role.setPermissions(perms);
            org.getOrganizationMembers().stream()
                    .filter(member -> roleId.equals(member.getRoleId()))
                    .forEach(member -> organizationApiKeyContextService.syncUserOrgPermissions(member.getUserId(), orgId, perms));
        }

        return userRepository.save(org);
    }

    public User deleteOrganizationRole(String orgId, String roleId, User requester) {
        User org = organizationAccessService.getOrganizationOrThrow(orgId);
        organizationAccessService.requireOrgPermission(
                org,
                requester,
                ApiKey.ApiPermission.ORG_MEMBER_EDIT_ROLE,
                "You do not have permission to delete roles for this organization."
        );

        User.OrganizationRole role = organizationAccessService.getOrganizationRoleOrThrow(org, roleId);
        if (role.isOwner()) {
            throw new OrganizationOperationForbiddenException("The Owner role cannot be deleted.");
        }
        if (org.getOrganizationMembers().stream().anyMatch(member -> roleId.equals(member.getRoleId()))) {
            throw new InvalidOrganizationRequestException("You cannot delete a role while members are still assigned to it.");
        }

        org.getOrganizationRoles().removeIf(existingRole -> existingRole.getId().equals(roleId));
        return userRepository.save(org);
    }

    public void updateOrganizationMemberRole(String orgId, String targetUserId, String newRoleId, User requester) {
        User org = organizationAccessService.getOrganizationOrThrow(orgId);
        organizationAccessService.requireOrgPermission(
                org,
                requester,
                ApiKey.ApiPermission.ORG_MEMBER_EDIT_ROLE,
                "You do not have permission to change member roles in this organization."
        );

        User.OrganizationRole newRole = organizationAccessService.getOrganizationRoleOrThrow(org, newRoleId);
        User.OrganizationMember targetMember = organizationAccessService.getOrganizationMemberOrThrow(
                org,
                targetUserId,
                "That user is not a member of this organization."
        );
        User.OrganizationRole targetCurrentRole = organizationAccessService.getOrganizationRole(org, targetMember.getRoleId());
        User.OrganizationMember requesterMember = organizationAccessService.getOrganizationMemberOrThrow(
                org,
                requester.getId(),
                "We couldn't find your organization membership."
        );
        User.OrganizationRole requesterRole = organizationAccessService.getOrganizationRole(org, requesterMember.getRoleId());

        boolean isRequesterOwner = requesterRole != null && requesterRole.isOwner();
        boolean isTargetOwner = targetCurrentRole != null && targetCurrentRole.isOwner();

        if (newRole.isOwner()) {
            if (!isRequesterOwner) {
                throw new OrganizationOperationForbiddenException("Only the current Owner can transfer ownership.");
            }
            if (requester.getId().equals(targetUserId)) {
                throw new InvalidOrganizationRequestException("You are already the owner of this organization.");
            }

            targetMember.setRoleId(newRole.getId());
            requesterMember.setRoleId(resolveReplacementRoleId(org, targetCurrentRole));
            userRepository.save(org);

            organizationApiKeyContextService.syncUserOrgPermissions(targetUserId, orgId, newRole.getPermissions());
            organizationApiKeyContextService.syncUserOrgPermissions(
                    requester.getId(),
                    orgId,
                    organizationAccessService.getOrganizationRoleOrThrow(org, requesterMember.getRoleId()).getPermissions()
            );
        } else if (isTargetOwner) {
            throw new InvalidOrganizationRequestException("You cannot demote the Owner. Transfer ownership to another member instead.");
        } else {
            targetMember.setRoleId(newRoleId);
            userRepository.save(org);
            organizationApiKeyContextService.syncUserOrgPermissions(targetUserId, orgId, newRole.getPermissions());
        }

        notificationService.sendNotifcation(
                List.of(targetUserId),
                "Role Updated",
                "Your role in " + org.getUsername() + " has been updated to " + newRole.getName() + ".",
                URI.create("/dashboard/orgs"),
                org.getAvatarUrl()
        );
    }

    public void removeOrganizationMember(String orgId, String targetUserId, User requester) {
        User org = organizationAccessService.getOrganizationOrThrow(orgId);
        boolean canRemove = organizationAccessService.hasOrgPermission(org, requester.getId(), ApiKey.ApiPermission.ORG_MEMBER_REMOVE);

        if (!canRemove && !requester.getId().equals(targetUserId)) {
            throw new OrganizationOperationForbiddenException("You do not have permission to remove this member from the organization.");
        }

        User.OrganizationMember targetMember = organizationAccessService.getOrganizationMemberOrThrow(
                org,
                targetUserId,
                "We couldn't find that member in this organization."
        );
        User.OrganizationRole targetRole = organizationAccessService.getOrganizationRole(org, targetMember.getRoleId());

        if (targetRole != null && targetRole.isOwner()) {
            throw new InvalidOrganizationRequestException("The Owner cannot be removed or leave the organization. Transfer ownership first or delete the organization entirely.");
        }

        if (requester.getId().equals(targetUserId) && org.getOrganizationMembers().size() <= 1) {
            throw new InvalidOrganizationRequestException("You cannot leave this organization while you are its only member. Delete the organization instead.");
        }

        if (org.getOrganizationMembers().removeIf(member -> member.getUserId().equals(targetUserId))) {
            userRepository.save(org);
            organizationApiKeyContextService.syncUserOrgPermissions(targetUserId, orgId, new HashSet<>());
        }
    }

    private String resolveReplacementRoleId(User org, User.OrganizationRole targetCurrentRole) {
        if (targetCurrentRole != null && !targetCurrentRole.isOwner()) {
            return targetCurrentRole.getId();
        }

        return org.getOrganizationRoles().stream()
                .filter(role -> !role.isOwner())
                .map(User.OrganizationRole::getId)
                .findFirst()
                .orElseThrow(() -> new InvalidOrganizationRequestException(
                        "Create at least one non-owner role before transferring organization ownership."
                ));
    }
}
