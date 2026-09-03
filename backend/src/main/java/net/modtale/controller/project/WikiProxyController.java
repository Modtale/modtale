package net.modtale.controller.project;

import jakarta.servlet.http.HttpServletRequest;
import net.modtale.exception.ErrorMessageUtils;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.UpstreamServiceException;
import net.modtale.model.user.User;
import net.modtale.service.project.media.WikiService;
import net.modtale.service.user.account.AccountService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wiki")
public class WikiProxyController {

    private static final String PUBLIC_WIKI_CACHE_CONTROL = "public, max-age=300, s-maxage=3600, stale-while-revalidate=86400";

    private final WikiService wikiService;
    private final AccountService accountService;

    public WikiProxyController(WikiService wikiService, AccountService accountService) {
        this.wikiService = wikiService;
        this.accountService = accountService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<String> getWikiProject(@PathVariable String id, Authentication authentication) {
        User currentUser = accountService.getCurrentUser(authentication);
        return wikiResponse(wikiService.getWikiProject(id, currentUser), currentUser);
    }

    @GetMapping({"/{id}/_bundle", "/{id}/_bundle/**"})
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<String> getWikiPageBundle(@PathVariable String id, HttpServletRequest request, Authentication authentication) {
        String pagePath = extractTrailingWikiPath(id, request, "/_bundle");
        User currentUser = accountService.getCurrentUser(authentication);
        return wikiResponse(wikiService.getWikiPageBundle(id, pagePath, currentUser), currentUser);
    }

    @GetMapping("/{id}/**")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<String> getWikiPage(@PathVariable String id, HttpServletRequest request, Authentication authentication) {
        String pagePath = extractTrailingWikiPath(id, request, "");
        User currentUser = accountService.getCurrentUser(authentication);
        return wikiResponse(wikiService.getWikiPage(id, pagePath, currentUser), currentUser);
    }

    private String extractTrailingWikiPath(String id, HttpServletRequest request, String suffix) {
        String path = request.getRequestURI();
        String searchStr = "/wiki/" + id + suffix;
        int index = path.indexOf(searchStr);
        if (index == -1) {
            throw new InvalidProjectRequestException("Invalid wiki path.");
        }
        String pagePath = path.substring(index + searchStr.length());
        return pagePath.startsWith("/") ? pagePath.substring(1) : pagePath;
    }

    private ResponseEntity<String> wikiResponse(String body, User currentUser) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON);

        if (currentUser == null) {
            builder.header(HttpHeaders.CACHE_CONTROL, PUBLIC_WIKI_CACHE_CONTROL);
        } else {
            builder.cacheControl(CacheControl.noCache());
        }

        return builder.body(body);
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ProblemDetail> handleWikiUpstream(UpstreamServiceException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ErrorMessageUtils.problemDetail(ex.getStatus(), ErrorMessageUtils.describe(ex, "Wiki upstream request failed.")));
    }
}
