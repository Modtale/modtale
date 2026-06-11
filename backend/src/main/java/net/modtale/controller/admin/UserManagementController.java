package net.modtale.controller.admin;

import jakarta.validation.Valid;
import net.modtale.model.dto.admin.BannedEmailDTO;
import net.modtale.model.dto.request.admin.BanEmailRequest;
import net.modtale.model.dto.response.admin.UserTierUpdateResponse;
import net.modtale.model.dto.user.UserDTO;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.service.admin.UserManagementService;
import net.modtale.service.user.AccountService;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class UserManagementController {

    private final AccountService accountService;
    private final UserManagementService userManagementService;

    public UserManagementController(AccountService accountService, UserManagementService userManagementService) {
        this.accountService = accountService;
        this.userManagementService = userManagementService;
    }

    @GetMapping("/users/bans")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<List<BannedEmailDTO>> getBannedEmails() {
        return ResponseEntity.ok(userManagementService.getBannedEmailViews());
    }

    @PostMapping("/users/bans")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<Void> banEmail(@Valid @RequestBody BanEmailRequest requestPayload) {
        User currentUser = accountService.requireCurrentUser("banning email addresses");
        userManagementService.banEmail(currentUser, requestPayload.getEmail(), requestPayload.getReason());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/bans")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<Void> unbanEmail(@RequestParam String email) {
        User currentUser = accountService.requireCurrentUser("unbanning email addresses");
        userManagementService.unbanEmail(currentUser.getId(), email);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users/{userId}")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<UserDTO> getUserDetails(@PathVariable String userId) {
        return ResponseEntity.ok(userManagementService.getUserDetails(userId));
    }

    @GetMapping("/users/{userId}/raw")
    @PreAuthorize("@apiSecurity.isSuperAdmin(authentication)")
    public ResponseEntity<User> getRawUser(@PathVariable String userId) {
        return ResponseEntity.ok(userManagementService.getRawUser(userId));
    }

    @PutMapping("/users/{userId}/raw")
    @PreAuthorize("@apiSecurity.isSuperAdmin(authentication)")
    public ResponseEntity<Void> updateRawUser(@PathVariable String userId, @RequestBody User updatedData) {
        User currentUser = accountService.requireCurrentUser("editing raw user data");
        userManagementService.updateRawUser(currentUser.getId(), userId, updatedData);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{userId}")
    @PreAuthorize("@apiSecurity.isAdmin(authentication)")
    public ResponseEntity<Void> deleteUser(
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "Administrative enforcement action.") String reason
    ) {
        User currentUser = accountService.requireCurrentUser("deleting users");
        userManagementService.deleteUser(currentUser, userId, reason);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/users/{userId}/tier")
    @PreAuthorize("@apiSecurity.isSuperAdmin(authentication)")
    public ResponseEntity<UserTierUpdateResponse> setUserTier(@PathVariable String userId, @RequestParam ApiKey.Tier tier) {
        User currentUser = accountService.requireCurrentUser("managing user tiers");
        return ResponseEntity.ok(userManagementService.setUserTier(currentUser.getId(), userId, tier));
    }

    @PostMapping("/users/{userId}/role")
    @PreAuthorize("@apiSecurity.isSuperAdmin(authentication)")
    public ResponseEntity<Void> addUserRole(@PathVariable String userId, @RequestParam String role) {
        User currentUser = accountService.requireCurrentUser("managing user roles");
        userManagementService.addUserRole(currentUser.getId(), userId, role);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{userId}/role")
    @PreAuthorize("@apiSecurity.isSuperAdmin(authentication)")
    public ResponseEntity<Void> removeUserRole(@PathVariable String userId, @RequestParam String role) {
        User currentUser = accountService.requireCurrentUser("managing user roles");
        userManagementService.removeUserRole(currentUser.getId(), userId, role);
        return ResponseEntity.ok().build();
    }
}
