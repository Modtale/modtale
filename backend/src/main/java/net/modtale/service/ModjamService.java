package net.modtale.service;

import net.modtale.model.jam.Modjam;
import net.modtale.model.jam.ModjamSubmission;
import net.modtale.model.resources.Mod;
import net.modtale.model.user.User;
import net.modtale.repository.jam.ModjamRepository;
import net.modtale.repository.jam.ModjamSubmissionRepository;
import net.modtale.repository.resources.ModRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.resources.StorageService;
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

    private void enrichSubmission(ModjamSubmission sub) {
        modRepository.findById(sub.getProjectId()).ifPresent(project -> {
            sub.setProjectTitle(project.getTitle());
            sub.setProjectImageUrl(project.getImageUrl());
            sub.setProjectBannerUrl(project.getBannerUrl());
            sub.setProjectAuthor(project.getAuthor());
            sub.setProjectDescription(project.getDescription());
        });
    }

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

        if (updatedJam.getRestrictions() != null) {
            jam.setRestrictions(updatedJam.getRestrictions());
        }

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

        String targetStatus = updatedJam.getStatus();
        if (!"COMPLETED".equals(targetStatus) && !"DRAFT".equals(targetStatus)) {
            LocalDateTime now = LocalDateTime.now();
            if (jam.getStartDate() != null && now.isBefore(jam.getStartDate())) {
                targetStatus = "UPCOMING";
            } else if (jam.getEndDate() != null && now.isBefore(jam.getEndDate())) {
                targetStatus = "ACTIVE";
            } else if (jam.getVotingEndDate() != null && now.isBefore(jam.getVotingEndDate())) {
                targetStatus = "VOTING";
            } else {
                targetStatus = "AWAITING_WINNERS";
            }
        }

        jam.setStatus(targetStatus);
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
        List<ModjamSubmission> subs = submissionRepository.findByJamId(jamId);
        subs.forEach(this::enrichSubmission);
        return subs;
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
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Submissions are closed.");
        }

        Mod project = modRepository.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));

        if (!project.getAuthorId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your project");
        }

        if (!"PUBLISHED".equals(project.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project must be published.");
        }

        Modjam.Restrictions res = jam.getRestrictions();
        if (res != null) {
            if (res.isRequireNewProject() && jam.getStartDate() != null && project.getCreatedAt() != null) {
                try {
                    String cleanDate = project.getCreatedAt().replace("Z", "");
                    LocalDateTime projCreated = LocalDateTime.parse(cleanDate);
                    if (projCreated.isBefore(jam.getStartDate())) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project must be created after the jam start date.");
                    }
                } catch (Exception ignored) {}
            }

            if (res.isRequireSourceRepo() && (project.getRepositoryUrl() == null || project.getRepositoryUrl().trim().isEmpty())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project must have a linked public source repository.");
            }

            if (res.isRequireOsiLicense()) {
                String l = project.getLicense() != null ? project.getLicense().toUpperCase().replaceAll("[^A-Z0-9]", "") : "";
                boolean isOsi = l.contains("MIT") || l.contains("APACHE") || l.contains("LGPL") || l.contains("AGPL") || l.contains("GPL") || l.contains("MPL") || l.contains("BSD") || l.contains("UNLICENSE") || l.contains("CC0");
                if (!isOsi) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project must use an OSI-approved open source license.");
                }
            }

            if (res.getAllowedClassifications() != null && !res.getAllowedClassifications().isEmpty()) {
                if (!res.getAllowedClassifications().contains(project.getClassification())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project classification is not allowed for this jam.");
                }
            }

            if (res.getAllowedLicenses() != null && !res.getAllowedLicenses().isEmpty()) {
                if (!res.getAllowedLicenses().contains(project.getLicense())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project license is not allowed for this jam.");
                }
            }

            if (res.getRequiredDependencyId() != null && !res.getRequiredDependencyId().trim().isEmpty()) {
                if (project.getModIds() == null || !project.getModIds().contains(res.getRequiredDependencyId().trim())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project is missing the required dependency.");
                }
            }

            int contributorCount = (project.getContributors() != null ? project.getContributors().size() : 0) + 1;
            if (res.getMinContributors() != null && contributorCount < res.getMinContributors()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project does not meet the minimum contributor requirement.");
            }

            if (res.getMaxContributors() != null && contributorCount > res.getMaxContributors()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project exceeds the maximum contributor limit.");
            }

            if (res.isRequireUniqueSubmission() && project.getModjamIds() != null) {
                for (String otherJamId : project.getModjamIds()) {
                    if (otherJamId.equals(jamId)) continue;
                    modjamRepository.findById(otherJamId).ifPresent(otherJam -> {
                        if ("ACTIVE".equals(otherJam.getStatus()) || "VOTING".equals(otherJam.getStatus())) {
                            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Project is currently entered in another active jam.");
                        }
                    });
                }
            }

            if (res.isRequireNewbie()) {
                User u = userRepository.findById(userId).orElse(null);
                if (u != null && u.getJoinedModjamIds() != null) {
                    long activeJams = u.getJoinedModjamIds().stream().filter(id -> !id.equals(jamId)).count();
                    if (activeJams > 0) {
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This jam is restricted to first-time participants.");
                    }
                }
            }
        }

        List<ModjamSubmission> existing = submissionRepository.findByJamIdAndSubmitterId(jamId, userId);
        if (existing.stream().anyMatch(s -> s.getProjectId().equals(projectId))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Already submitted.");
        }

        ModjamSubmission sub = new ModjamSubmission();
        sub.setJamId(jamId);
        sub.setProjectId(projectId);
        sub.setSubmitterId(userId);

        submissionRepository.save(sub);

        if (project.getModjamIds() == null) project.setModjamIds(new ArrayList<>());
        if (!project.getModjamIds().contains(jamId)) {
            project.getModjamIds().add(jamId);
            modRepository.save(project);
        }

        enrichSubmission(sub);
        return sub;
    }

    public ModjamSubmission vote(String jamId, String submissionId, String categoryId, int score, String userId) {
        Modjam jam = modjamRepository.findById(jamId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (jam.getVotingEndDate() != null && LocalDateTime.now().isAfter(jam.getVotingEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Voting has closed for this jam.");
        }

        ModjamSubmission sub = submissionRepository.findById(submissionId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (sub.getSubmitterId().equals(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot vote on self");

        sub.getVotes().removeIf(v -> v.getVoterId().equals(userId) && v.getCategoryId().equals(categoryId));

        ModjamSubmission.Vote vote = new ModjamSubmission.Vote(UUID.randomUUID().toString(), userId, categoryId, score);
        sub.getVotes().add(vote);

        submissionRepository.save(sub);
        calculateScores(jamId);

        ModjamSubmission updated = submissionRepository.findById(submissionId).orElse(sub);
        enrichSubmission(updated);
        return updated;
    }

    private void calculateScores(String jamId) {
        List<ModjamSubmission> submissions = submissionRepository.findByJamId(jamId);
        if (submissions == null || submissions.isEmpty()) return;

        for (ModjamSubmission sub : submissions) {
            Map<String, List<Integer>> categoryScoresMap = new HashMap<>();
            if (sub.getVotes() != null) {
                for (ModjamSubmission.Vote vote : sub.getVotes()) {
                    categoryScoresMap.computeIfAbsent(vote.getCategoryId(), k -> new ArrayList<>()).add(vote.getScore());
                }
            }

            Map<String, Double> averagedCategoryScores = new HashMap<>();
            double totalScoreSum = 0;
            int categoryCount = 0;

            for (Map.Entry<String, List<Integer>> entry : categoryScoresMap.entrySet()) {
                double avg = entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0.0);
                averagedCategoryScores.put(entry.getKey(), avg);
                totalScoreSum += avg;
                categoryCount++;
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

    public Modjam finalizeJam(String jamId, String userId, List<Map<String, String>> winnersData) {
        Modjam jam = modjamRepository.findById(jamId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!jam.getHostId().equals(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can finalize the jam");

        calculateScores(jamId);

        List<ModjamSubmission> allSubs = submissionRepository.findByJamId(jamId);
        for (ModjamSubmission sub : allSubs) {
            Optional<Map<String, String>> matchingWinner = winnersData.stream()
                    .filter(w -> w.get("submissionId").equals(sub.getId()))
                    .findFirst();

            if (matchingWinner.isPresent()) {
                sub.setWinner(true);
                sub.setAwardTitle(matchingWinner.get().get("awardTitle"));
            } else {
                sub.setWinner(false);
                sub.setAwardTitle(null);
            }
            submissionRepository.save(sub);
        }

        jam.setStatus("COMPLETED");
        jam.setUpdatedAt(LocalDateTime.now());
        return modjamRepository.save(jam);
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void cleanupStaleDrafts() {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<Modjam> staleDrafts = modjamRepository.findByStatusAndUpdatedAtBefore("DRAFT", thirtyDaysAgo);
        for (Modjam jam : staleDrafts) {
            submissionRepository.deleteAll(submissionRepository.findByJamId(jam.getId()));
            modjamRepository.delete(jam);
        }
    }
}