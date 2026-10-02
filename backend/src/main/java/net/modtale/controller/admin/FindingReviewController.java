package net.modtale.controller.admin;

import net.modtale.service.security.issue.FindingReviewService;
import net.modtale.service.user.account.AccountService;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/projects/{projectId}/versions/{versionId}/finding-decisions")
public class FindingReviewController {
    private final FindingReviewService reviews;
    private final AccountService accounts;
    public FindingReviewController(FindingReviewService reviews, AccountService accounts) {
        this.reviews = reviews; this.accounts = accounts;
    }
    @GetMapping
    @PreAuthorize("@apiSecurity.hasAdminPermission('PROJECT_REVIEW_READ', authentication)")
    public ResponseEntity<FindingReviewService.History> history(@PathVariable String projectId,
            @PathVariable String versionId, @RequestHeader("If-Match") String token,
            @RequestParam(defaultValue = "0") int offset) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.history(projectId, versionId, token, offset));
    }
    @PostMapping
    @PreAuthorize("@apiSecurity.hasAdminPermission('PROJECT_REVIEW_DECIDE', authentication)")
    public ResponseEntity<FindingReviewService.Event> record(@PathVariable String projectId,
            @PathVariable String versionId, @RequestHeader("If-Match") String token,
            @RequestBody FindingReviewService.Request request) {
        var actor = accounts.requireCurrentUser("recording finding decisions");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.record(projectId, versionId, token, actor.getId(), request));
    }
    public record Revocation(String rationale) {}
    @PostMapping("/{decisionId}/revoke")
    @PreAuthorize("@apiSecurity.hasAdminPermission('PROJECT_REVIEW_DECIDE', authentication)")
    public ResponseEntity<FindingReviewService.Event> revoke(@PathVariable String projectId,
            @PathVariable String versionId, @PathVariable String decisionId,
            @RequestHeader("If-Match") String token, @RequestBody Revocation request) {
        var actor = accounts.requireCurrentUser("revoking finding decisions");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(reviews.revoke(projectId, versionId, token, actor.getId(), decisionId, request.rationale()));
    }
}
