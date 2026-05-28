package net.modtale.service.user;

import net.modtale.model.user.ApiKey;
import net.modtale.model.user.NotificationType;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.auth.ApiKeyService;
import net.modtale.service.communication.NotificationService;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.security.SanitizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

@Service
public class OrganizationService {

    @Autowired private UserRepository userRepository;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private TrackingService trackingService;
    @Autowired private ApiKeyService apiKeyService;
    @Autowired private NotificationService notificationService;
    @Autowired private SanitizationService sanitizer;
    @Autowired private AccountService accountService;
    @Autowired private AccessControlService accessControlService;

    @Value("${app.limits.max-orgs-per-user:5}")
    private int maxOrgsPerUser;

    public User createOrganization(String name, User owner) {
        String cleanName = name.trim();

        if (userRepository.existsByUsernameIgnoreCase(cleanName)) {
            throw new IllegalArgumentException("A user or organization with this name already exists.");
        }
        if (!cleanName.matches("^[a-zA-Z0-9_.-]+$")) {
            throw new IllegalArgumentException("Organization name contains invalid characters.");
        }

        List<User> myOrgs = getUserOrganizations(owner.getId());
        long adminOrgCount = myOrgs.stream()
                .filter(o -> o.getOrganizationMembers().stream()
                        .anyMatch(m -> m.getUserId().equals(owner.getId()) && accessControlService.hasOrgPermission(o, owner.getId(), ApiKey.ApiPermission.ORG_EDIT_METADATA)))
                .count();

        if (adminOrgCount >= maxOrgsPerUser) {
            throw new IllegalStateException("You have reached the limit of " + maxOrgsPerUser + " organizations.");
        }

        User org = new User();
        org.setUsername(cleanName);
        org.setAccountType(User.AccountType.ORGANIZATION);
        org.setCreatedAt(LocalDate.now().toString());
        org.setTier(owner.getTier());
        org.setAvatarUrl("https://ui-avatars.com/api/?name=" + cleanName + "&background=random");

        User.OrganizationRole ownerRole = new User.OrganizationRole(UUID.randomUUID().toString(), "Owner", "#ef4444", EnumSet.allOf(ApiKey.ApiPermission.class));
        ownerRole.setOwner(true);
        User.OrganizationRole adminRole = new User.OrganizationRole(UUID.randomUUID().toString(), "Admin", "#fbbf24", EnumSet.complementOf(EnumSet.of(ApiKey.ApiPermission.ORG_DELETE)));
        User.OrganizationRole memberRole = new User.OrganizationRole(UUID.randomUUID().toString(), "Member", "#3b82f6", EnumSet.of(ApiKey.ApiPermission.PROJECT_READ, ApiKey.ApiPermission.VERSION_READ, ApiKey.ApiPermission.VERSION_DOWNLOAD));

        org.setOrganizationRoles(new ArrayList<>(List.of(ownerRole, adminRole, memberRole)));
        org.setOrganizationMembers(new ArrayList<>(List.of(new User.OrganizationMember(owner.getId(), ownerRole.getId()))));

        User savedOrg = userRepository.save(org);
        trackingService.logNewOrg(savedOrg.getId());
        return savedOrg;
    }

    public User updateOrganization(String orgId, String newName, String bio, User requester) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        if (!accessControlService.hasOrgPermission(org, requester.getId(), ApiKey.ApiPermission.ORG_EDIT_METADATA)) throw new SecurityException("Insufficient permissions.");

        if (newName != null && !newName.isBlank() && !newName.equals(org.getUsername())) {
            Optional<User> existing = userRepository.findByUsernameIgnoreCase(newName);
            if (existing.isPresent() && !existing.get().getId().equals(orgId)) throw new IllegalArgumentException("Name already taken.");
            if (!newName.matches("^[a-zA-Z0-9_.-]+$")) throw new IllegalArgumentException("Name contains invalid characters.");
            org.setUsername(newName);
        }

        if (bio != null) org.setBio(sanitizer.sanitizePlainText(bio));
        return userRepository.save(org);
    }

    public User createOrganizationRole(String orgId, String name, String color, Set<ApiKey.ApiPermission> perms, User requester) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        if (!accessControlService.hasOrgPermission(org, requester.getId(), ApiKey.ApiPermission.ORG_MEMBER_EDIT_ROLE)) throw new SecurityException("Insufficient permissions.");

        if (org.getOrganizationRoles() == null) org.setOrganizationRoles(new ArrayList<>());
        if (org.getOrganizationRoles().size() >= 20) throw new IllegalArgumentException("Maximum of 20 roles reached.");

        org.getOrganizationRoles().add(new User.OrganizationRole(UUID.randomUUID().toString(), name, color, perms));
        return userRepository.save(org);
    }

    public User updateOrganizationRole(String orgId, String roleId, String name, String color, Set<ApiKey.ApiPermission> perms, User requester) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        if (!accessControlService.hasOrgPermission(org, requester.getId(), ApiKey.ApiPermission.ORG_MEMBER_EDIT_ROLE)) throw new SecurityException("Insufficient permissions.");

        User.OrganizationRole role = org.getOrganizationRoles().stream().filter(r -> r.getId().equals(roleId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Role not found."));
        if (role.isOwner()) throw new SecurityException("The Owner role cannot be modified.");

        if (name != null) role.setName(name);
        if (color != null) role.setColor(color);
        if (perms != null) {
            role.setPermissions(perms);
            org.getOrganizationMembers().stream().filter(m -> roleId.equals(m.getRoleId())).forEach(m -> apiKeyService.syncUserOrgPermissions(m.getUserId(), orgId, perms));
        }

        return userRepository.save(org);
    }

    public User deleteOrganizationRole(String orgId, String roleId, User requester) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        if (!accessControlService.hasOrgPermission(org, requester.getId(), ApiKey.ApiPermission.ORG_MEMBER_EDIT_ROLE)) throw new SecurityException("Insufficient permissions.");

        User.OrganizationRole role = org.getOrganizationRoles().stream().filter(r -> r.getId().equals(roleId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Role not found."));
        if (role.isOwner()) throw new SecurityException("The Owner role cannot be deleted.");
        if (org.getOrganizationMembers().stream().anyMatch(m -> roleId.equals(m.getRoleId()))) throw new IllegalArgumentException("Cannot delete role while members are assigned to it.");

        org.getOrganizationRoles().removeIf(r -> r.getId().equals(roleId));
        return userRepository.save(org);
    }

    public void inviteOrganizationMember(String orgId, String targetUserId, String roleId, User requester) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        if (org.getAccountType() != User.AccountType.ORGANIZATION) throw new IllegalArgumentException("Target is not an organization");
        if (!accessControlService.hasOrgPermission(org, requester.getId(), ApiKey.ApiPermission.ORG_MEMBER_INVITE)) throw new SecurityException("Only members with invite permissions can invite members.");

        User target = userRepository.findById(targetUserId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        if (org.getOrganizationMembers().stream().anyMatch(m -> m.getUserId().equals(target.getId()))) throw new IllegalArgumentException("User is already a member.");
        if (org.getPendingOrgInvites() != null && org.getPendingOrgInvites().stream().anyMatch(m -> m.getUserId().equals(target.getId()))) throw new IllegalArgumentException("User has already been invited.");

        User.OrganizationRole role = org.getOrganizationRoles().stream().filter(r -> r.getId().equals(roleId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Role not found."));
        if (role.isOwner()) throw new SecurityException("You cannot invite someone directly to the Owner role. Transfer ownership instead.");

        if (org.getPendingOrgInvites() == null) org.setPendingOrgInvites(new ArrayList<>());
        org.getPendingOrgInvites().add(new User.OrganizationMember(target.getId(), roleId));
        userRepository.save(org);

        Map<String, String> metadata = new HashMap<>(); metadata.put("orgId", org.getId()); metadata.put("action", "ORG_INVITE");
        notificationService.sendNotifcation(List.of(target.getId()), "Organization Invite", "You have been invited to join " + org.getUsername() + " as " + role.getName() + ".", URI.create("/dashboard/orgs"), org.getAvatarUrl(), NotificationType.ORG_INVITE, metadata);
    }

    public void resolveOrgInvite(String orgId, boolean accept, User responder) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        User.OrganizationMember invite = org.getPendingOrgInvites() != null ? org.getPendingOrgInvites().stream().filter(m -> m.getUserId().equals(responder.getId())).findFirst().orElse(null) : null;

        if (invite == null) throw new IllegalArgumentException("No pending invite found for this organization.");

        if (accept) {
            org.getOrganizationMembers().add(invite);
            org.getPendingOrgInvites().remove(invite);
            userRepository.save(org);

            String msg = responder.getUsername() + " accepted the invitation to join " + org.getUsername();
            org.getOrganizationMembers().stream().filter(m -> accessControlService.hasOrgPermission(org, m.getUserId(), ApiKey.ApiPermission.ORG_MEMBER_READ) && !m.getUserId().equals(responder.getId()))
                    .forEach(admin -> notificationService.sendNotifcation(List.of(admin.getUserId()), "Invite Accepted", msg, URI.create("/dashboard/orgs"), responder.getAvatarUrl()));
        } else {
            org.getPendingOrgInvites().remove(invite);
            userRepository.save(org);
        }
    }

    public void voidOrgInvite(String orgId, String userId) {
        userRepository.findById(orgId).ifPresent(org -> {
            if (org.getPendingOrgInvites() != null && org.getPendingOrgInvites().removeIf(m -> m.getUserId().equals(userId))) {
                userRepository.save(org);
            }
        });
    }

    public void updateOrganizationMemberRole(String orgId, String targetUserId, String newRoleId, User requester) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        if (!accessControlService.hasOrgPermission(org, requester.getId(), ApiKey.ApiPermission.ORG_MEMBER_EDIT_ROLE)) throw new SecurityException("Insufficient permissions.");

        User.OrganizationRole newRole = org.getOrganizationRoles().stream().filter(r -> r.getId().equals(newRoleId)).findFirst().orElseThrow(() -> new IllegalArgumentException("Target role not found."));
        User.OrganizationMember targetMember = org.getOrganizationMembers().stream().filter(m -> m.getUserId().equals(targetUserId)).findFirst().orElseThrow(() -> new IllegalArgumentException("User is not a member of this organization."));
        User.OrganizationRole targetCurrentRole = org.getOrganizationRoles().stream().filter(r -> r.getId().equals(targetMember.getRoleId())).findFirst().orElse(null);
        User.OrganizationMember requesterMember = org.getOrganizationMembers().stream().filter(m -> m.getUserId().equals(requester.getId())).findFirst().orElse(null);
        User.OrganizationRole requesterRole = requesterMember != null ? org.getOrganizationRoles().stream().filter(r -> r.getId().equals(requesterMember.getRoleId())).findFirst().orElse(null) : null;

        boolean isRequesterOwner = requesterRole != null && requesterRole.isOwner();
        boolean isTargetOwner = targetCurrentRole != null && targetCurrentRole.isOwner();

        if (newRole.isOwner()) {
            if (!isRequesterOwner) throw new SecurityException("Only the current Owner can transfer ownership.");
            if (requester.getId().equals(targetUserId)) throw new IllegalArgumentException("You are already the owner.");

            targetMember.setRoleId(newRole.getId());
            requesterMember.setRoleId(targetCurrentRole != null ? targetCurrentRole.getId() : org.getOrganizationRoles().stream().filter(r -> !r.isOwner()).findFirst().get().getId());
            userRepository.save(org);

            apiKeyService.syncUserOrgPermissions(targetUserId, orgId, newRole.getPermissions());
            apiKeyService.syncUserOrgPermissions(requester.getId(), orgId, org.getOrganizationRoles().stream().filter(r -> r.getId().equals(requesterMember.getRoleId())).findFirst().get().getPermissions());
        } else if (isTargetOwner) {
            throw new IllegalArgumentException("You cannot demote the Owner. Transfer ownership to another member instead.");
        } else {
            targetMember.setRoleId(newRoleId);
            userRepository.save(org);
            apiKeyService.syncUserOrgPermissions(targetUserId, orgId, newRole.getPermissions());
        }

        notificationService.sendNotifcation(List.of(targetUserId), "Role Updated", "Your role in " + org.getUsername() + " has been updated to " + newRole.getName() + ".", URI.create("/dashboard/orgs"), org.getAvatarUrl());
    }

    public void removeOrganizationMember(String orgId, String targetUserId, User requester) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        boolean canRemove = accessControlService.hasOrgPermission(org, requester.getId(), ApiKey.ApiPermission.ORG_MEMBER_REMOVE);

        if (!canRemove && !requester.getId().equals(targetUserId)) throw new SecurityException("Insufficient permissions.");

        User.OrganizationMember targetMember = org.getOrganizationMembers().stream().filter(m -> m.getUserId().equals(targetUserId)).findFirst().orElseThrow(() -> new IllegalArgumentException("User not found in organization."));
        User.OrganizationRole targetRole = org.getOrganizationRoles().stream().filter(r -> r.getId().equals(targetMember.getRoleId())).findFirst().orElse(null);

        if (targetRole != null && targetRole.isOwner()) throw new IllegalArgumentException("The Owner cannot be removed or leave the organization. Transfer ownership first or delete the organization entirely.");

        if (org.getOrganizationMembers().removeIf(m -> m.getUserId().equals(targetUserId))) {
            userRepository.save(org);
            apiKeyService.syncUserOrgPermissions(targetUserId, orgId, new HashSet<>());
        }
    }

    public void updateOrganizationAvatar(String orgId, String url, User requester) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        if (!accessControlService.hasOrgPermission(org, requester.getId(), ApiKey.ApiPermission.ORG_EDIT_AVATAR)) throw new SecurityException("Insufficient permissions.");
        org.setAvatarUrl(url);
        userRepository.save(org);
    }

    public void updateOrganizationBanner(String orgId, String url, User requester) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        if (!accessControlService.hasOrgPermission(org, requester.getId(), ApiKey.ApiPermission.ORG_EDIT_BANNER)) throw new SecurityException("Insufficient permissions.");
        org.setBannerUrl(url);
        userRepository.save(org);
    }

    public void deleteOrganization(String orgId, User requester) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        if (org.getAccountType() != User.AccountType.ORGANIZATION) throw new IllegalArgumentException("Target is not an organization");
        if (!accessControlService.hasOrgPermission(org, requester.getId(), ApiKey.ApiPermission.ORG_DELETE)) throw new SecurityException("Insufficient permissions.");
        accountService.deleteUser(orgId);
    }

    public List<User> getOrganizationMembers(String orgId) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        if (org.getOrganizationMembers() == null || org.getOrganizationMembers().isEmpty() || org.isDeleted()) return new ArrayList<>();

        List<String> memberIds = org.getOrganizationMembers().stream().map(User.OrganizationMember::getUserId).collect(Collectors.toList());
        Query query = new Query(Criteria.where("_id").in(memberIds).and("deletedAt").is(null));
        query.fields().include("username", "avatarUrl", "roles", "tier", "id", "bio");
        return mongoTemplate.find(query, User.class);
    }

    public List<User> getOrganizationInvites(String orgId) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        if (org.getPendingOrgInvites() == null || org.getPendingOrgInvites().isEmpty()) return new ArrayList<>();

        List<String> inviteIds = org.getPendingOrgInvites().stream().map(User.OrganizationMember::getUserId).collect(Collectors.toList());
        Query query = new Query(Criteria.where("_id").in(inviteIds).and("deletedAt").is(null));
        query.fields().include("username", "avatarUrl", "id");
        return mongoTemplate.find(query, User.class);
    }

    public List<User> getUserOrganizations(String userId) {
        return userRepository.findOrganizationsByMemberId(userId).stream().filter(o -> !o.isDeleted()).collect(Collectors.toList());
    }

    public void unlinkOrgAccount(String orgId, String provider, User requester) {
        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        if (!accessControlService.hasOrgPermission(org, requester.getId(), ApiKey.ApiPermission.ORG_CONNECTION_MANAGE)) {
            throw new SecurityException("Insufficient permissions.");
        }

        boolean removed = org.getConnectedAccounts().removeIf(a -> a.getProvider().equals(provider));
        if (removed) {
            if ("github".equals(provider)) org.setGithubAccessToken(null);
            if ("gitlab".equals(provider)) {
                org.setGitlabAccessToken(null);
                org.setGitlabRefreshToken(null);
                org.setGitlabTokenExpiresAt(null);
            }
            userRepository.save(org);
        }
    }

    public void toggleOrgConnectionVisibility(String orgId, String provider, User requester) {
        if ("google".equals(provider)) throw new IllegalArgumentException("Google accounts cannot be made visible.");

        User org = userRepository.findById(orgId).orElseThrow(() -> new IllegalArgumentException("Organization not found"));
        if (!accessControlService.hasOrgPermission(org, requester.getId(), ApiKey.ApiPermission.ORG_CONNECTION_MANAGE)) {
            throw new SecurityException("Insufficient permissions.");
        }

        org.getConnectedAccounts().stream()
                .filter(a -> a.getProvider().equals(provider))
                .findFirst()
                .ifPresent(a -> a.setVisible(!a.isVisible()));
        userRepository.save(org);
    }
}