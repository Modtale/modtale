package net.modtale.controller.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.mapper.ProjectMapper;
import net.modtale.mapper.UserMapper;
import net.modtale.model.dto.project.ProjectSummaryDTO;
import net.modtale.model.dto.request.user.UpdateProfileRequest;
import net.modtale.model.dto.request.user.UsersBatchRequest;
import net.modtale.model.dto.response.common.ResourceUrlResponse;
import net.modtale.model.dto.user.UserDTO;
import net.modtale.model.dto.user.UserSummaryDTO;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.project.SearchService;
import net.modtale.service.security.AccessControlService;
import net.modtale.service.security.FileValidationService;
import net.modtale.service.social.SocialService;
import net.modtale.service.user.AccountService;
import net.modtale.service.media.MediaUploadService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
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

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class UserController {

    private final AccountService accountService;
    private final SocialService socialService;
    private final UserRepository userRepository;
    private final SearchService searchService;
    private final AccessControlService accessControlService;
    private final FileValidationService validationService;
    private final MediaUploadService mediaUploadService;

    public UserController(
            AccountService accountService,
            SocialService socialService,
            UserRepository userRepository,
            SearchService searchService,
            AccessControlService accessControlService,
            FileValidationService validationService,
            MediaUploadService mediaUploadService
    ) {
        this.accountService = accountService;
        this.socialService = socialService;
        this.userRepository = userRepository;
        this.searchService = searchService;
        this.accessControlService = accessControlService;
        this.validationService = validationService;
        this.mediaUploadService = mediaUploadService;
    }

    @GetMapping("/users/search")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROFILE_READ', authentication)")
    public ResponseEntity<List<UserSummaryDTO>> searchUsers(@RequestParam String query) {
        List<User> users = accountService.searchUsers(query);
        return ResponseEntity.ok(users.stream().map(UserMapper::toSummaryDTO).collect(Collectors.toList()));
    }

    @PostMapping("/users/batch")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROFILE_READ', authentication)")
    public ResponseEntity<List<UserSummaryDTO>> getUsersBatch(@Valid @RequestBody UsersBatchRequest requestPayload) {
        List<String> userIds = requestPayload.getUserIds();
        if (userIds == null || userIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<User> users = accountService.getPublicProfilesByIds(userIds);
        return ResponseEntity.ok(users.stream().map(UserMapper::toSummaryDTO).collect(Collectors.toList()));
    }

    @GetMapping("/user/me")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<UserDTO> getCurrentUser(Authentication authentication) {
        User user = accountService.requireCurrentUser(authentication, "viewing your profile");
        return ResponseEntity.ok(UserMapper.toDTO(user, true));
    }

    @DeleteMapping("/user/me")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_DELETE', authentication)")
    public ResponseEntity<Void> deleteAccount(HttpServletRequest request, Authentication authentication) {
        User user = accountService.requireCurrentUser(authentication, "deleting your account");
        accountService.deleteUser(user.getId());

        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.ok().build();
    }

    @GetMapping("/user/profile/{userId}")
    public ResponseEntity<UserDTO> getUserProfile(@PathVariable String userId) {
        User user = accountService.getPublicProfile(userId);
        if (user == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(UserMapper.toDTO(user, false));
    }

    @PutMapping("/user/profile")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_EDIT_BASIC', authentication)")
    public ResponseEntity<UserDTO> updateProfile(
            @Valid @RequestBody UpdateProfileRequest requestPayload,
            HttpServletRequest request,
            Authentication authentication
    ) {
        User user = accountService.requireCurrentUser(authentication, "updating your profile");
        User updated = accountService.updateUserProfile(user.getId(), requestPayload.getBio(), requestPayload.getUsername());

        if (requestPayload.getUsername() != null && !requestPayload.getUsername().equals(user.getUsername())) {
            Authentication newAuth = new UsernamePasswordAuthenticationToken(
                    updated,
                    null,
                    updated.getRoles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).collect(Collectors.toList())
            );
            SecurityContextHolder.getContext().setAuthentication(newAuth);
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.setAttribute(
                        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY,
                        SecurityContextHolder.getContext()
                );
            }
        }

        return ResponseEntity.ok(UserMapper.toDTO(updated, true));
    }

    @PostMapping("/user/profile/avatar")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_EDIT_AVATAR', authentication)")
    public ResponseEntity<ResourceUrlResponse> uploadAvatar(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        User user = accountService.requireCurrentUser(authentication, "uploading an avatar");
        String url = mediaUploadService.uploadPublicUrl(file, "avatars/" + user.getUsername(), validationService::validateIcon);
        accountService.updateUserAvatar(user.getId(), url);
        return ResponseEntity.ok(new ResourceUrlResponse(url));
    }

    @PostMapping("/user/profile/banner")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_EDIT_BANNER', authentication)")
    public ResponseEntity<ResourceUrlResponse> uploadBanner(
            @RequestParam("file") MultipartFile file,
            Authentication authentication
    ) {
        User user = accountService.requireCurrentUser(authentication, "uploading a banner");
        String url = mediaUploadService.uploadPublicUrl(file, "banners/" + user.getUsername(), validationService::validateBanner);
        accountService.updateUserBanner(user.getId(), url);
        return ResponseEntity.ok(new ResourceUrlResponse(url));
    }

    @PutMapping("/user/settings/notifications")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_NOTIFICATION_MANAGE', authentication)")
    public ResponseEntity<Void> updateNotificationSettings(
            @RequestBody User.NotificationPreferences prefs,
            Authentication authentication
    ) {
        User user = accountService.requireCurrentUser(authentication, "updating notification settings");
        accountService.updateNotificationPreferences(user.getId(), prefs);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/user/follow/{targetId}")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_FOLLOW', authentication)")
    public ResponseEntity<Void> followUser(@PathVariable String targetId, Authentication authentication) {
        User currentUser = accountService.requireCurrentUser(authentication, "following a user");
        socialService.followUser(currentUser.getId(), targetId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/user/unfollow/{targetId}")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_UNFOLLOW', authentication)")
    public ResponseEntity<Void> unfollowUser(@PathVariable String targetId, Authentication authentication) {
        User currentUser = accountService.requireCurrentUser(authentication, "unfollowing a user");
        socialService.unfollowUser(currentUser.getId(), targetId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/users/{userId}/following")
    public ResponseEntity<List<UserSummaryDTO>> getUserFollowing(@PathVariable String userId) {
        List<User> following = socialService.getFollowing(userId);
        return ResponseEntity.ok(following.stream().map(UserMapper::toSummaryDTO).collect(Collectors.toList()));
    }

    @GetMapping("/users/{userId}/followers")
    public ResponseEntity<List<UserSummaryDTO>> getUserFollowers(@PathVariable String userId) {
        List<User> followers = socialService.getFollowers(userId);
        return ResponseEntity.ok(followers.stream().map(UserMapper::toSummaryDTO).collect(Collectors.toList()));
    }

    @GetMapping("/creators/search")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROFILE_READ', authentication)")
    public ResponseEntity<List<UserSummaryDTO>> searchCreators(@RequestParam String query) {
        List<User> creators = userRepository.findByUsernameContainingIgnoreCase(query, PageRequest.of(0, 10));
        return ResponseEntity.ok(creators.stream().map(UserMapper::toSummaryDTO).collect(Collectors.toList()));
    }

    @GetMapping("/creators/{userId}/projects")
    public ResponseEntity<Page<ProjectSummaryDTO>> getCreatorProjects(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        User currentUser = accountService.getCurrentUser(authentication);
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        boolean hasPrivilege = hasCreatorPrivilege(currentUser, targetUser);
        PageRequest pageable = PageRequest.of(page, size, Sort.by("updatedAt").descending());

        Page<Project> pageResult = hasPrivilege
                ? searchService.getPrivilegedCreatorProjects(targetUser.getId(), pageable)
                : searchService.getCreatorProjects(targetUser.getId(), pageable);

        if (currentUser != null) {
            pageResult.getContent().forEach(project -> {
                project.setCanEdit(accessControlService.hasEditPermission(project, currentUser));
                project.setIsOwner(accessControlService.isOwner(project, currentUser));
            });
        }

        final boolean includeManagementFields = hasPrivilege;
        return ResponseEntity.ok(pageResult.map(project -> ProjectMapper.toSummaryDTO(project, includeManagementFields)));
    }

    @GetMapping("/projects/user/contributed")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROJECT_READ', authentication)")
    public ResponseEntity<Page<ProjectSummaryDTO>> getContributedProjects(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            Authentication authentication
    ) {
        User user = accountService.requireCurrentUser(authentication, "viewing projects you contribute to");
        Page<Project> pageResult = searchService.getContributedProjects(
                user.getId(),
                PageRequest.of(page, size, Sort.by("updatedAt").descending())
        );

        pageResult.getContent().forEach(project -> {
            project.setCanEdit(accessControlService.hasEditPermission(project, user));
            project.setIsOwner(accessControlService.isOwner(project, user));
        });

        return ResponseEntity.ok(pageResult.map(project -> ProjectMapper.toSummaryDTO(project, true)));
    }

    private boolean hasCreatorPrivilege(User currentUser, User targetUser) {
        if (currentUser == null || targetUser == null) {
            return false;
        }
        if (currentUser.getId().equals(targetUser.getId())) {
            return true;
        }
        return targetUser.getAccountType() == User.AccountType.ORGANIZATION
                && targetUser.getOrganizationMembers() != null
                && targetUser.getOrganizationMembers().stream()
                .anyMatch(member -> member.getUserId().equals(currentUser.getId()));
    }
}
