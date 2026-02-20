package net.modtale.service.resources;

import net.modtale.model.resources.Modjam;
import net.modtale.model.resources.ModjamSubmission;
import net.modtale.model.resources.Mod;
import net.modtale.model.user.User;
import net.modtale.repository.resources.ModjamRepository;
import net.modtale.repository.resources.ModjamSubmissionRepository;
import net.modtale.repository.resources.ModRepository;
import net.modtale.repository.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ModjamService {

    @Autowired private ModjamRepository modjamRepository;
    @Autowired private ModjamSubmissionRepository submissionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ModRepository modRepository;

    public List<Modjam> getAllJams() {
        return modjamRepository.findAll();
    }

    public Modjam getJamBySlug(String slug) {
        return modjamRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jam not found"));
    }

    public Modjam createJam(Modjam jam, String hostId, String hostName) {
        jam.setId(null);
        jam.setHostId(hostId);
        jam.setHostName(hostName);

        String baseSlug = jam.getTitle().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        jam.setSlug(baseSlug + "-" + UUID.randomUUID().toString().substring(0, 6));

        if (jam.getStartDate() != null && jam.getStartDate().isAfter(LocalDateTime.now())) {
            jam.setStatus("UPCOMING");
        } else {
            jam.setStatus("ACTIVE");
        }

        jam.setCreatedAt(LocalDateTime.now());

        if (jam.getCategories() != null) {
            for (Modjam.Category cat : jam.getCategories()) {
                if (cat.getId() == null || cat.getId().isEmpty()) {
                    cat.setId(UUID.randomUUID().toString());
                }
            }
        }

        return modjamRepository.save(jam);
    }

    public List<ModjamSubmission> getSubmissions(String jamId) {
        return submissionRepository.findByJamId(jamId);
    }

    public Modjam participate(String jamId, String userId) {
        Modjam jam = modjamRepository.findById(jamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jam not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (!jam.getParticipantIds().contains(userId)) {
            jam.getParticipantIds().add(userId);
            modjamRepository.save(jam);
        }

        if (!user.getJoinedModjamIds().contains(jamId)) {
            user.getJoinedModjamIds().add(jamId);
            userRepository.save(user);
        }

        return jam;
    }

    public ModjamSubmission submitProject(String jamId, String projectId, String userId) {
        Modjam jam = modjamRepository.findById(jamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jam not found"));
        Mod project = modRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        if (!project.getAuthorId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this project");
        }

        List<ModjamSubmission> existing = submissionRepository.findByJamIdAndSubmitterId(jamId, userId);
        boolean alreadySubmitted = existing.stream().anyMatch(s -> s.getProjectId().equals(projectId));
        if (alreadySubmitted) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project already submitted to this jam");
        }

        ModjamSubmission sub = new ModjamSubmission();
        sub.setJamId(jamId);
        sub.setProjectId(projectId);
        sub.setProjectTitle(project.getTitle());
        sub.setProjectImageUrl(project.getImageUrl());
        sub.setSubmitterId(userId);

        submissionRepository.save(sub);

        if (!project.getModjamIds().contains(jamId)) {
            project.getModjamIds().add(jamId);
            modRepository.save(project);
        }

        return sub;
    }

    public ModjamSubmission vote(String jamId, String submissionId, String categoryId, int score, String userId) {
        Modjam jam = modjamRepository.findById(jamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jam not found"));

        if (jam.getStatus().equals("COMPLETED")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voting has concluded for this jam.");
        }

        if (!jam.isAllowPublicVoting() && !jam.getHostId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only judges are permitted to vote on this jam.");
        }

        ModjamSubmission sub = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));

        sub.getVotes().removeIf(v -> v.getVoterId().equals(userId) && v.getCategoryId().equals(categoryId));

        ModjamSubmission.Vote vote = new ModjamSubmission.Vote();
        vote.setId(UUID.randomUUID().toString());
        vote.setVoterId(userId);
        vote.setCategoryId(categoryId);
        vote.setScore(score);
        sub.getVotes().add(vote);

        return submissionRepository.save(sub);
    }
}