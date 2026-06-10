package net.modtale.controller.user;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.modtale.exception.ResourceNotFoundException;
import net.modtale.exception.UnauthorizedException;
import net.modtale.exception.UpstreamServiceException;
import net.modtale.mapper.UserResponseMapper;
import net.modtale.model.dto.user.GitRepositoryDTO;
import net.modtale.model.user.User;
import net.modtale.service.user.GithubService;
import net.modtale.service.user.GitlabService;
import net.modtale.service.user.OrganizationService;
import net.modtale.service.user.AccountService;
import net.modtale.service.user.ConnectionSessionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ConnectionController {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionController.class);

    private final AccountService accountService;
    private final OrganizationService organizationService;
    private final GithubService githubService;
    private final GitlabService gitlabService;
    private final ConnectionSessionService connectionSessionService;

    public ConnectionController(
            AccountService accountService,
            OrganizationService organizationService,
            GithubService githubService,
            GitlabService gitlabService,
            ConnectionSessionService connectionSessionService
    ) {
        this.accountService = accountService;
        this.organizationService = organizationService;
        this.githubService = githubService;
        this.gitlabService = gitlabService;
        this.connectionSessionService = connectionSessionService;
    }

    @PostMapping("/user/connections/{provider}/toggle-visibility")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<Void> toggleVisibility(@PathVariable String provider) {
        User user = accountService.requireCurrentUser("changing connection visibility");
        accountService.toggleConnectionVisibility(user.getId(), provider);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/user/connections/{provider}")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<Void> unlinkAccount(@PathVariable String provider) {
        User user = accountService.requireCurrentUser("unlinking a connected account");
        accountService.unlinkAccount(user.getId(), provider);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/user/repos/github")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<List<GitRepositoryDTO>> getGithubRepos(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        User user = accountService.requireCurrentUser("loading GitHub repositories");

        String accessToken = user.getGithubAccessToken();

        if (accessToken == null) {
            accessToken = connectionSessionService.loadAuthorizedClientAccessToken(
                    "github",
                    authentication,
                    request,
                    "We couldn't access your GitHub connection session."
            );
        }

        if (accessToken == null) {
            throw new UnauthorizedException("No GitHub connection is available for this account. Link GitHub first, then try again.");
        }

        try {
            return ResponseEntity.ok(githubService.getUserRepos(accessToken).stream()
                    .map(UserResponseMapper::toGitRepositoryDTO)
                    .toList());
        } catch (HttpClientErrorException.Unauthorized e) {
            accountService.unlinkAccount(user.getId(), "github");
            connectionSessionService.clearAuthorizedClient("github", authentication, request, response, user.getId());
            throw new UnauthorizedException("Your GitHub connection is no longer valid. Reconnect GitHub and then try loading repositories again.");
        }
    }

    @GetMapping("/user/repos/gitlab")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<List<GitRepositoryDTO>> getGitlabRepos(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        User user = accountService.requireCurrentUser("loading GitLab repositories");

        String accessToken = user.getGitlabAccessToken();

        if (accessToken == null) {
            accessToken = connectionSessionService.loadAuthorizedClientAccessToken(
                    "gitlab",
                    authentication,
                    request,
                    "We couldn't access your GitLab connection session."
            );
        }

        if (accessToken == null) {
            throw new UnauthorizedException("No GitLab connection is available for this account. Link GitLab first, then try again.");
        }

        try {
            return ResponseEntity.ok(gitlabService.getUserRepos(accessToken).stream()
                    .map(UserResponseMapper::toGitRepositoryDTO)
                    .toList());
        } catch (HttpClientErrorException.Unauthorized e) {
            accountService.unlinkAccount(user.getId(), "gitlab");
            connectionSessionService.clearAuthorizedClient("gitlab", authentication, request, response, user.getId());
            throw new UnauthorizedException("Your GitLab connection is no longer valid. Reconnect GitLab and then try loading repositories again.");
        } catch (org.springframework.web.client.RestClientException e) {
            throw new UpstreamServiceException(HttpStatus.BAD_GATEWAY, "Failed to load GitLab repositories.", e);
        }
    }

    @GetMapping("/user/repos")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<List<GitRepositoryDTO>> getMyRepos(Authentication authentication, HttpServletRequest request, HttpServletResponse response) {
        return getGithubRepos(authentication, request, response);
    }

    @PostMapping("/orgs/{orgId}/link/prepare")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<Void> prepareOrgLink(@PathVariable String orgId, HttpServletRequest request) {
        User user = accountService.requireCurrentUser("linking an account to an organization");
        organizationService.requireConnectionManagedOrganization(orgId, user);
        request.getSession().setAttribute("pending_org_link_id", orgId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/orgs/{orgId}/connections/{provider}/toggle-visibility")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<Void> toggleOrgConnectionVisibility(@PathVariable String orgId, @PathVariable String provider) {
        User user = accountService.requireCurrentUser("changing organization connection visibility");
        organizationService.toggleOrgConnectionVisibility(orgId, provider, user);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/orgs/{orgId}/connections/{provider}")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<Void> unlinkOrgAccount(@PathVariable String orgId, @PathVariable String provider) {
        User user = accountService.requireCurrentUser("unlinking an organization account");
        organizationService.unlinkOrgAccount(orgId, provider, user);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/orgs/{orgId}/repos/github")
    @PreAuthorize("@apiSecurity.hasOrgPerm(#orgId, 'ORG_CONNECTION_MANAGE', authentication)")
    public ResponseEntity<List<GitRepositoryDTO>> getOrgGithubRepos(@PathVariable String orgId) {
        User user = accountService.requireCurrentUser("loading organization repositories");
        User org = organizationService.requireConnectionManagedOrganization(orgId, user);

        String accessToken = org.getGithubAccessToken();
        if (accessToken == null) {
            throw new ResourceNotFoundException("This organization does not currently have a valid GitHub connection.");
        }

        try {
            return ResponseEntity.ok(githubService.getUserRepos(accessToken).stream()
                    .map(UserResponseMapper::toGitRepositoryDTO)
                    .toList());
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new UnauthorizedException("This organization's GitHub connection is no longer valid. Reconnect GitHub and then try again.");
        }
    }

}
