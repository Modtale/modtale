package net.modtale.controller.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import net.modtale.model.dto.UserDTO;
import net.modtale.model.user.User;
import net.modtale.model.user.GitRepository;
import net.modtale.service.user.UserService;
import net.modtale.service.user.GithubService;
import net.modtale.service.resources.StorageService;
import net.modtale.service.security.FileValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class UserController {

    @Autowired private UserService userService;
    @Autowired private GithubService githubService;
    @Autowired private StorageService storageService;
    @Autowired private OAuth2AuthorizedClientRepository authorizedClientRepository;
    @Autowired private FileValidationService validationService;

    @GetMapping("/users/search")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROFILE_READ', authentication)")
    public ResponseEntity<List<UserDTO>> searchUsers(@RequestParam String query) {
        List<User> users = userService.searchUsers(query);
        return ResponseEntity.ok(users.stream()
                .map(u -> UserDTO.fromEntity(u, false))
                .collect(Collectors.toList()));
    }

    @PostMapping("/users/batch")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROFILE_READ', authentication)")
    public ResponseEntity<List<UserDTO>> getUsersBatch(@RequestBody Map<String, List<String>> body) {
        List<String> userIds = body.get("userIds");
        if (userIds == null || userIds.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }
        List<User> users = userService.getPublicProfilesByIds(userIds);
        return ResponseEntity.ok(users.stream()
                .map(u -> UserDTO.fromEntity(u, false))
                .collect(Collectors.toList()));
    }

    @PostMapping("/orgs")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('ORG_CREATE', authentication)")
    public ResponseEntity<?> createOrganization(@RequestBody Map<String, String> payload) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        String name = payload.get("name");
        if (name == null || name.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Organization name is required.");
        }

        try {
            User org = userService.createOrganization(name, user);
            return ResponseEntity.ok(UserDTO.fromEntity(org, true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/user/orgs")
    @PreAuthorize("@apiSecurity.hasAnyPerm('ORG_READ', authentication)")
    public ResponseEntity<List<UserDTO>> getMyOrganizations() {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        List<User> orgs = userService.getUserOrganizations(user.getId());
        return ResponseEntity.ok(orgs.stream()
                .map(u -> UserDTO.fromEntity(u, true))
                .collect(Collectors.toList()));
    }

    @GetMapping("/users/{userId}/organizations")
    @PreAuthorize("@apiSecurity.hasAnyPerm('ORG_READ', authentication)")
    public ResponseEntity<List<UserDTO>> getUserOrganizations(@PathVariable String userId) {
        List<User> orgs = userService.getUserOrganizations(userId);
        return ResponseEntity.ok(orgs.stream()
                .map(u -> UserDTO.fromEntity(u, false))
                .collect(Collectors.toList()));
    }

    @GetMapping("/orgs/{orgId}/members")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_READ', authentication)")
    public ResponseEntity<?> getOrgMembers(@PathVariable String orgId) {
        try {
            return ResponseEntity.ok(userService.getOrganizationMembers(orgId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/orgs/{orgId}/members")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_INVITE', authentication)")
    public ResponseEntity<?> addOrgMember(@PathVariable String orgId, @RequestBody Map<String, String> payload) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        String targetUserId = payload.get("userId");
        String role = payload.getOrDefault("role", "MEMBER");

        try {
            userService.inviteOrganizationMember(orgId, targetUserId, role, user);
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
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        if (userId.equals(user.getId())) {
            List<User> userOrgs = userService.getUserOrganizations(user.getId());
            Optional<User> targetOrg = userOrgs.stream().filter(o -> o.getId().equals(orgId)).findFirst();
            if (targetOrg.isPresent()) {
                if (targetOrg.get().getOrganizationMembers().size() <= 1) {
                    return ResponseEntity.badRequest().body("You cannot leave the organization as you are the only member. Delete the organization instead.");
                }
            }
        }

        try {
            userService.removeOrganizationMember(orgId, userId, user);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/orgs/{orgId}/members/{userId}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_MEMBER_EDIT_ROLE', authentication)")
    public ResponseEntity<?> updateMemberRole(@PathVariable String orgId, @PathVariable String userId, @RequestBody Map<String, String> payload) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        String newRole = payload.get("role");
        if (newRole == null) return ResponseEntity.badRequest().body("Role is required.");

        try {
            userService.updateOrganizationMemberRole(orgId, userId, newRole, user);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/orgs/{orgId}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_EDIT_METADATA', authentication)")
    public ResponseEntity<?> updateOrganization(@PathVariable String orgId, @RequestBody Map<String, String> payload) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        String name = payload.get("displayName");
        if (name == null) name = payload.get("name");

        String bio = payload.get("bio");

        try {
            User updated = userService.updateOrganization(orgId, name, bio, user);
            return ResponseEntity.ok(UserDTO.fromEntity(updated, true));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/orgs/{orgId}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_DELETE', authentication)")
    public ResponseEntity<?> deleteOrganization(@PathVariable String orgId) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        try {
            userService.deleteOrganization(orgId, user);
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
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            validationService.validateIcon(file);
            String path = storageService.upload(file, "avatars/" + orgId);
            String url = storageService.getPublicUrl(path);
            userService.updateOrganizationAvatar(orgId, url, user);
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
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            validationService.validateBanner(file);
            String path = storageService.upload(file, "banners/" + orgId);
            String url = storageService.getPublicUrl(path);
            userService.updateOrganizationBanner(orgId, url, user);
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
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            userService.resolveOrgInvite(orgId, true, user);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/orgs/{orgId}/invite/decline")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('ORG_INVITE_DECLINE', authentication)")
    public ResponseEntity<?> declineOrgInvite(@PathVariable String orgId) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            userService.resolveOrgInvite(orgId, false, user);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/orgs/{orgId}/link/prepare")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<?> prepareOrgLink(@PathVariable String orgId, HttpServletRequest request) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        List<User> userOrgs = userService.getUserOrganizations(user.getId());
        boolean isAdmin = userOrgs.stream()
                .filter(o -> o.getId().equals(orgId))
                .flatMap(o -> o.getOrganizationMembers().stream())
                .anyMatch(m -> m.getUserId().equals(user.getId()) && "ADMIN".equals(m.getRole()));

        if (!isAdmin) return ResponseEntity.status(403).body("Insufficient permissions.");

        request.getSession().setAttribute("pending_org_link_id", orgId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/orgs/{orgId}/connections/{provider}/toggle-visibility")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<?> toggleOrgConnectionVisibility(@PathVariable String orgId, @PathVariable String provider) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            userService.toggleOrgConnectionVisibility(orgId, provider, user);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/orgs/{orgId}/connections/{provider}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<?> unlinkOrgAccount(@PathVariable String orgId, @PathVariable String provider) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            userService.unlinkOrgAccount(orgId, provider, user);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(e.getMessage());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/orgs/{orgId}/repos/github")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<List<GitRepository>> getOrgGithubRepos(@PathVariable String orgId) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        List<User> userOrgs = userService.getUserOrganizations(user.getId());
        User org = userOrgs.stream().filter(o -> o.getId().equals(orgId)).findFirst().orElse(null);

        if (org == null) return ResponseEntity.status(403).build();

        String accessToken = org.getGithubAccessToken();
        if (accessToken == null) return ResponseEntity.status(404).body(List.of());

        try {
            return ResponseEntity.ok(githubService.getUserRepos(accessToken));
        } catch (HttpClientErrorException.Unauthorized e) {
            return ResponseEntity.status(401).build();
        }
    }

    @GetMapping("/user/me")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<UserDTO> getCurrentUser() {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(UserDTO.fromEntity(user, true));
    }

    @DeleteMapping("/user/me")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_DELETE', authentication)")
    public ResponseEntity<?> deleteAccount(HttpServletRequest request) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        try {
            userService.deleteUser(user.getId());
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
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROFILE_READ', authentication)")
    public ResponseEntity<UserDTO> getUserProfile(@PathVariable String userId) {
        User user = userService.getPublicProfile(userId);
        if (user == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(UserDTO.fromEntity(user, false));
    }

    @PutMapping("/user/profile")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_EDIT_BASIC', authentication)")
    public ResponseEntity<?> updateProfile(@RequestBody Map<String, Object> payload, HttpServletRequest request) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        String bio = (String) payload.get("bio");
        String username = (String) payload.get("username");

        if (bio != null && bio.length() > 300) {
            return ResponseEntity.badRequest().body("Bio cannot exceed 300 characters.");
        }

        try {
            User updated = userService.updateUserProfile(user.getId(), bio, username);

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

            return ResponseEntity.ok(UserDTO.fromEntity(updated, true));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/user/profile/avatar")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_EDIT_AVATAR', authentication)")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            validationService.validateIcon(file);
            String path = storageService.upload(file, "avatars/" + user.getUsername());
            String url = storageService.getPublicUrl(path);
            userService.updateUserAvatar(user.getId(), url);
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
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            validationService.validateBanner(file);
            String path = storageService.upload(file, "banners/" + user.getUsername());
            String url = storageService.getPublicUrl(path);
            userService.updateUserBanner(user.getId(), url);
            return ResponseEntity.ok(url);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Failed to upload banner");
        }
    }

    @PostMapping("/user/connections/{provider}/toggle-visibility")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<?> toggleVisibility(@PathVariable String provider) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        userService.toggleConnectionVisibility(user.getId(), provider);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/user/connections/{provider}")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<?> unlinkAccount(@PathVariable String provider) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            userService.unlinkAccount(user.getId(), provider);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/user/settings/notifications")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_NOTIFICATION_MANAGE', authentication)")
    public ResponseEntity<?> updateNotificationSettings(@RequestBody User.NotificationPreferences prefs) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        userService.updateNotificationPreferences(user.getId(), prefs);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/user/follow/{targetId}")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_FOLLOW', authentication)")
    public ResponseEntity<?> followUser(@PathVariable String targetId) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(401).build();
        try {
            userService.followUser(currentUser.getId(), targetId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/user/unfollow/{targetId}")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_UNFOLLOW', authentication)")
    public ResponseEntity<?> unfollowUser(@PathVariable String targetId) {
        User currentUser = userService.getCurrentUser();
        if (currentUser == null) return ResponseEntity.status(401).build();
        try {
            userService.unfollowUser(currentUser.getId(), targetId);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/user/repos/github")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<List<GitRepository>> getGithubRepos(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String accessToken = user.getGithubAccessToken();

        if (accessToken == null) {
            try {
                OAuth2AuthorizedClient client = authorizedClientRepository.loadAuthorizedClient("github", authentication, request);
                if (client != null && client.getAccessToken() != null) {
                    accessToken = client.getAccessToken().getTokenValue();
                }
            } catch (Exception ignored) {}
        }

        if (accessToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(List.of());
        }

        try {
            return ResponseEntity.ok(githubService.getUserRepos(accessToken));
        } catch (HttpClientErrorException.Unauthorized e) {
            userService.unlinkAccount(user.getId(), "github");
            try {
                authorizedClientRepository.removeAuthorizedClient("github", authentication, request, response);
            } catch (Exception ignored) {}
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/user/repos/gitlab")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<List<GitRepository>> getGitlabRepos(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        String accessToken = user.getGitlabAccessToken();

        if (accessToken == null) {
            try {
                OAuth2AuthorizedClient client = authorizedClientRepository.loadAuthorizedClient("gitlab", authentication, request);
                if (client != null && client.getAccessToken() != null) {
                    accessToken = client.getAccessToken().getTokenValue();
                }
            } catch (Exception ignored) {}
        }

        if (accessToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(List.of());
        }

        try {
            RestTemplate restTemplate = new RestTemplate();
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + accessToken);

            List<GitRepository> allRepos = new ArrayList<>();
            int page = 1;
            boolean hasMore = true;

            while (hasMore) {
                String url = "https://gitlab.com/api/v4/projects?membership=true&min_access_level=30&order_by=updated_at&per_page=100&page=" + page;

                ResponseEntity<List<Map<String, Object>>> apiResponse = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        new ParameterizedTypeReference<List<Map<String, Object>>>() {}
                );

                List<Map<String, Object>> pageData = apiResponse.getBody();

                if (pageData != null && !pageData.isEmpty()) {
                    for (Map<String, Object> data : pageData) {
                        GitRepository repo = new GitRepository();
                        repo.setName((String) data.get("path_with_namespace"));
                        repo.setUrl((String) data.get("web_url"));
                        repo.setDescription((String) data.get("description"));

                        Object visibility = data.get("visibility");
                        if (visibility instanceof String) {
                            repo.setPrivate("private".equalsIgnoreCase((String) visibility) || "internal".equalsIgnoreCase((String) visibility));
                        } else {
                            repo.setPrivate(false);
                        }

                        allRepos.add(repo);
                    }

                    if (pageData.size() < 100) {
                        hasMore = false;
                    } else {
                        page++;
                    }
                } else {
                    hasMore = false;
                }
            }

            return ResponseEntity.ok(allRepos);
        } catch (HttpClientErrorException.Unauthorized e) {
            userService.unlinkAccount(user.getId(), "gitlab");
            try {
                authorizedClientRepository.removeAuthorizedClient("gitlab", authentication, request, response);
            } catch (Exception ignored) {}
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/user/repos")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<List<GitRepository>> getMyRepos(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return getGithubRepos(authentication, request, response);
    }

    @GetMapping("/users/{userId}/following")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROFILE_READ', authentication)")
    public ResponseEntity<List<UserDTO>> getUserFollowing(@PathVariable String userId) {
        List<User> following = userService.getFollowing(userId);
        return ResponseEntity.ok(following.stream()
                .map(u -> UserDTO.fromEntity(u, false))
                .collect(Collectors.toList()));
    }

    @GetMapping("/users/{userId}/followers")
    @PreAuthorize("@apiSecurity.hasAnyPerm('PROFILE_READ', authentication)")
    public ResponseEntity<List<UserDTO>> getUserFollowers(@PathVariable String userId) {
        List<User> followers = userService.getFollowers(userId);
        return ResponseEntity.ok(followers.stream()
                .map(u -> UserDTO.fromEntity(u, false))
                .collect(Collectors.toList()));
    }
}