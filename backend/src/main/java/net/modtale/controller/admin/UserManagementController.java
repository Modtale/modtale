package net.modtale.controller.admin;

import net.modtale.mapper.AdminMapper;
import net.modtale.mapper.UserMapper;
import net.modtale.model.admin.AdminLog;
import net.modtale.model.dto.admin.BannedEmailDTO;
import net.modtale.model.dto.request.admin.BanEmailRequest;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.admin.AdminLogRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.admin.UserManagementService;
import net.modtale.service.communication.EmailService;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.user.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/v1/admin")
public class UserManagementController {

    @Autowired private AccountService accountService;
    @Autowired private UserManagementService userManagementService;
    @Autowired private UserRepository userRepository;
    @Autowired private AdminLogRepository adminLogRepository;
    @Autowired private EmailService emailService;
    @Autowired private AccessControlService accessControlService;

    private boolean canManageUser(User currentUser, User targetUser) {
        if (accessControlService.isSuperAdmin(currentUser)) return true;
        if (targetUser != null && targetUser.getRoles() != null && targetUser.getRoles().contains("ADMIN")) {
            return false;
        }
        return true;
    }

    private User getSafeUser() {
        try {
            return accountService.getCurrentUser();
        } catch (Exception e) {
            return null;
        }
    }

    private void logAction(String adminId, String action, String targetId, String targetType, String details) {
        adminLogRepository.save(new AdminLog(adminId, action, targetId, targetType, details));
    }

    @GetMapping("/users/bans")
    public ResponseEntity<List<BannedEmailDTO>> getBannedEmails() {
        User currentUser = getSafeUser();
        if (!accessControlService.isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(userManagementService.getBannedEmails().stream()
                .map(AdminMapper::toBannedEmailDTO)
                .toList());
    }

    @PostMapping("/users/bans")
    public ResponseEntity<?> banEmail(@RequestBody BanEmailRequest requestPayload) {
        User currentUser = getSafeUser();
        if (!accessControlService.isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        String email = requestPayload.getEmail();
        String reason = requestPayload.getReason();

        Optional<User> targetByEmail = userRepository.findByEmail(email);
        if (targetByEmail.isPresent() && !canManageUser(currentUser, targetByEmail.get())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Cannot ban the email of an administrator.");
        }

        try {
            userManagementService.banEmail(email, reason, currentUser.getId());
            logAction(currentUser.getId(), "BAN_EMAIL", email, "EMAIL", "Reason: " + reason);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/users/bans")
    public ResponseEntity<?> unbanEmail(@RequestParam String email) {
        User currentUser = getSafeUser();
        if (!accessControlService.isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        userManagementService.unbanEmail(email);
        logAction(currentUser.getId(), "UNBAN_EMAIL", email, "EMAIL", null);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<?> getUserDetails(@PathVariable String userId) {
        User currentUser = getSafeUser();
        if (!accessControlService.isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        Optional<User> target = userRepository.findById(userId);
        if (target.isEmpty()) return ResponseEntity.notFound().build();

        return ResponseEntity.ok(UserMapper.toDTO(target.get(), true));
    }

    @GetMapping("/users/{userId}/raw")
    public ResponseEntity<?> getRawUser(@PathVariable String userId) {
        User currentUser = getSafeUser();
        if (!accessControlService.isSuperAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        User target = userRepository.findById(userId).orElse(null);
        if (target == null) return ResponseEntity.notFound().build();

        target.setGithubAccessToken(null);
        target.setGitlabAccessToken(null);
        target.setGitlabRefreshToken(null);
        target.setGitlabTokenExpiresAt(null);

        return ResponseEntity.ok(target);
    }

    @PutMapping("/users/{userId}/raw")
    public ResponseEntity<?> updateRawUser(@PathVariable String userId, @RequestBody User updatedData) {
        User currentUser = getSafeUser();
        if (!accessControlService.isSuperAdmin(currentUser)) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        User existing = userRepository.findById(userId).orElse(null);
        if (existing == null) return ResponseEntity.notFound().build();

        updatedData.setId(existing.getId());
        updatedData.setPassword(existing.getPassword());
        updatedData.setMfaSecret(existing.getMfaSecret());
        updatedData.setVerificationToken(existing.getVerificationToken());
        updatedData.setVerificationTokenExpiry(existing.getVerificationTokenExpiry());
        updatedData.setPasswordResetToken(existing.getPasswordResetToken());
        updatedData.setPasswordResetTokenExpiry(existing.getPasswordResetTokenExpiry());
        updatedData.setGithubAccessToken(existing.getGithubAccessToken());
        updatedData.setGitlabAccessToken(existing.getGitlabAccessToken());
        updatedData.setGitlabRefreshToken(existing.getGitlabRefreshToken());
        updatedData.setGitlabTokenExpiresAt(existing.getGitlabTokenExpiresAt());

        userRepository.save(updatedData);
        logAction(currentUser.getId(), "RAW_UPDATE_USER", existing.getId(), "USER", "Updated via Raw JSON");
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(
            @PathVariable String userId,
            @RequestParam(required = false, defaultValue = "Administrative enforcement action.") String reason
    ) {
        User currentUser = getSafeUser();
        if (!accessControlService.isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        User target = userRepository.findById(userId).orElse(null);
        if (target == null) return ResponseEntity.notFound().build();

        if (!canManageUser(currentUser, target)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only Super Admin can delete other admins.");
        }

        try {
            accountService.deleteUser(target.getId());
            try {
                if (target.getEmail() != null && !target.getEmail().isEmpty()) {
                    emailService.sendAccountDeletionEmail(target.getEmail(), target.getUsername(), reason);
                }
            } catch (Exception e) {}
            logAction(currentUser.getId(), "DELETE_USER", target.getId(), "USER", "Username: " + target.getUsername() + ", Reason: " + reason);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/users/{userId}/tier")
    public ResponseEntity<?> setUserTier(@PathVariable String userId, @RequestParam String tier) {
        User currentUser = getSafeUser();
        if (!accessControlService.isSuperAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access Denied", "message", "You do not have permission."));
        }

        User target = userRepository.findById(userId).orElse(null);
        if (target == null) return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Not Found", "message", "User not found."));

        try {
            ApiKey.Tier tierEnum;
            if ("USER".equalsIgnoreCase(tier) || "FREE".equalsIgnoreCase(tier)) {
                tierEnum = ApiKey.Tier.USER;
            } else {
                tierEnum = ApiKey.Tier.valueOf(tier.toUpperCase());
            }

            userManagementService.setUserTier(target.getId(), tierEnum);
            logAction(currentUser.getId(), "UPDATE_TIER", target.getId(), "USER", "New Tier: " + tierEnum.name());

            return ResponseEntity.ok(Map.<String, Object>of(
                    "status", "success",
                    "message", "User " + target.getUsername() + " updated to tier " + tierEnum.name()
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid Tier", "message", "Tier must be USER or ENTERPRISE"));
        }
    }

    @PostMapping("/users/{userId}/role")
    public ResponseEntity<?> addUserRole(@PathVariable String userId, @RequestParam String role) {
        User currentUser = getSafeUser();
        if (!accessControlService.isSuperAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only Super Admin can manage roles.");
        }

        User target = userRepository.findById(userId).orElse(null);
        if (target == null) return ResponseEntity.notFound().build();

        if (target.getRoles() == null) target.setRoles(new ArrayList<>());
        if (!target.getRoles().contains(role)) {
            target.getRoles().add(role);
        }
        userRepository.save(target);
        logAction(currentUser.getId(), "ADD_ROLE", target.getId(), "USER", "Role: " + role);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{userId}/role")
    public ResponseEntity<?> removeUserRole(@PathVariable String userId, @RequestParam String role) {
        User currentUser = getSafeUser();
        if (!accessControlService.isSuperAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only Super Admin can manage roles.");
        }

        User target = userRepository.findById(userId).orElse(null);
        if (target == null) return ResponseEntity.notFound().build();

        if (target.getRoles() != null) {
            target.getRoles().remove(role);
            userRepository.save(target);
        }
        logAction(currentUser.getId(), "REMOVE_ROLE", target.getId(), "USER", "Role: " + role);
        return ResponseEntity.ok().build();
    }
}
