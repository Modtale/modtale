package net.modtale.service.user.organization;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import net.modtale.config.properties.AppLimitProperties;
import net.modtale.exception.InvalidOrganizationRequestException;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.security.validation.SanitizationService;
import net.modtale.service.user.account.AccountService;
import net.modtale.util.MongoIdUtils;
import net.modtale.validation.AccountNameRules;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
public class OrganizationService {

    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final TrackingService trackingService;
    private final OrganizationApiKeyContextService organizationApiKeyContextService;
    private final SanitizationService sanitizer;
    private final AccountService accountService;
    private final OrganizationAccessService organizationAccessService;
    private final OrganizationConnectionService organizationConnectionService;
    private final OrganizationRoleService organizationRoleService;
    private final OrganizationInviteService organizationInviteService;
    private final int maxOrgsPerUser;

    public OrganizationService(
            UserRepository userRepository,
            MongoTemplate mongoTemplate,
            TrackingService trackingService,
            OrganizationApiKeyContextService organizationApiKeyContextService,
            SanitizationService sanitizer,
            AccountService accountService,
            OrganizationAccessService organizationAccessService,
            OrganizationConnectionService organizationConnectionService,
            OrganizationRoleService organizationRoleService,
            OrganizationInviteService organizationInviteService,
            AppLimitProperties limitProperties
    ) {
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.trackingService = trackingService;
        this.organizationApiKeyContextService = organizationApiKeyContextService;
        this.sanitizer = sanitizer;
        this.accountService = accountService;
        this.organizationAccessService = organizationAccessService;
        this.organizationConnectionService = organizationConnectionService;
        this.organizationRoleService = organizationRoleService;
        this.organizationInviteService = organizationInviteService;
        this.maxOrgsPerUser = limitProperties.maxOrgsPerUser();
    }

    public User createOrganization(String name, User owner) {
        String cleanName = name.trim();

        if (userRepository.existsByUsernameIgnoreCase(cleanName)) {
            throw new InvalidOrganizationRequestException("A user or organization with this name already exists.");
        }
        AccountNameRules.validateOrganizationName(cleanName);

        List<User> myOrgs = getUserOrganizations(owner.getId());
        long adminOrgCount = myOrgs.stream()
                .filter(org -> org.getOrganizationMembers().stream()
                        .anyMatch(member -> member.getUserId().equals(owner.getId())
                                && organizationAccessService.hasOrgPermission(org, owner.getId(), ApiKey.ApiPermission.ORG_EDIT_METADATA)))
                .count();

        if (adminOrgCount >= maxOrgsPerUser) {
            throw new InvalidOrganizationRequestException("You have reached the limit of " + maxOrgsPerUser + " organizations.");
        }

        User org = new User();
        org.setUsername(cleanName);
        org.setAccountType(User.AccountType.ORGANIZATION);
        org.setCreatedAt(LocalDate.now().toString());
        org.setTier(owner.getTier());
        org.setAvatarUrl("https://ui-avatars.com/api/?name=" + cleanName + "&background=random");

        User.OrganizationRole ownerRole = new User.OrganizationRole(
                UUID.randomUUID().toString(),
                "Owner",
                "#ef4444",
                EnumSet.allOf(ApiKey.ApiPermission.class)
        );
        ownerRole.setOwner(true);
        User.OrganizationRole adminRole = new User.OrganizationRole(
                UUID.randomUUID().toString(),
                "Admin",
                "#fbbf24",
                EnumSet.complementOf(EnumSet.of(ApiKey.ApiPermission.ORG_DELETE))
        );
        User.OrganizationRole memberRole = new User.OrganizationRole(
                UUID.randomUUID().toString(),
                "Member",
                "#3b82f6",
                EnumSet.of(ApiKey.ApiPermission.PROJECT_READ, ApiKey.ApiPermission.VERSION_READ, ApiKey.ApiPermission.VERSION_DOWNLOAD)
        );

        org.setOrganizationRoles(new ArrayList<>(List.of(ownerRole, adminRole, memberRole)));
        org.setOrganizationMembers(new ArrayList<>(List.of(new User.OrganizationMember(owner.getId(), ownerRole.getId()))));

        User savedOrg = userRepository.save(org);
        trackingService.logNewOrg(savedOrg.getId());
        return savedOrg;
    }

    public User updateOrganization(String orgId, String newName, String bio, User requester) {
        User org = organizationAccessService.getOrganizationOrThrow(orgId);
        organizationAccessService.requireOrgPermission(
                org,
                requester,
                ApiKey.ApiPermission.ORG_EDIT_METADATA,
                "You do not have permission to update this organization."
        );

        if (newName != null && !newName.isBlank() && !newName.equals(org.getUsername())) {
            Optional<User> existing = userRepository.findByUsernameIgnoreCase(newName);
            if (existing.isPresent() && !existing.get().getId().equals(orgId)) {
                throw new InvalidOrganizationRequestException("That organization name is already taken.");
            }
            AccountNameRules.validateOrganizationName(newName.trim());
            org.setUsername(newName.trim());
        }

        if (bio != null) {
            org.setBio(sanitizer.sanitizePlainText(bio));
        }
        return userRepository.save(org);
    }

    public User createOrganizationRole(String orgId, String name, String color, java.util.Set<ApiKey.ApiPermission> perms, User requester) {
        return organizationRoleService.createOrganizationRole(orgId, name, color, perms, requester);
    }

    public User updateOrganizationRole(String orgId, String roleId, String name, String color, java.util.Set<ApiKey.ApiPermission> perms, User requester) {
        return organizationRoleService.updateOrganizationRole(orgId, roleId, name, color, perms, requester);
    }

    public User deleteOrganizationRole(String orgId, String roleId, User requester) {
        return organizationRoleService.deleteOrganizationRole(orgId, roleId, requester);
    }

    public void inviteOrganizationMember(String orgId, String targetUserId, String roleId, User requester) {
        organizationInviteService.inviteOrganizationMember(orgId, targetUserId, roleId, requester);
    }

    public void resolveOrgInvite(String orgId, boolean accept, User responder) {
        organizationInviteService.resolveOrgInvite(orgId, accept, responder);
    }

    public void voidOrgInvite(String orgId, String userId, User requester) {
        organizationInviteService.voidOrgInvite(orgId, userId, requester);
    }

    public void updateOrganizationMemberRole(String orgId, String targetUserId, String newRoleId, User requester) {
        organizationRoleService.updateOrganizationMemberRole(orgId, targetUserId, newRoleId, requester);
    }

    public void removeOrganizationMember(String orgId, String targetUserId, User requester) {
        organizationRoleService.removeOrganizationMember(orgId, targetUserId, requester);
    }

    public void updateOrganizationAvatar(String orgId, String url, User requester) {
        User org = organizationAccessService.getOrganizationOrThrow(orgId);
        organizationAccessService.requireOrgPermission(
                org,
                requester,
                ApiKey.ApiPermission.ORG_EDIT_AVATAR,
                "You do not have permission to upload an avatar for this organization."
        );
        org.setAvatarUrl(url);
        userRepository.save(org);
    }

    public void updateOrganizationBanner(String orgId, String url, User requester) {
        User org = organizationAccessService.getOrganizationOrThrow(orgId);
        organizationAccessService.requireOrgPermission(
                org,
                requester,
                ApiKey.ApiPermission.ORG_EDIT_BANNER,
                "You do not have permission to upload a banner for this organization."
        );
        org.setBannerUrl(url);
        userRepository.save(org);
    }

    public void deleteOrganization(String orgId, User requester) {
        User org = organizationAccessService.getOrganizationOrThrow(orgId);
        organizationAccessService.requireOrgPermission(
                org,
                requester,
                ApiKey.ApiPermission.ORG_DELETE,
                "You do not have permission to delete this organization."
        );
        accountService.deleteUser(orgId);
    }

    public List<User> getOrganizationMembers(String orgId) {
        User org = organizationAccessService.getOrganizationOrThrow(orgId);
        if (org.getOrganizationMembers() == null || org.getOrganizationMembers().isEmpty() || org.isDeleted()) {
            return new ArrayList<>();
        }

        List<String> memberIds = org.getOrganizationMembers().stream()
                .map(User.OrganizationMember::getUserId)
                .collect(Collectors.toList());
        Query query = new Query(Criteria.where("_id").in(MongoIdUtils.expandIds(memberIds)).and("deletedAt").is(null));
        query.fields().include("username", "avatarUrl", "roles", "tier", "id", "bio");
        return mongoTemplate.find(query, User.class);
    }

    public List<User> getOrganizationInvites(String orgId) {
        User org = organizationAccessService.getOrganizationOrThrow(orgId);
        if (org.getPendingOrgInvites() == null || org.getPendingOrgInvites().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> inviteIds = org.getPendingOrgInvites().stream()
                .map(User.OrganizationMember::getUserId)
                .collect(Collectors.toList());
        Query query = new Query(Criteria.where("_id").in(MongoIdUtils.expandIds(inviteIds)).and("deletedAt").is(null));
        query.fields().include("username", "avatarUrl", "id");
        return mongoTemplate.find(query, User.class);
    }

    public List<User> getUserOrganizations(String userId) {
        return organizationApiKeyContextService.getUserOrganizations(userId);
    }

    public User requireConnectionManagedOrganization(String orgId, User requester) {
        return organizationConnectionService.requireConnectionManagedOrganization(orgId, requester);
    }

    public void unlinkOrgAccount(String orgId, String provider, User requester) {
        organizationConnectionService.unlinkOrgAccount(orgId, provider, requester);
    }

    public void toggleOrgConnectionVisibility(String orgId, String provider, User requester) {
        organizationConnectionService.toggleOrgConnectionVisibility(orgId, provider, requester);
    }
}
