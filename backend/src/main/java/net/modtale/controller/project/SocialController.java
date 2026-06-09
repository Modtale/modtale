package net.modtale.controller.project;

import net.modtale.exception.ErrorMessageUtils;
import net.modtale.model.dto.request.project.CommentRequest;
import net.modtale.model.user.User;
import net.modtale.service.social.SocialService;
import net.modtale.service.user.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projects")
public class SocialController {

    @Autowired private SocialService socialService;
    @Autowired private AccountService accountService;

    @PostMapping("/{id}/favorite")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_FAVORITE', authentication)")
    public ResponseEntity<?> toggleFavorite(@PathVariable String id) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before favoriting a project.");
        socialService.toggleFavorite(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'COMMENT_CREATE', authentication)")
    public ResponseEntity<?> addComment(@PathVariable String id, @RequestBody CommentRequest requestPayload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before posting a comment.");
        try { socialService.addComment(id, user.getId(), requestPayload.getContent()); return ResponseEntity.ok().build(); }
        catch (IllegalStateException e) { return ErrorMessageUtils.forbidden(e, "You cannot post a comment on this project right now."); }
        catch (IllegalArgumentException e) { return ErrorMessageUtils.badRequest(e, "We could not post that comment."); }
    }

    @PutMapping("/{id}/comments/{commentId}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'COMMENT_EDIT', authentication)")
    public ResponseEntity<?> editComment(@PathVariable String id, @PathVariable String commentId, @RequestBody CommentRequest requestPayload) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before editing a comment.");
        try { socialService.editComment(id, commentId, user.getId(), requestPayload.getContent()); return ResponseEntity.ok().build(); }
        catch (SecurityException e) { return ErrorMessageUtils.forbidden(e, "You do not have permission to edit this comment."); }
        catch (IllegalArgumentException e) { return ErrorMessageUtils.badRequest(e, "We could not update that comment."); }
    }

    @PostMapping("/{id}/comments/{commentId}/vote")
    public ResponseEntity<?> voteComment(@PathVariable String id, @PathVariable String commentId, @RequestParam boolean upvote) {
        User user = accountService.getCurrentUser();
        if (user == null) return ErrorMessageUtils.unauthorized("You need to sign in before voting on comments.");
        try {
            socialService.voteComment(id, commentId, user.getId(), upvote);
            return ResponseEntity.ok().build();
        } catch (SecurityException e) {
            return ErrorMessageUtils.forbidden(e, "You do not have permission to vote on this comment.");
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ErrorMessageUtils.badRequest(e, "We could not register that vote.");
        }
    }
}
