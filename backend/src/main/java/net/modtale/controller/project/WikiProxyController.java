package net.modtale.controller.project;

import jakarta.servlet.http.HttpServletRequest;
import net.modtale.exception.ErrorMessageUtils;
import net.modtale.exception.InvalidProjectRequestException;
import net.modtale.exception.UpstreamServiceException;
import net.modtale.model.user.User;
import net.modtale.service.project.media.WikiService;
import net.modtale.service.user.account.AccountService;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping("/api/v1/wiki")
public class WikiProxyController {

    private final WikiService wikiService;
    private final AccountService accountService;

    public WikiProxyController(WikiService wikiService, AccountService accountService) {
        this.wikiService = wikiService;
        this.accountService = accountService;
    }

    @GetMapping("/{id}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<JsonNode> getWikiProject(@PathVariable String id, Authentication authentication) {
        User currentUser = accountService.getCurrentUser(authentication);
        return ResponseEntity.ok(wikiService.getWikiProject(id, currentUser));
    }

    @GetMapping("/{id}/**")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_READ', authentication)")
    public ResponseEntity<JsonNode> getWikiPage(@PathVariable String id, HttpServletRequest request, Authentication authentication) {
        String path = request.getRequestURI();
        String searchStr = "/wiki/" + id + "/";
        int index = path.indexOf(searchStr);
        if (index == -1) {
            throw new InvalidProjectRequestException("Invalid wiki path.");
        }
        String pagePath = path.substring(index + searchStr.length());
        User currentUser = accountService.getCurrentUser(authentication);
        return ResponseEntity.ok(wikiService.getWikiPage(id, pagePath, currentUser));
    }

    @ExceptionHandler(UpstreamServiceException.class)
    public ResponseEntity<ProblemDetail> handleWikiUpstream(UpstreamServiceException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ErrorMessageUtils.problemDetail(ex.getStatus(), ErrorMessageUtils.describe(ex, "Wiki upstream request failed.")));
    }
}
