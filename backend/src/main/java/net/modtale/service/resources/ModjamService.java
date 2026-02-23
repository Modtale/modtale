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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ModjamService {

    @Autowired private ModjamRepository modjamRepository;
    @Autowired private ModjamSubmissionRepository submissionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ModRepository modRepository;
    @Autowired private StorageService storageService;

    public List<Modjam> getAllJams() {
        return modjamRepository.findAll();
    }

    public List<Modjam> getUserHostedJams(String hostId) {
        return modjamRepository.findByHostId(hostId);
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
        } else if (!"DRAFT".equals(jam.getStatus())) {
            jam.setStatus("ACTIVE");
        }

        jam.setCreatedAt(LocalDateTime.now());
        jam.setUpdatedAt(LocalDateTime.now());

        if (jam.getCategories() != null) {
            for (Modjam.Category cat : jam.getCategories()) {
                if (cat.getId() == null || cat.getId().trim().isEmpty()) {
                    cat.setId(UUID.randomUUID().toString());
                }
            }
        } else {
            jam.setCategories(new ArrayList<>());
        }

        return modjamRepository.save(jam);
    }

    public Modjam updateJam(String id, Modjam updatedJam) {
        Modjam jam = modjamRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jam not found"));

        jam.setTitle(updatedJam.getTitle());
        jam.setDescription(updatedJam.getDescription());
        jam.setStartDate(updatedJam.getStartDate());
        jam.setEndDate(updatedJam.getEndDate());
        jam.setVotingEndDate(updatedJam.getVotingEndDate());
        jam.setAllowPublicVoting(updatedJam.isAllowPublicVoting());
        jam.setAllowConcurrentVoting(updatedJam.isAllowConcurrentVoting());
        jam.setShowResultsBeforeVotingEnds(updatedJam.isShowResultsBeforeVotingEnds());

        if (updatedJam.getCategories() != null) {
            for (Modjam.Category cat : updatedJam.getCategories()) {
                if (cat.getId() == null || cat.getId().trim().isEmpty()) {
                    cat.setId(UUID.randomUUID().toString());
                }
            }
            jam.setCategories(updatedJam.getCategories());
        } else {
            jam.setCategories(new ArrayList<>());
        }

        if (!"COMPLETED".equals(jam.getStatus()) && "COMPLETED".equals(updatedJam.getStatus())) {
            calculateScores(jam.getId());
        }

        jam.setStatus(updatedJam.getStatus());
        jam.setUpdatedAt(LocalDateTime.now());

        return modjamRepository.save(jam);
    }

    public void updateIcon(String jamId, MultipartFile file) {
        try {
            Modjam jam = modjamRepository.findById(jamId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            String pathPrefix = "modjams/" + jamId + "/icon";
            String storageKey = storageService.upload(file, pathPrefix);
            String publicUrl = storageService.getPublicUrl(storageKey);
            jam.setImageUrl(publicUrl);
            jam.setUpdatedAt(LocalDateTime.now());
            modjamRepository.save(jam);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload icon", e);
        }
    }

    public void updateBanner(String jamId, MultipartFile file) {
        try {
            Modjam jam = modjamRepository.findById(jamId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
            String pathPrefix = "modjams/" + jamId + "/banner";
            String storageKey = storageService.upload(file, pathPrefix);
            String publicUrl = storageService.getPublicUrl(storageKey);
            jam.setBannerUrl(publicUrl);
            jam.setUpdatedAt(LocalDateTime.now());
            modjamRepository.save(jam);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload banner", e);
        }
    }

    public void deleteJam(String jamId, String userId) {
        Modjam jam = modjamRepository.findById(jamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jam not found"));

        if (!jam.getHostId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can delete this jam");
        }

        List<ModjamSubmission> submissions = submissionRepository.findByJamId(jamId);
        if (submissions != null && !submissions.isEmpty()) {
            submissionRepository.deleteAll(submissions);
        }

        modjamRepository.delete(jam);
    }

    public List<ModjamSubmission> getSubmissions(String jamId) {
        return submissionRepository.findByJamId(jamId);
    }

    public Modjam participate(String jamId, String userId) {
        Modjam jam = modjamRepository.findById(jamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jam not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (jam.getParticipantIds() == null) {
            jam.setParticipantIds(new ArrayList<>());
        }

        if (!jam.getParticipantIds().contains(userId)) {
            jam.getParticipantIds().add(userId);
            modjamRepository.save(jam);
        }

        if (user.getJoinedModjamIds() == null) {
            user.setJoinedModjamIds(new ArrayList<>());
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

        if (!"ACTIVE".equals(jam.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Submissions are closed for this jam.");
        }

        Mod project = modRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        if (!project.getAuthorId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this project");
        }

        if (!"PUBLISHED".equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only published projects can be submitted to a jam.");
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
        sub.setProjectBannerUrl(project.getBannerUrl());
        sub.setProjectAuthor(project.getAuthor());
        sub.setProjectDescription(project.getDescription());
        sub.setSubmitterId(userId);

        submissionRepository.save(sub);

        if (project.getModjamIds() == null) {
            project.setModjamIds(new ArrayList<>());
        }
        if (!project.getModjamIds().contains(jamId)) {
            project.getModjamIds().add(jamId);
            modRepository.save(project);
        }

        return sub;
    }

    public ModjamSubmission vote(String jamId, String submissionId, String categoryId, int score, String userId) {
        if (categoryId == null || categoryId.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Category ID is required to vote.");
        }

        Modjam jam = modjamRepository.findById(jamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jam not found"));

        boolean validCategory = jam.getCategories() != null && jam.getCategories().stream().anyMatch(c -> categoryId.equals(c.getId()));
        if (!validCategory) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid category ID provided.");
        }

        boolean canVote = "VOTING".equals(jam.getStatus()) ||
                ("ACTIVE".equals(jam.getStatus()) && jam.isAllowConcurrentVoting());

        if (!canVote) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voting is not currently active for this jam.");
        }

        if (!jam.isAllowPublicVoting() && !jam.getHostId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only judges are permitted to vote on this jam.");
        }

        ModjamSubmission sub = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));

        if (sub.getSubmitterId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot vote on your own submission.");
        }

        if (sub.getVotes() == null) {
            sub.setVotes(new ArrayList<>());
        }

        sub.getVotes().removeIf(v -> v.getVoterId().equals(userId) && v.getCategoryId().equals(categoryId));

        ModjamSubmission.Vote vote = new ModjamSubmission.Vote();
        vote.setId(UUID.randomUUID().toString());
        vote.setVoterId(userId);
        vote.setCategoryId(categoryId);
        vote.setScore(score);
        sub.getVotes().add(vote);

        submissionRepository.save(sub);
        calculateScores(jamId);

        return submissionRepository.findById(submissionId).orElse(sub);
    }

    private void calculateScores(String jamId) {
        List<ModjamSubmission> submissions = submissionRepository.findByJamId(jamId);
        if (submissions == null || submissions.isEmpty()) return;

        for (ModjamSubmission sub : submissions) {
            Map<String, List<Integer>> categoryScoresMap = new HashMap<>();

            if (sub.getVotes() != null) {
                for (ModjamSubmission.Vote vote : sub.getVotes()) {
                    if (vote.getCategoryId() != null) {
                        categoryScoresMap.computeIfAbsent(vote.getCategoryId(), k -> new ArrayList<>()).add(vote.getScore());
                    }
                }
            }

            Map<String, Double> averagedCategoryScores = new HashMap<>();
            double totalScoreSum = 0;
            int categoryCount = 0;

            for (Map.Entry<String, List<Integer>> entry : categoryScoresMap.entrySet()) {
                if (entry.getKey() != null) {
                    double avg = entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0.0);
                    averagedCategoryScores.put(entry.getKey(), avg);
                    totalScoreSum += avg;
                    categoryCount++;
                }
            }

            sub.setCategoryScores(averagedCategoryScores);
            sub.setTotalScore(categoryCount > 0 ? totalScoreSum / categoryCount : 0.0);
        }

        submissions.sort((s1, s2) -> Double.compare(s2.getTotalScore() != null ? s2.getTotalScore() : 0.0, s1.getTotalScore() != null ? s1.getTotalScore() : 0.0));

        int rank = 1;
        for (ModjamSubmission sub : submissions) {
            sub.setRank(rank++);
            submissionRepository.save(sub);
        }
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void cleanupStaleDrafts() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<Modjam> staleDrafts = modjamRepository.findByStatusAndUpdatedAtBefore("DRAFT", thirtyDaysAgo);

        for (Modjam jam : staleDrafts) {
            List<ModjamSubmission> submissions = submissionRepository.findByJamId(jam.getId());
            if (submissions != null && !submissions.isEmpty()) {
                submissionRepository.deleteAll(submissions);
            }
            modjamRepository.delete(jam);
        }
    }
}