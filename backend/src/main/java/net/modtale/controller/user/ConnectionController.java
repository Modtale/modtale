package net.modtale.controller.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.modtale.exception.ErrorMessageUtils;
import net.modtale.mapper.UserResponseMapper;
import net.modtale.model.dto.user.GitRepositoryDTO;
import net.modtale.model.user.GitRepository;
import net.modtale.model.user.User;
import net.modtale.service.user.GithubService;
import net.modtale.service.user.OrganizationService;
import net.modtale.service.user.AccountService;
import net.modtale.service.security.AccessControlService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class ConnectionController {

    @Autowired private AccountService accountService;
    @Autowired private OrganizationService organizationService;
    @Autowired private GithubService githubService;
    @Autowired private OAuth2AuthorizedClientRepository authorizedClientRepository;
    @Autowired private AccessControlService accessControlService;

    @PostMapping("/user/connections/{provider}/toggle-visibility")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<?> toggleVisibility(@PathVariable String provider) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before changing connection visibility.");
        try {
            accountService.toggleConnectionVisibility(user.getId(), provider);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ErrorMessageUtils.badRequest(e, "We could not update that connection visibility.");
        }
    }

    @DeleteMapping("/user/connections/{provider}")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<?> unlinkAccount(@PathVariable String provider) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before unlinking a connected account.");
        try {
            accountService.unlinkAccount(user.getId(), provider);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ErrorMessageUtils.badRequest(e, "We could not unlink that connected account.");
        }
    }

    @GetMapping("/user/repos/github")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<?> getGithubRepos(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before loading GitHub repositories.");

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
            return ErrorMessageUtils.unauthorized("No GitHub connection is available for this account. Link GitHub first, then try again.");
        }

        try {
            return ResponseEntity.ok(githubService.getUserRepos(accessToken).stream()
                    .map(UserResponseMapper::toGitRepositoryDTO)
                    .toList());
        } catch (HttpClientErrorException.Unauthorized e) {
            accountService.unlinkAccount(user.getId(), "github");
            try {
                authorizedClientRepository.removeAuthorizedClient("github", authentication, request, response);
            } catch (Exception ignored) {}
            return ErrorMessageUtils.unauthorized("Your GitHub connection is no longer valid. Reconnect GitHub and then try loading repositories again.");
        }
    }

    @GetMapping("/user/repos/gitlab")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<?> getGitlabRepos(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before loading GitLab repositories.");

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
            return ErrorMessageUtils.unauthorized("No GitLab connection is available for this account. Link GitLab first, then try again.");
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

            return ResponseEntity.ok(allRepos.stream()
                    .map(UserResponseMapper::toGitRepositoryDTO)
                    .toList());
        } catch (HttpClientErrorException.Unauthorized e) {
            accountService.unlinkAccount(user.getId(), "gitlab");
            try {
                authorizedClientRepository.removeAuthorizedClient("gitlab", authentication, request, response);
            } catch (Exception ignored) {}
            return ErrorMessageUtils.unauthorized("Your GitLab connection is no longer valid. Reconnect GitLab and then try loading repositories again.");
        } catch (Exception e) {
            return ErrorMessageUtils.internalServerError(e, "Failed to load GitLab repositories.");
        }
    }

    @GetMapping("/user/repos")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<?> getMyRepos(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return getGithubRepos(authentication, request, response);
    }

    @PostMapping("/orgs/{orgId}/link/prepare")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<?> prepareOrgLink(@PathVariable String orgId, HttpServletRequest request) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before linking an account to an organization.");

        List<User> userOrgs = organizationService.getUserOrganizations(user.getId());
        User org = userOrgs.stream().filter(o -> o.getId().equals(orgId)).findFirst().orElse(null);

        if (org == null || !accessControlService.hasOrgPermission(org, user.getId(), net.modtale.model.user.ApiKey.ApiPermission.ORG_CONNECTION_MANAGE)) {
            return ErrorMessageUtils.forbidden("You do not have permission to manage connected accounts for this organization.");
        }

        request.getSession().setAttribute("pending_org_link_id", orgId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/orgs/{orgId}/connections/{provider}/toggle-visibility")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<?> toggleOrgConnectionVisibility(@PathVariable String orgId, @PathVariable String provider) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before changing organization connection visibility.");
        try {
            organizationService.toggleOrgConnectionVisibility(orgId, provider, user);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ErrorMessageUtils.forbidden(e, "You do not have permission to manage connections for this organization.");
        } catch (IllegalArgumentException e) {
            return ErrorMessageUtils.badRequest(e, "We could not update that organization connection visibility.");
        }
    }

    @DeleteMapping("/orgs/{orgId}/connections/{provider}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<?> unlinkOrgAccount(@PathVariable String orgId, @PathVariable String provider) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before unlinking an organization account.");
        try {
            organizationService.unlinkOrgAccount(orgId, provider, user);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ErrorMessageUtils.forbidden(e, "You do not have permission to unlink accounts from this organization.");
        } catch (IllegalArgumentException e) {
            return ErrorMessageUtils.badRequest(e, "We could not unlink that organization account.");
        }
    }

    @GetMapping("/orgs/{orgId}/repos/github")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<?> getOrgGithubRepos(@PathVariable String orgId) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before loading organization repositories.");

        List<User> userOrgs = organizationService.getUserOrganizations(user.getId());
        User org = userOrgs.stream().filter(o -> o.getId().equals(orgId)).findFirst().orElse(null);

        if (org == null) return ErrorMessageUtils.forbidden("You do not have access to this organization.");

        String accessToken = org.getGithubAccessToken();
        if (accessToken == null) return ErrorMessageUtils.notFound("This organization does not currently have a valid GitHub connection.");

        try {
            return ResponseEntity.ok(githubService.getUserRepos(accessToken).stream()
                    .map(UserResponseMapper::toGitRepositoryDTO)
                    .toList());
        } catch (HttpClientErrorException.Unauthorized e) {
            return ErrorMessageUtils.unauthorized("This organization's GitHub connection is no longer valid. Reconnect GitHub and then try again.");
        }
    }
}
