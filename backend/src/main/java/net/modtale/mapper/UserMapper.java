package net.modtale.mapper;

import net.modtale.model.dto.UserDTO;
import net.modtale.model.user.User;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class UserMapper {

    public static UserDTO toDTO(User user, boolean includePrivate) {
        if (user == null) return null;
        UserDTO dto = new UserDTO();

        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setBannerUrl(user.getBannerUrl());
        dto.setBio(user.getBio());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setTier(user.getTier());
        dto.setRoles(user.getRoles());
        dto.setAccountType(user.getAccountType());
        dto.setBadges(user.getBadges());

        dto.setOrganizationRoles(user.getOrganizationRoles());
        dto.setOrganizationMembers(user.getOrganizationMembers());
        dto.setFollowingIds(user.getFollowingIds());
        dto.setFollowerIds(user.getFollowerIds());

        if (user.getConnectedAccounts() != null) {
            if (includePrivate) {
                dto.setConnectedAccounts(user.getConnectedAccounts());
            } else {
                dto.setConnectedAccounts(user.getConnectedAccounts().stream()
                        .filter(User.ConnectedAccount::isVisible)
                        .collect(Collectors.toList()));
            }
        } else {
            dto.setConnectedAccounts(new ArrayList<>());
        }

        if (includePrivate) {
            dto.setEmail(user.getEmail());
            dto.setEmailVerified(user.isEmailVerified());
            dto.setMfaEnabled(user.isMfaEnabled());
            dto.setPendingOrgInvites(user.getPendingOrgInvites());
            dto.setLikedModIds(user.getLikedModIds());
            dto.setNotificationPreferences(user.getNotificationPreferences());
        } else {
            dto.setEmail(null);
            dto.setPendingOrgInvites(null);
            dto.setNotificationPreferences(null);
            dto.setLikedModIds(null);
        }

        return dto;
    }
}