package net.modtale.controller;

import net.modtale.model.jam.Modjam;
import net.modtale.model.jam.ModjamSubmission;
import net.modtale.model.user.User;
import net.modtale.service.ModjamService;
import net.modtale.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/modjams")
public class ModjamController {

    @Autowired private ModjamService modjamService;
    @Autowired private UserService userService;

    @GetMapping
    public ResponseEntity<List<Modjam>> getAllJams() {
        return ResponseEntity.ok(modjamService.getAllJams());
    }

    @GetMapping("/user/me")
    public ResponseEntity<List<Modjam>> getMyJams() {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.getUserHostedJams(user.getId()));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<Modjam> getJamBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(modjamService.getJamBySlug(slug));
    }

    @PostMapping
    public ResponseEntity<Modjam> createJam(@RequestBody Modjam jam) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.createJam(jam, user.getId(), user.getUsername()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Modjam> updateJam(@PathVariable String id, @RequestBody Modjam jam) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.updateJam(id, jam));
    }

    @PutMapping("/{id}/icon")
    public ResponseEntity<?> updateIcon(@PathVariable String id, @RequestParam("file") MultipartFile file) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            modjamService.updateIcon(id, file);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PutMapping("/{id}/banner")
    public ResponseEntity<?> updateBanner(@PathVariable String id, @RequestParam("file") MultipartFile file) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        try {
            modjamService.updateBanner(id, file);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteJam(@PathVariable String id) {
        User user = userService.getCurrentUser();
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
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.participate(jamId, user.getId()));
    }

    @PostMapping("/{jamId}/leave")
    public ResponseEntity<Modjam> leaveJam(@PathVariable String jamId) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.leaveJam(jamId, user.getId()));
    }

    @PostMapping("/{jamId}/submit")
    public ResponseEntity<ModjamSubmission> submitProject(@PathVariable String jamId, @RequestBody Map<String, String> body) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.submitProject(jamId, body.get("projectId"), user.getId()));
    }

    @PostMapping("/{jamId}/vote")
    public ResponseEntity<ModjamSubmission> vote(@PathVariable String jamId, @RequestBody Map<String, Object> body) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();

        String submissionId = (String) body.get("submissionId");
        String categoryId = (String) body.get("categoryId");
        int score = (Integer) body.get("score");

        return ResponseEntity.ok(modjamService.vote(jamId, submissionId, categoryId, score, user.getId()));
    }

    @PostMapping("/{jamId}/finalize")
    public ResponseEntity<Modjam> finalizeJam(@PathVariable String jamId, @RequestBody List<Map<String, String>> winners) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.finalizeJam(jamId, user.getId(), winners));
    }

    // Judging Endpoints
    @PostMapping("/{jamId}/judges/invite")
    public ResponseEntity<Modjam> inviteJudge(@PathVariable String jamId, @RequestBody Map<String, String> body) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.inviteJudge(jamId, body.get("username"), user.getId()));
    }

    @PostMapping("/{jamId}/judges/accept")
    public ResponseEntity<Modjam> acceptJudge(@PathVariable String jamId) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.acceptJudgeInvite(jamId, user.getId(), user.getUsername()));
    }

    @PostMapping("/{jamId}/judges/decline")
    public ResponseEntity<Modjam> declineJudge(@PathVariable String jamId) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.declineJudgeInvite(jamId, user.getUsername()));
    }

    @DeleteMapping("/{jamId}/judges/{username}")
    public ResponseEntity<Modjam> removeJudge(@PathVariable String jamId, @PathVariable String username) {
        User user = userService.getCurrentUser();
        if (user == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(modjamService.removeJudge(jamId, username, user.getId()));
    }
}