package net.modtale.service.user;

import net.modtale.mapper.UserMapper;
import net.modtale.model.dto.response.common.ResourceUrlResponse;
import net.modtale.model.dto.user.UserDTO;
import net.modtale.model.dto.user.UserSummaryDTO;
import net.modtale.model.user.User;
import net.modtale.service.media.MediaUploadService;
import net.modtale.service.security.FileValidationService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class OrganizationApplicationService {

    private final OrganizationService organizationService;
    private final MediaUploadService mediaUploadService;
    private final FileValidationService fileValidationService;

    public OrganizationApplicationService(
            OrganizationService organizationService,
            MediaUploadService mediaUploadService,
            FileValidationService fileValidationService
    ) {
        this.organizationService = organizationService;
        this.mediaUploadService = mediaUploadService;
        this.fileValidationService = fileValidationService;
    }

    public UserDTO createOrganization(String name, User currentUser) {
        return UserMapper.toDTO(organizationService.createOrganization(name, currentUser), true);
    }

    public List<UserDTO> getUserOrganizations(String userId, boolean includePrivate) {
        return organizationService.getUserOrganizations(userId).stream()
                .map(org -> UserMapper.toDTO(org, includePrivate))
                .toList();
    }

    public List<UserSummaryDTO> getOrganizationMembers(String orgId) {
        return organizationService.getOrganizationMembers(orgId).stream()
                .map(UserMapper::toSummaryDTO)
                .toList();
    }

    public List<UserSummaryDTO> getOrganizationInvites(String orgId) {
        return organizationService.getOrganizationInvites(orgId).stream()
                .map(UserMapper::toSummaryDTO)
                .toList();
    }

    public UserDTO createOrganizationRole(String orgId, String name, String color, java.util.Set<net.modtale.model.user.ApiKey.ApiPermission> permissions, User currentUser) {
        return UserMapper.toDTO(organizationService.createOrganizationRole(orgId, name, color, permissions, currentUser), true);
    }

    public UserDTO updateOrganizationRole(String orgId, String roleId, String name, String color, java.util.Set<net.modtale.model.user.ApiKey.ApiPermission> permissions, User currentUser) {
        return UserMapper.toDTO(organizationService.updateOrganizationRole(orgId, roleId, name, color, permissions, currentUser), true);
    }

    public UserDTO deleteOrganizationRole(String orgId, String roleId, User currentUser) {
        return UserMapper.toDTO(organizationService.deleteOrganizationRole(orgId, roleId, currentUser), true);
    }

    public void inviteOrganizationMember(String orgId, String userId, String roleId, User currentUser) {
        organizationService.inviteOrganizationMember(orgId, userId, roleId, currentUser);
    }

    public void removeOrganizationMember(String orgId, String userId, User currentUser) {
        organizationService.removeOrganizationMember(orgId, userId, currentUser);
    }

    public void updateOrganizationMemberRole(String orgId, String userId, String roleId, User currentUser) {
        organizationService.updateOrganizationMemberRole(orgId, userId, roleId, currentUser);
    }

    public UserDTO updateOrganization(String orgId, String displayName, String fallbackName, String bio, User currentUser) {
        String effectiveName = displayName == null ? fallbackName : displayName;
        return UserMapper.toDTO(organizationService.updateOrganization(orgId, effectiveName, bio, currentUser), true);
    }

    public void deleteOrganization(String orgId, User currentUser) {
        organizationService.deleteOrganization(orgId, currentUser);
    }

    public ResourceUrlResponse uploadOrganizationAvatar(String orgId, MultipartFile file, User currentUser) {
        String url = mediaUploadService.uploadPublicUrl(file, "avatars/" + orgId, fileValidationService::validateIcon);
        organizationService.updateOrganizationAvatar(orgId, url, currentUser);
        return new ResourceUrlResponse(url);
    }

    public ResourceUrlResponse uploadOrganizationBanner(String orgId, MultipartFile file, User currentUser) {
        String url = mediaUploadService.uploadPublicUrl(file, "banners/" + orgId, fileValidationService::validateBanner);
        organizationService.updateOrganizationBanner(orgId, url, currentUser);
        return new ResourceUrlResponse(url);
    }

    public void acceptOrganizationInvite(String orgId, User currentUser) {
        organizationService.resolveOrgInvite(orgId, true, currentUser);
    }

    public void declineOrganizationInvite(String orgId, User currentUser) {
        organizationService.resolveOrgInvite(orgId, false, currentUser);
    }

    public void cancelOrganizationInvite(String orgId, String userId, User currentUser) {
        organizationService.voidOrgInvite(orgId, userId, currentUser);
    }
}
