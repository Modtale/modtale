package net.modtale.controller.project;

import jakarta.validation.Valid;
import net.modtale.model.dto.request.project.CommentRequest;
import net.modtale.model.user.User;
import net.modtale.service.social.SocialService;
import net.modtale.service.user.AccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
public class SocialController {

    private final SocialService socialService;
    private final AccountService accountService;

    public SocialController(SocialService socialService, AccountService accountService) {
        this.socialService = socialService;
        this.accountService = accountService;
    }

    @PostMapping("/{id}/favorite")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_FAVORITE', authentication)")
    public ResponseEntity<Void> toggleFavorite(@PathVariable String id) {
        User user = accountService.requireCurrentUser("favoriting a project");
        socialService.toggleFavorite(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'COMMENT_CREATE', authentication)")
    public ResponseEntity<Void> addComment(@PathVariable String id, @Valid @RequestBody CommentRequest requestPayload) {
        User user = accountService.requireCurrentUser("posting a comment");
        socialService.addComment(id, user.getId(), requestPayload.getContent());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/comments/{commentId}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'COMMENT_EDIT', authentication)")
    public ResponseEntity<Void> editComment(@PathVariable String id, @PathVariable String commentId, @Valid @RequestBody CommentRequest requestPayload) {
        User user = accountService.requireCurrentUser("editing a comment");
        socialService.editComment(id, commentId, user.getId(), requestPayload.getContent());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/comments/{commentId}/vote")
    @PreAuthorize("@apiSecurity.hasPersonalPerm('PROFILE_READ', authentication)")
    public ResponseEntity<Void> voteComment(@PathVariable String id, @PathVariable String commentId, @RequestParam boolean upvote) {
        User user = accountService.requireCurrentUser("voting on comments");
        socialService.voteComment(id, commentId, user.getId(), upvote);
        return ResponseEntity.ok().build();
    }
}
