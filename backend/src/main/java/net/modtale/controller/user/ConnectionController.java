package net.modtale.controller.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
        if (user == null) return ResponseEntity.status(401).build();
        accountService.toggleConnectionVisibility(user.getId(), provider);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/user/connections/{provider}")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<?> unlinkAccount(@PathVariable String provider) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            accountService.unlinkAccount(user.getId(), provider);
            return ResponseEntity.ok().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/user/repos/github")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<List<GitRepository>> getGithubRepos(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        User user = accountService.getCurrentUser();
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
            accountService.unlinkAccount(user.getId(), "github");
            try {
                authorizedClientRepository.removeAuthorizedClient("github", authentication, request, response);
            } catch (Exception ignored) {}
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }

    @GetMapping("/user/repos/gitlab")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<List<GitRepository>> getGitlabRepos(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        User user = accountService.getCurrentUser();
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
            accountService.unlinkAccount(user.getId(), "gitlab");
            try {
                authorizedClientRepository.removeAuthorizedClient("gitlab", authentication, request, response);
            } catch (Exception ignored) {}
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/user/repos")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<List<GitRepository>> getMyRepos(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return getGithubRepos(authentication, request, response);
    }

    @PostMapping("/orgs/{orgId}/link/prepare")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<?> prepareOrgLink(@PathVariable String orgId, HttpServletRequest request) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        List<User> userOrgs = organizationService.getUserOrganizations(user.getId());
        User org = userOrgs.stream().filter(o -> o.getId().equals(orgId)).findFirst().orElse(null);

        if (org == null || !accessControlService.hasOrgPermission(org, user.getId(), net.modtale.model.user.ApiKey.ApiPermission.ORG_CONNECTION_MANAGE)) {
            return ResponseEntity.status(403).body("Insufficient permissions.");
        }

        request.getSession().setAttribute("pending_org_link_id", orgId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/orgs/{orgId}/connections/{provider}/toggle-visibility")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<?> toggleOrgConnectionVisibility(@PathVariable String orgId, @PathVariable String provider) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            organizationService.toggleOrgConnectionVisibility(orgId, provider, user);
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
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            organizationService.unlinkOrgAccount(orgId, provider, user);
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
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        List<User> userOrgs = organizationService.getUserOrganizations(user.getId());
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
}