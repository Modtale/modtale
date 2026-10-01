package net.modtale.controller;

import jakarta.validation.Valid;
import net.modtale.model.dto.request.jam.VoteRequest;
import net.modtale.model.jam.Modjam;
import net.modtale.model.jam.ModjamSubmission;
import net.modtale.model.user.User;
import net.modtale.exception.ApiKeyOperationForbiddenException;
import net.modtale.service.ModjamService;
import net.modtale.service.jam.ModjamOrganizerService;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.user.account.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/modjams")
public class ModjamController {

    @Autowired private ModjamService modjamService;
    @Autowired private ModjamOrganizerService organizerService;
    @Autowired private AccountService accountService;
    @Autowired private AccessControlService accessControlService;

    private User getBrowserUser() {
        if (accessControlService.isApiKey(SecurityContextHolder.getContext().getAuthentication())) {
            throw new ApiKeyOperationForbiddenException("API keys cannot be used to manage or participate in modjams.");
        }
        return accountService.getCurrentUser();
    }

    @GetMapping
    public ResponseEntity<List<Modjam>> getAllJams() {
        return ResponseEntity.ok(modjamService.getAllJams());
    }

    @GetMapping("/user/me")
    public ResponseEntity<List<Modjam>> getMyJams() {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.getUserHostedJams(user.getId()));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<Modjam> getJamBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(modjamService.getJamBySlug(slug));
    }

    @PostMapping
    public ResponseEntity<Modjam> createJam(@RequestBody Modjam jam) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.createJam(jam, user.getId(), user.getUsername()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Modjam> updateJam(@PathVariable String id, @RequestBody Modjam jam) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.jamForViewer(modjamService.updateJam(id, jam, user.getId()), user));
    }

    @PutMapping("/{id}/icon")
    public ResponseEntity<?> updateIcon(@PathVariable String id, @RequestParam("file") MultipartFile file) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            modjamService.updateIcon(id, file, user.getId());
            return ResponseEntity.ok().build();
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/banner")
    public ResponseEntity<?> updateBanner(@PathVariable String id, @RequestParam("file") MultipartFile file) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            modjamService.updateBanner(id, file, user.getId());
            return ResponseEntity.ok().build();
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteJam(@PathVariable String id) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            modjamService.deleteJam(id, user.getId());
            return ResponseEntity.ok().build();
        } catch (ResponseStatusException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getReason());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/{jamId}/submissions")
    public ResponseEntity<List<ModjamSubmission>> getSubmissions(@PathVariable String jamId) {
        return ResponseEntity.ok(modjamService.getSubmissions(jamId));
    }

    @PostMapping("/{jamId}/participate")
    public ResponseEntity<Modjam> participate(@PathVariable String jamId) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.participate(jamId, user.getId()));
    }

    @PostMapping("/{jamId}/leave")
    public ResponseEntity<Modjam> leaveJam(@PathVariable String jamId) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.leaveJam(jamId, user.getId()));
    }

    @PostMapping("/{jamId}/submit")
    public ResponseEntity<ModjamSubmission> submitProject(@PathVariable String jamId, @RequestBody Map<String, String> body) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.submitProject(jamId, body.get("projectId"), user.getId()));
    }

    @PostMapping("/{jamId}/vote")
    public ResponseEntity<ModjamSubmission> vote(@PathVariable String jamId, @Valid @RequestBody VoteRequest body) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();

        return ResponseEntity.ok(modjamService.vote(jamId, body.submissionId(), body.categoryId(), body.score().intValueExact(), user.getId()));
    }

    @PostMapping("/{jamId}/finalize")
    public ResponseEntity<Modjam> finalizeJam(@PathVariable String jamId, @RequestBody List<Map<String, String>> winners) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.jamForViewer(modjamService.finalizeJam(jamId, user.getId(), winners), user));
    }

    // Judging Endpoints
    @PostMapping("/{jamId}/judges/invite")
    public ResponseEntity<Modjam> inviteJudge(@PathVariable String jamId, @RequestBody Map<String, String> body) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.inviteJudge(jamId, body.get("username"), user.getId()));
    }

    @PostMapping("/{jamId}/judges/accept")
    public ResponseEntity<Modjam> acceptJudge(@PathVariable String jamId) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.acceptJudgeInvite(jamId, user.getId(), user.getUsername()));
    }

    @PostMapping("/{jamId}/judges/decline")
    public ResponseEntity<Modjam> declineJudge(@PathVariable String jamId) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.declineJudgeInvite(jamId, user.getId(), user.getUsername()));
    }

    @DeleteMapping("/{jamId}/judges/{username}")
    public ResponseEntity<Modjam> removeJudge(@PathVariable String jamId, @PathVariable String username) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.removeJudge(jamId, username, user.getId()));
    }

    @PostMapping("/{jamId}/organizer-roles")
    public ResponseEntity<Modjam> saveOrganizerRole(@PathVariable String jamId, @RequestBody Modjam.OrganizerRole role) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.jamForViewer(organizerService.saveRole(jamId, user.getId(), role), user));
    }

    @DeleteMapping("/{jamId}/organizer-roles/{roleId}")
    public ResponseEntity<Modjam> deleteOrganizerRole(@PathVariable String jamId, @PathVariable String roleId) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.jamForViewer(organizerService.deleteRole(jamId, user.getId(), roleId), user));
    }

    @PostMapping("/{jamId}/organizers/invite")
    public ResponseEntity<Modjam> inviteOrganizer(@PathVariable String jamId, @RequestBody Map<String, String> input) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.jamForViewer(organizerService.invite(jamId, user.getId(), input.get("username"), input.get("roleId")), user));
    }

    @PostMapping("/{jamId}/organizers/accept")
    public ResponseEntity<Modjam> acceptOrganizer(@PathVariable String jamId) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.jamForViewer(organizerService.answerInvite(jamId, user.getId(), true), user));
    }

    @PostMapping("/{jamId}/organizers/decline")
    public ResponseEntity<Modjam> declineOrganizer(@PathVariable String jamId) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.jamForViewer(organizerService.answerInvite(jamId, user.getId(), false), user));
    }

    @DeleteMapping("/{jamId}/organizers/{userId}")
    public ResponseEntity<Modjam> removeOrganizer(@PathVariable String jamId, @PathVariable String userId) {
        User user = getBrowserUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.jamForViewer(organizerService.remove(jamId, user.getId(), userId), user));
    }
}
