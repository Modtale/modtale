package net.modtale.controller.user;

import jakarta.validation.Valid;
import java.util.List;
import net.modtale.model.dto.request.organization.AddOrganizationMemberRequest;
import net.modtale.model.dto.request.organization.CreateOrganizationRequest;
import net.modtale.model.dto.request.organization.OrganizationRoleRequest;
import net.modtale.model.dto.request.organization.UpdateOrganizationMemberRoleRequest;
import net.modtale.model.dto.request.organization.UpdateOrganizationRequest;
import net.modtale.model.dto.response.common.ResourceUrlResponse;
import net.modtale.model.dto.user.UserDTO;
import net.modtale.model.dto.user.UserSummaryDTO;
import net.modtale.model.user.User;
import net.modtale.service.user.account.AccountService;
import net.modtale.service.user.organization.OrganizationApplicationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class OrganizationController {

    private final OrganizationApplicationService organizationApplicationService;
    private final AccountService accountService;

    public OrganizationController(OrganizationApplicationService organizationApplicationService, AccountService accountService) {
        this.organizationApplicationService = organizationApplicationService;
        this.accountService = accountService;
    }

    @PostMapping("/orgs")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('ORG_CREATE', authentication)")
    public ResponseEntity<UserDTO> createOrganization(@Valid @RequestBody CreateOrganizationRequest requestPayload) {
        User user = accountService.requireCurrentUser("creating an organization");
        return ResponseEntity.ok(organizationApplicationService.createOrganization(requestPayload.getName(), user));
    }

    @GetMapping("/user/orgs")
    @PreAuthorize("@apiSecurity.hasAnyPerm('ORG_READ', authentication)")
    public ResponseEntity<List<UserDTO>> getMyOrganizations() {
        User user = accountService.requireCurrentUser("viewing your organizations");
        return ResponseEntity.ok(organizationApplicationService.getUserOrganizations(user.getId(), true));
    }

    @GetMapping("/users/{userId}/organizations")
    @PreAuthorize("@apiSecurity.hasAnyPerm('ORG_READ', authentication)")
    public ResponseEntity<List<UserDTO>> getUserOrganizations(@PathVariable String userId) {
        return ResponseEntity.ok(organizationApplicationService.getUserOrganizations(userId, false));
    }

    @GetMapping("/orgs/{orgId}/members")
    @PreAuthorize("@apiSecurity.hasAnyPerm('ORG_READ', authentication)")
    public ResponseEntity<List<UserSummaryDTO>> getOrgMembers(@PathVariable String orgId) {
        return ResponseEntity.ok(organizationApplicationService.getOrganizationMembers(orgId));
    }

    @GetMapping("/orgs/{orgId}/invites")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_READ', authentication)")
    public ResponseEntity<List<UserSummaryDTO>> getOrgInvites(@PathVariable String orgId) {
        return ResponseEntity.ok(organizationApplicationService.getOrganizationInvites(orgId));
    }

    @PostMapping("/orgs/{orgId}/roles")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_EDIT_ROLE', authentication)")
    public ResponseEntity<UserDTO> createOrgRole(@PathVariable String orgId, @Valid @RequestBody OrganizationRoleRequest payload) {
        User user = accountService.requireCurrentUser("creating an organization role");
        return ResponseEntity.ok(
                organizationApplicationService.createOrganizationRole(
                        orgId,
                        payload.getName(),
                        payload.getColor(),
                        payload.getPermissions(),
                        user
                )
        );
    }

    @PutMapping("/orgs/{orgId}/roles/{roleId}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_EDIT_ROLE', authentication)")
    public ResponseEntity<UserDTO> updateOrgRole(
            @PathVariable String orgId,
            @PathVariable String roleId,
            @Valid @RequestBody OrganizationRoleRequest payload
    ) {
        User user = accountService.requireCurrentUser("updating an organization role");
        return ResponseEntity.ok(
                organizationApplicationService.updateOrganizationRole(
                        orgId,
                        roleId,
                        payload.getName(),
                        payload.getColor(),
                        payload.getPermissions(),
                        user
                )
        );
    }

    @DeleteMapping("/orgs/{orgId}/roles/{roleId}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_EDIT_ROLE', authentication)")
    public ResponseEntity<UserDTO> deleteOrgRole(@PathVariable String orgId, @PathVariable String roleId) {
        User user = accountService.requireCurrentUser("deleting an organization role");
        return ResponseEntity.ok(organizationApplicationService.deleteOrganizationRole(orgId, roleId, user));
    }

    @PostMapping("/orgs/{orgId}/members")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_INVITE', authentication)")
    public ResponseEntity<Void> addOrgMember(@PathVariable String orgId, @Valid @RequestBody AddOrganizationMemberRequest requestPayload) {
        User user = accountService.requireCurrentUser("inviting organization members");
        organizationApplicationService.inviteOrganizationMember(orgId, requestPayload.getUserId(), requestPayload.getRoleId(), user);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/orgs/{orgId}/members/{userId}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_REMOVE', authentication)")
    public ResponseEntity<Void> removeOrgMember(@PathVariable String orgId, @PathVariable String userId) {
        User user = accountService.requireCurrentUser("removing an organization member");
        organizationApplicationService.removeOrganizationMember(orgId, userId, user);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/orgs/{orgId}/members/{userId}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_EDIT_ROLE', authentication)")
    public ResponseEntity<Void> updateMemberRole(
            @PathVariable String orgId,
            @PathVariable String userId,
            @Valid @RequestBody UpdateOrganizationMemberRoleRequest requestPayload
    ) {
        User user = accountService.requireCurrentUser("changing an organization member role");
        organizationApplicationService.updateOrganizationMemberRole(orgId, userId, requestPayload.getRoleId(), user);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/orgs/{orgId}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_EDIT_METADATA', authentication)")
    public ResponseEntity<UserDTO> updateOrganization(
            @PathVariable String orgId,
            @Valid @RequestBody UpdateOrganizationRequest requestPayload
    ) {
        User user = accountService.requireCurrentUser("updating organization settings");
        return ResponseEntity.ok(
                organizationApplicationService.updateOrganization(
                        orgId,
                        requestPayload.getDisplayName(),
                        requestPayload.getName(),
                        requestPayload.getBio(),
                        user
                )
        );
    }

    @DeleteMapping("/orgs/{orgId}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_DELETE', authentication)")
    public ResponseEntity<Void> deleteOrganization(@PathVariable String orgId) {
        User user = accountService.requireCurrentUser("deleting an organization");
        organizationApplicationService.deleteOrganization(orgId, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/orgs/{orgId}/avatar")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_EDIT_AVATAR', authentication)")
    public ResponseEntity<ResourceUrlResponse> uploadOrgAvatar(@PathVariable String orgId, @RequestParam("file") MultipartFile file) {
        User user = accountService.requireCurrentUser("uploading an organization avatar");
        return ResponseEntity.ok(organizationApplicationService.uploadOrganizationAvatar(orgId, file, user));
    }

    @PostMapping("/orgs/{orgId}/banner")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_EDIT_BANNER', authentication)")
    public ResponseEntity<ResourceUrlResponse> uploadOrgBanner(@PathVariable String orgId, @RequestParam("file") MultipartFile file) {
        User user = accountService.requireCurrentUser("uploading an organization banner");
        return ResponseEntity.ok(organizationApplicationService.uploadOrganizationBanner(orgId, file, user));
    }

    @PostMapping("/orgs/{orgId}/invite/accept")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('ORG_INVITE_ACCEPT', authentication)")
    public ResponseEntity<Void> acceptOrgInvite(@PathVariable String orgId) {
        User user = accountService.requireCurrentUser("accepting an organization invite");
        organizationApplicationService.acceptOrganizationInvite(orgId, user);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/orgs/{orgId}/invite/decline")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('ORG_INVITE_DECLINE', authentication)")
    public ResponseEntity<Void> declineOrgInvite(@PathVariable String orgId) {
        User user = accountService.requireCurrentUser("declining an organization invite");
        organizationApplicationService.declineOrganizationInvite(orgId, user);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/orgs/{orgId}/invites/{userId}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_INVITE', authentication)")
    public ResponseEntity<Void> cancelOrgInvite(@PathVariable String orgId, @PathVariable String userId) {
        User user = accountService.requireCurrentUser("canceling an organization invite");
        organizationApplicationService.cancelOrganizationInvite(orgId, userId, user);
        return ResponseEntity.ok().build();
    }
}
