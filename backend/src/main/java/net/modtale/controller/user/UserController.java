package net.modtale.controller.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import net.modtale.mapper.ProjectMapper;
import net.modtale.mapper.UserMapper;
import net.modtale.model.dto.project.ProjectSummaryDTO;
import net.modtale.model.dto.request.user.UpdateProfileRequest;
import net.modtale.model.dto.request.user.UsersBatchRequest;
import net.modtale.model.dto.user.UserDTO;
import net.modtale.model.dto.user.UserSummaryDTO;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.SearchService;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.storage.StorageService;
import net.modtale.service.security.FileValidationService;
import net.modtale.service.user.AccountService;
import net.modtale.service.social.SocialService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class UserController {

    @Autowired private AccountService accountService;
    @Autowired private SocialService socialService;
    @Autowired private UserRepository userRepository;
    @Autowired private SearchService searchService;
    @Autowired private AccessControlService AccessControlService;
    @Autowired private StorageService storageService;
    @Autowired private FileValidationService validationService;

    @GetMapping("/users/search")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROFILE_READ', authentication)")
    public ResponseEntity<List<UserSummaryDTO>> searchUsers(@RequestParam String query) {
        List<User> users = accountService.searchUsers(query);
        return ResponseEntity.ok(users.stream()
                .map(UserMapper::toSummaryDTO)
                .collect(Collectors.toList()));
    }

    @PostMapping("/users/batch")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROFILE_READ', authentication)")
    public ResponseEntity<List<UserSummaryDTO>> getUsersBatch(@RequestBody UsersBatchRequest requestPayload) {
        List<String> userIds = requestPayload.getUserIds();
        if (userIds == null || userIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<User> users = accountService.getPublicProfilesByIds(userIds);
        return ResponseEntity.ok(users.stream()
                .map(UserMapper::toSummaryDTO)
                .collect(Collectors.toList()));
    }

    @GetMapping("/users/lookup/{username}")
    public ResponseEntity<Map<String, String>> lookupUserId(@PathVariable String username) {
        Optional<User> target = userRepository.findByUsernameIgnoreCase(username);
        if (target.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(Map.of("id", target.get().getId()));
    }

    @GetMapping("/user/me")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<UserDTO> getCurrentUser() {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(UserMapper.toDTO(user, true));
    }

    @DeleteMapping("/user/me")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_DELETE', authentication)")
    public ResponseEntity<?> deleteAccount(HttpServletRequest request) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            accountService.deleteUser(user.getId());
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to delete account.");
        }
    }

    @GetMapping("/user/profile/{userId}")
    public ResponseEntity<UserDTO> getUserProfile(@PathVariable String userId) {
        User user = accountService.getPublicProfile(userId);
        if (user == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(UserMapper.toDTO(user, false));
    }

    @PutMapping("/user/profile")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_EDIT_BASIC', authentication)")
    public ResponseEntity<?> updateProfile(@RequestBody UpdateProfileRequest requestPayload, HttpServletRequest request) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        String bio = requestPayload.getBio();
        String username = requestPayload.getUsername();

        if (bio != null && bio.length() > 300) {
            return ResponseEntity.badRequest().body("Bio cannot exceed 300 characters.");
        }

        try {
            User updated = accountService.updateUserProfile(user.getId(), bio, username);

            if (username != null && !username.equals(user.getUsername())) {
                Authentication newAuth = new UsernamePasswordAuthenticationToken(
                        updated,
                        null,
                        updated.getRoles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).collect(Collectors.toList())
                );
                SecurityContextHolder.getContext().setAuthentication(newAuth);
                HttpSession session = request.getSession(false);
                if (session != null) {
                    session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, SecurityContextHolder.getContext());
                }
            }

            return ResponseEntity.ok(UserMapper.toDTO(updated, true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/user/profile/avatar")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_EDIT_AVATAR', authentication)")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            validationService.validateIcon(file);
            String path = storageService.upload(file, "avatars/" + user.getUsername());
            String url = storageService.getPublicUrl(path);
            accountService.updateUserAvatar(user.getId(), url);
            return ResponseEntity.ok(url);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to upload avatar");
        }
    }

    @PostMapping("/user/profile/banner")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_EDIT_BANNER', authentication)")
    public ResponseEntity<?> uploadBanner(@RequestParam("file") MultipartFile file) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            validationService.validateBanner(file);
            String path = storageService.upload(file, "banners/" + user.getUsername());
            String url = storageService.getPublicUrl(path);
            accountService.updateUserBanner(user.getId(), url);
            return ResponseEntity.ok(url);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to upload banner");
        }
    }

    @PutMapping("/user/settings/notifications")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_NOTIFICATION_MANAGE', authentication)")
    public ResponseEntity<?> updateNotificationSettings(@RequestBody User.NotificationPreferences prefs) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        accountService.updateNotificationPreferences(user.getId(), prefs);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/user/follow/{targetId}")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_FOLLOW', authentication)")
    public ResponseEntity<?> followUser(@PathVariable String targetId) {
        User currentUser = accountService.getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(401).build();
        try {
            socialService.followUser(currentUser.getId(), targetId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/user/unfollow/{targetId}")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_UNFOLLOW', authentication)")
    public ResponseEntity<?> unfollowUser(@PathVariable String targetId) {
        User currentUser = accountService.getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(401).build();
        try {
            socialService.unfollowUser(currentUser.getId(), targetId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/users/{userId}/following")
    public ResponseEntity<List<UserSummaryDTO>> getUserFollowing(@PathVariable String userId) {
        List<User> following = socialService.getFollowing(userId);
        return ResponseEntity.ok(following.stream()
                .map(UserMapper::toSummaryDTO)
                .collect(Collectors.toList()));
    }

    @GetMapping("/users/{userId}/followers")
    public ResponseEntity<List<UserSummaryDTO>> getUserFollowers(@PathVariable String userId) {
        List<User> followers = socialService.getFollowers(userId);
        return ResponseEntity.ok(followers.stream()
                .map(UserMapper::toSummaryDTO)
                .collect(Collectors.toList()));
    }

    @GetMapping("/creators/search")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROFILE_READ', authentication)")
    public ResponseEntity<List<UserSummaryDTO>> searchCreators(@RequestParam String query) {
        List<User> creators = userRepository.findByUsernameContainingIgnoreCase(query, PageRequest.of(0, 10));
        return ResponseEntity.ok(creators.stream()
                .map(UserMapper::toSummaryDTO)
                .collect(Collectors.toList()));
    }

    @GetMapping("/creators/{userId}/projects")
    public ResponseEntity<Page<ProjectSummaryDTO>> getCreatorProjects(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        User currentUser = accountService.getCurrentUser();
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        boolean hasPrivilege = false;

        if (currentUser != null) {
            if (currentUser.getId().equals(targetUser.getId())) {
                hasPrivilege = true;
            } else if (targetUser.getAccountType() == User.AccountType.ORGANIZATION) {
                hasPrivilege = targetUser.getOrganizationMembers().stream()
                        .anyMatch(m -> m.getUserId().equals(currentUser.getId()));
            }
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());

        Page<Project> pageResult;
        if (hasPrivilege) {
            pageResult = searchService.getPrivilegedCreatorProjects(targetUser.getId(), pageable);
        } else {
            pageResult = searchService.getCreatorProjects(targetUser.getId(), pageable);
        }

        if (currentUser != null) {
            pageResult.getContent().forEach(p -> {
                p.setCanEdit(AccessControlService.hasEditPermission(p, currentUser));
                p.setIsOwner(AccessControlService.isOwner(p, currentUser));
            });
        }

        final boolean includeManagementFields = hasPrivilege;
        return ResponseEntity.ok(pageResult.map(p -> ProjectMapper.toSummaryDTO(p, includeManagementFields)));
    }

    @GetMapping("/projects/user/contributed")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROJECT_READ', authentication)")
    public ResponseEntity<Page<ProjectSummaryDTO>> getContributedProjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        Page<Project> pageResult = searchService.getContributedProjects(user.getId(), PageRequest.of(page, size, Sort.by("updatedAt").descending()));

        pageResult.getContent().forEach(p -> {
            p.setCanEdit(AccessControlService.hasEditPermission(p, user));
            p.setIsOwner(AccessControlService.isOwner(p, user));
        });

        return ResponseEntity.ok(pageResult.map(p -> ProjectMapper.toSummaryDTO(p, true)));
    }
}
