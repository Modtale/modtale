package net.modtale.controller;

import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.model.resources.Mod;
import net.modtale.service.user.UserService;
import net.modtale.service.resources.ModService;
import net.modtale.repository.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    @Autowired
    private UserService userService;
    @Autowired
    private ModService modService;
    @Autowired
    private UserRepository userRepository;

    private static final String SUPER_ADMIN_ID = "692620f7c2f3266e23ac0ded";

    private boolean isSuperAdmin(User user) {
        return user != null && SUPER_ADMIN_ID.equals(user.getId());
    }

    private boolean isAdmin(User user) {
        return (user != null && user.getRoles() != null && user.getRoles().contains("ADMIN")) || isSuperAdmin(user);
    }

    @PostMapping("/users/{username}/tier")
    public ResponseEntity<?> setUserTier(
            @PathVariable String username,
            @RequestParam String tier
    ) {
        User currentUser = userService.getCurrentUser();
        if (!isSuperAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Access Denied", "message", "You do not have permission."));
        }

        try {
            ApiKey.Tier tierEnum;
            if ("USER".equalsIgnoreCase(tier) || "FREE".equalsIgnoreCase(tier)) {
                tierEnum = ApiKey.Tier.USER;
            } else {
                tierEnum = ApiKey.Tier.valueOf(tier.toUpperCase());
            }

            userService.setUserTier(username, tierEnum);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "User " + username + " updated to tier " + tierEnum
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid Tier", "message", "Tier must be USER or ENTERPRISE"));
        }
    }

    @PostMapping("/users/{username}/role")
    public ResponseEntity<?> addUserRole(@PathVariable String username, @RequestParam String role) {
        User currentUser = userService.getCurrentUser();
        if (!isSuperAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only Super Admin can manage roles.");
        }

        User target = userRepository.findByUsername(username).orElse(null);
        if (target == null) return ResponseEntity.notFound().build();

        if (target.getRoles() == null) target.setRoles(new ArrayList<>());
        if (!target.getRoles().contains(role)) {
            target.getRoles().add(role);
        }
        userRepository.save(target);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/users/{username}/role")
    public ResponseEntity<?> removeUserRole(@PathVariable String username, @RequestParam String role) {
        User currentUser = userService.getCurrentUser();
        if (!isSuperAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Only Super Admin can manage roles.");
        }

        User target = userRepository.findByUsername(username).orElse(null);
        if (target == null) return ResponseEntity.notFound().build();

        if (target.getRoles() != null) {
            target.getRoles().remove(role);
            userRepository.save(target);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping("/verification/queue")
    public ResponseEntity<List<Mod>> getVerificationQueue() {
        User currentUser = userService.getCurrentUser();
        if (!isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        return ResponseEntity.ok(modService.getPendingProjects());
    }

    @GetMapping("/projects/{id}/review-details")
    public ResponseEntity<?> getProjectReviewDetails(@PathVariable String id) {
        User currentUser = userService.getCurrentUser();
        if (!isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        Mod mod = modService.getModById(id);
        if (mod == null) return ResponseEntity.notFound().build();

        User author = userRepository.findByUsername(mod.getAuthor()).orElse(null);
        Map<String, Object> authorStats = Map.of(
                "accountAge", author != null ? author.getCreatedAt() : "Unknown",
                "tier", author != null ? author.getTier() : "Unknown",
                "totalProjects", author != null ? modService.getCreatorProjects(author.getUsername(), org.springframework.data.domain.Pageable.unpaged()).getTotalElements() : 0
        );

        return ResponseEntity.ok(Map.of(
                "mod", mod,
                "authorStats", authorStats
        ));
    }

    @PostMapping("/projects/{id}/reject")
    public ResponseEntity<?> rejectProject(@PathVariable String id, @RequestBody Map<String, String> body) {
        User currentUser = userService.getCurrentUser();
        if (!isAdmin(currentUser)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        try {
            modService.rejectMod(id, body.get("reason"));
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}