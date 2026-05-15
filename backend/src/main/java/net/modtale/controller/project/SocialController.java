package net.modtale.controller.project;

import net.modtale.model.user.User;
import net.modtale.service.social.SocialService;
import net.modtale.service.user.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/projects")
public class SocialController {

    @Autowired private SocialService socialService;
    @Autowired private AccountService accountService;

    @PostMapping("/{id}/favorite")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'PROJECT_FAVORITE', authentication)")
    public ResponseEntity<?> toggleFavorite(@PathVariable String id) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        socialService.toggleFavorite(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/comments")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'COMMENT_CREATE', authentication)")
    public ResponseEntity<?> addComment(@PathVariable String id, @RequestBody Map<String, Object> body) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try { socialService.addComment(id, user.getId(), (String) body.get("content")); return ResponseEntity.ok().build(); }
        catch (IllegalStateException e) { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage()); }
    }

    @PutMapping("/{id}/comments/{commentId}")
    @PreAuthorize("@apiSecurity.hasProjectPerm(#id, 'COMMENT_EDIT', authentication)")
    public ResponseEntity<?> editComment(@PathVariable String id, @PathVariable String commentId, @RequestBody Map<String, Object> body) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try { socialService.editComment(id, commentId, user.getId(), (String) body.get("content")); return ResponseEntity.ok().build(); }
        catch (SecurityException e) { return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage()); }
    }

    @PostMapping("/{id}/comments/{commentId}/vote")
    public ResponseEntity<?> voteComment(@PathVariable String id, @PathVariable String commentId, @RequestParam boolean upvote) {
        User user = accountService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        socialService.voteComment(id, commentId, user.getId(), upvote);
        return ResponseEntity.ok().build();
    }
}