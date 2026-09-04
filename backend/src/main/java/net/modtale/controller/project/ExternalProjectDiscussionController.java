package net.modtale.controller.project;

import jakarta.validation.Valid;
import net.modtale.exception.ApiKeyOperationForbiddenException;
import net.modtale.model.dto.project.ProjectCommentsDTO;
import net.modtale.model.dto.request.project.CommentRequest;
import net.modtale.model.user.User;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.social.ExternalProjectDiscussionService;
import net.modtale.service.user.account.AccountService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/external/{provider}/{externalProjectId}/comments")
public class ExternalProjectDiscussionController {
    private final ExternalProjectDiscussionService discussionService;
    private final AccountService accountService;
    private final AccessControlService accessControlService;

    public ExternalProjectDiscussionController(
            ExternalProjectDiscussionService discussionService,
            AccountService accountService,
            AccessControlService accessControlService
    ) {
        this.discussionService = discussionService;
        this.accountService = accountService;
        this.accessControlService = accessControlService;
    }

    @GetMapping
    public ResponseEntity<ProjectCommentsDTO> getComments(
            @PathVariable String provider,
            @PathVariable String externalProjectId,
            Authentication authentication
    ) {
        User user = accountService.getCurrentUser(authentication);
        return ResponseEntity.ok()
                .cacheControl(user == null ? CacheControl.noCache() : CacheControl.noStore())
                .body(discussionService.getComments(provider, externalProjectId, user == null ? null : user.getId()));
    }

    @PostMapping
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<Void> addComment(
            @PathVariable String provider,
            @PathVariable String externalProjectId,
            @Valid @RequestBody CommentRequest request,
            Authentication authentication
    ) {
        User user = requireUser(authentication, "posting comments");
        discussionService.addComment(provider, externalProjectId, user.getId(), request.getContent());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{commentId}")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<Void> editComment(
            @PathVariable String provider,
            @PathVariable String externalProjectId,
            @PathVariable String commentId,
            @Valid @RequestBody CommentRequest request,
            Authentication authentication
    ) {
        User user = requireUser(authentication, "editing comments");
        discussionService.editComment(provider, externalProjectId, commentId, user.getId(), request.getContent());
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{commentId}")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<Void> deleteComment(
            @PathVariable String provider,
            @PathVariable String externalProjectId,
            @PathVariable String commentId,
            Authentication authentication
    ) {
        User user = requireUser(authentication, "deleting comments");
        discussionService.deleteComment(provider, externalProjectId, commentId, user.getId(),
                accessControlService.hasAdminPermission("PROJECT_MODERATE", authentication));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{commentId}/vote")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<Void> voteComment(
            @PathVariable String provider,
            @PathVariable String externalProjectId,
            @PathVariable String commentId,
            @RequestParam boolean upvote,
            Authentication authentication
    ) {
        User user = requireUser(authentication, "voting on comments");
        discussionService.voteComment(provider, externalProjectId, commentId, user.getId(), upvote);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{commentId}/pin")
    @PreAuthorize("@apiSecurity.hasAdminPermission('PROJECT_MODERATE', authentication)")
    public ResponseEntity<Void> setPinned(
            @PathVariable String provider,
            @PathVariable String externalProjectId,
            @PathVariable String commentId,
            @RequestParam boolean pinned,
            Authentication authentication
    ) {
        requireUser(authentication, "pinning comments");
        discussionService.setPinned(provider, externalProjectId, commentId, pinned);
        return ResponseEntity.ok().build();
    }

    private User requireUser(Authentication authentication, String action) {
        if (accessControlService.isApiKey(authentication)) {
            throw new ApiKeyOperationForbiddenException("API keys cannot be used for " + action + ".");
        }
        return accountService.requireCurrentUser(authentication, action);
    }
}
