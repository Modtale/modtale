package net.modtale.controller.user;

import net.modtale.mapper.UserMapper;
import net.modtale.model.dto.request.organization.AddOrganizationMemberRequest;
import net.modtale.model.dto.request.organization.CreateOrganizationRequest;
import net.modtale.model.dto.request.organization.OrganizationRoleRequest;
import net.modtale.model.dto.request.organization.UpdateOrganizationMemberRoleRequest;
import net.modtale.model.dto.request.organization.UpdateOrganizationRequest;
import net.modtale.model.dto.user.UserDTO;
import net.modtale.model.dto.user.UserSummaryDTO;
import net.modtale.model.user.User;
import net.modtale.service.storage.StorageService;
import net.modtale.service.security.FileValidationService;
import net.modtale.service.user.OrganizationService;
import net.modtale.service.user.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class OrganizationController {

    @Autowired private OrganizationService organizationService;
    @Autowired private AccountService accountService;
    @Autowired private StorageService storageService;
    @Autowired private FileValidationService validationService;

    @PostMapping("/orgs")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('ORG_CREATE', authentication)")
    public ResponseEntity<?> createOrganization(@RequestBody CreateOrganizationRequest requestPayload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        String name = requestPayload.getName();
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Organization name is required.");
        }

        try {
            User org = organizationService.createOrganization(name, user);
            return ResponseEntity.ok(UserMapper.toDTO(org, true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/user/orgs")
    @PreAuthorize("@apiSecurity.hasAnyPerm('ORG_READ', authentication)")
    public ResponseEntity<List<UserDTO>> getMyOrganizations() {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        List<User> orgs = organizationService.getUserOrganizations(user.getId());
        return ResponseEntity.ok(orgs.stream()
                .map(u -> UserMapper.toDTO(u, true))
                .collect(Collectors.toList()));
    }

    @GetMapping("/users/{userId}/organizations")
    @PreAuthorize("@apiSecurity.hasAnyPerm('ORG_READ', authentication)")
    public ResponseEntity<List<UserDTO>> getUserOrganizations(@PathVariable String userId) {
        List<User> orgs = organizationService.getUserOrganizations(userId);
        return ResponseEntity.ok(orgs.stream()
                .map(u -> UserMapper.toDTO(u, false))
                .collect(Collectors.toList()));
    }

    @GetMapping("/orgs/{orgId}/members")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_READ', authentication)")
    public ResponseEntity<?> getOrgMembers(@PathVariable String orgId) {
        try {
            List<UserSummaryDTO> members = organizationService.getOrganizationMembers(orgId).stream()
                    .map(UserMapper::toSummaryDTO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(members);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/orgs/{orgId}/invites")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_READ', authentication)")
    public ResponseEntity<?> getOrgInvites(@PathVariable String orgId) {
        try {
            List<User> invites = organizationService.getOrganizationInvites(orgId);
            return ResponseEntity.ok(invites.stream()
                    .map(UserMapper::toSummaryDTO)
                    .collect(Collectors.toList()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/orgs/{orgId}/roles")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_EDIT_ROLE', authentication)")
    public ResponseEntity<?> createOrgRole(@PathVariable String orgId, @RequestBody OrganizationRoleRequest payload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        if (payload.getName() == null || payload.getName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Role name is required.");
        }

        try {
            User updated = organizationService.createOrganizationRole(orgId, payload.getName(), payload.getColor(), payload.getPermissions(), user);
            return ResponseEntity.ok(UserMapper.toDTO(updated, true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/orgs/{orgId}/roles/{roleId}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_EDIT_ROLE', authentication)")
    public ResponseEntity<?> updateOrgRole(@PathVariable String orgId, @PathVariable String roleId, @RequestBody OrganizationRoleRequest payload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        try {
            User updated = organizationService.updateOrganizationRole(orgId, roleId, payload.getName(), payload.getColor(), payload.getPermissions(), user);
            return ResponseEntity.ok(UserMapper.toDTO(updated, true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/orgs/{orgId}/roles/{roleId}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_EDIT_ROLE', authentication)")
    public ResponseEntity<?> deleteOrgRole(@PathVariable String orgId, @PathVariable String roleId) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        try {
            User updated = organizationService.deleteOrganizationRole(orgId, roleId, user);
            return ResponseEntity.ok(UserMapper.toDTO(updated, true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/orgs/{orgId}/members")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_INVITE', authentication)")
    public ResponseEntity<?> addOrgMember(@PathVariable String orgId, @RequestBody AddOrganizationMemberRequest requestPayload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        String targetUserId = requestPayload.getUserId();
        String roleId = requestPayload.getRoleId();

        if (roleId == null) {
            return ResponseEntity.badRequest().body("Role ID is required.");
        }

        try {
            organizationService.inviteOrganizationMember(orgId, targetUserId, roleId, user);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/orgs/{orgId}/members/{userId}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_REMOVE', authentication)")
    public ResponseEntity<?> removeOrgMember(@PathVariable String orgId, @PathVariable String userId) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        if (userId.equals(user.getId())) {
            List<User> userOrgs = organizationService.getUserOrganizations(user.getId());
            if (userOrgs.stream().filter(o -> o.getId().equals(orgId)).findFirst().map(o -> o.getOrganizationMembers().size() <= 1).orElse(false)) {
                return ResponseEntity.badRequest().body("You cannot leave the organization as you are the only member. Delete the organization instead.");
            }
        }

        try {
            organizationService.removeOrganizationMember(orgId, userId, user);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/orgs/{orgId}/members/{userId}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_EDIT_ROLE', authentication)")
    public ResponseEntity<?> updateMemberRole(@PathVariable String orgId, @PathVariable String userId, @RequestBody UpdateOrganizationMemberRoleRequest requestPayload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        String newRoleId = requestPayload.getRoleId();
        if (newRoleId == null) return ResponseEntity.badRequest().body("Role ID is required.");

        try {
            organizationService.updateOrganizationMemberRole(orgId, userId, newRoleId, user);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/orgs/{orgId}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_EDIT_METADATA', authentication)")
    public ResponseEntity<?> updateOrganization(@PathVariable String orgId, @RequestBody UpdateOrganizationRequest requestPayload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        String name = requestPayload.getDisplayName();
        if (name == null) name = requestPayload.getName();
        String bio = requestPayload.getBio();

        try {
            User updated = organizationService.updateOrganization(orgId, name, bio, user);
            return ResponseEntity.ok(UserMapper.toDTO(updated, true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/orgs/{orgId}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_DELETE', authentication)")
    public ResponseEntity<?> deleteOrganization(@PathVariable String orgId) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        try {
            organizationService.deleteOrganization(orgId, user);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to delete organization");
        }
    }

    @PostMapping("/orgs/{orgId}/avatar")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_EDIT_AVATAR', authentication)")
    public ResponseEntity<?> uploadOrgAvatar(@PathVariable String orgId, @RequestParam("file") MultipartFile file) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            validationService.validateIcon(file);
            String path = storageService.upload(file, "avatars/" + orgId);
            String url = storageService.getPublicUrl(path);
            organizationService.updateOrganizationAvatar(orgId, url, user);
            return ResponseEntity.ok(url);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to upload avatar");
        }
    }

    @PostMapping("/orgs/{orgId}/banner")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_EDIT_BANNER', authentication)")
    public ResponseEntity<?> uploadOrgBanner(@PathVariable String orgId, @RequestParam("file") MultipartFile file) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            validationService.validateBanner(file);
            String path = storageService.upload(file, "banners/" + orgId);
            String url = storageService.getPublicUrl(path);
            organizationService.updateOrganizationBanner(orgId, url, user);
            return ResponseEntity.ok(url);
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to upload banner");
        }
    }

    @PostMapping("/orgs/{orgId}/invite/accept")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('ORG_INVITE_ACCEPT', authentication)")
    public ResponseEntity<?> acceptOrgInvite(@PathVariable String orgId) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            organizationService.resolveOrgInvite(orgId, true, user);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/orgs/{orgId}/invite/decline")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('ORG_INVITE_DECLINE', authentication)")
    public ResponseEntity<?> declineOrgInvite(@PathVariable String orgId) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            organizationService.resolveOrgInvite(orgId, false, user);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/orgs/{orgId}/invites/{userId}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_INVITE', authentication)")
    public ResponseEntity<?> cancelOrgInvite(@PathVariable String orgId, @PathVariable String userId) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            organizationService.voidOrgInvite(orgId, userId);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
