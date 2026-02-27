package net.modtale.service;

import net.modtale.model.jam.Modjam;
import net.modtale.model.jam.ModjamSubmission;
import net.modtale.model.resources.Mod;
import net.modtale.model.resources.ModVersion;
import net.modtale.model.user.User;
import net.modtale.repository.jam.ModjamRepository;
import net.modtale.repository.jam.ModjamSubmissionRepository;
import net.modtale.repository.resources.ModRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.resources.ModService;
import net.modtale.service.resources.StorageService;
import net.modtale.service.user.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;

@Service
public class ModjamService {

    @Autowired private ModjamRepository modjamRepository;
    @Autowired private ModjamSubmissionRepository submissionRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ModRepository modRepository;
    @Autowired private StorageService storageService;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private UserService userService;

    @Autowired
    @Lazy
    private ModService modService;

    @Value("${app.r2.public-domain:#{null}}")
    private String publicDomain;

    private static final RuntimeException FOUND_USAGE = new RuntimeException("Found usage of class or package", null, false, false) {};

    public static final class CheckJarUseClass {
        public static boolean checkUseClass(final File file, final String classOrPackage) {
            final String searchPrefix = classOrPackage.replace('.', '/').replace("*", "");
            try (final ZipFile zipFile = new ZipFile(file)) {
                zipFile.stream().filter(entry -> entry.getName().endsWith(".class"))
                        .forEach(zipEntry -> {
                            try {
                                new ClassReader(zipFile.getInputStream(zipEntry))
                                        .accept(new PrefixUsageSearcher(searchPrefix),
                                                ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });
            } catch (Exception e) {
                if (e == FOUND_USAGE) {
                    return true;
                }
            }
            return false;
        }

        private static class PrefixUsageSearcher extends ClassVisitor {
            private final String prefix;

            public PrefixUsageSearcher(String prefix) {
                super(Opcodes.ASM9);
                this.prefix = prefix;
            }

            private void check(String internalName) {
                if (internalName != null && internalName.startsWith(prefix)) {
                    throw FOUND_USAGE;
                }
            }

            private void checkDescriptor(String descriptor) {
                if (descriptor != null && descriptor.contains(prefix)) {
                    throw FOUND_USAGE;
                }
            }

            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                check(superName);
                if (interfaces != null) {
                    for (String i : interfaces) check(i);
                }
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                checkDescriptor(descriptor);
                return super.visitField(access, name, descriptor, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                checkDescriptor(descriptor);
                return new MethodVisitor(Opcodes.ASM9, super.visitMethod(access, name, descriptor, signature, exceptions)) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String mName, String mDescriptor, boolean isInterface) {
                        check(owner);
                        super.visitMethodInsn(opcode, owner, mName, mDescriptor, isInterface);
                    }

                    @Override
                    public void visitFieldInsn(int opcode, String owner, String fName, String fDescriptor) {
                        check(owner);
                        super.visitFieldInsn(opcode, owner, fName, fDescriptor);
                    }

                    @Override
                    public void visitTypeInsn(int opcode, String type) {
                        check(type);
                        super.visitTypeInsn(opcode, type);
                    }
                };
            }
        }
    }

    private Modjam enrichAndReturn(Modjam jam) {
        if (jam != null && jam.getJudgeIds() != null && !jam.getJudgeIds().isEmpty()) {
            List<Map<String, String>> profiles = new ArrayList<>();
            for (String jId : jam.getJudgeIds()) {
                userRepository.findById(jId).ifPresent(u -> {
                    Map<String, String> p = new HashMap<>();
                    p.put("id", u.getId());
                    p.put("username", u.getUsername());
                    p.put("avatarUrl", u.getAvatarUrl());
                    profiles.add(p);
                });
            }
            jam.setJudgeProfiles(profiles);
        }
        return jam;
    }

    private void enrichSubmissions(String jamId, List<ModjamSubmission> subs, Map<String, Mod> projectMap) {
        if (subs == null || subs.isEmpty()) return;

        Map<String, Integer> userVoteCount = new HashMap<>();
        Set<String> visibleProjectIds = new HashSet<>();

        for (ModjamSubmission s : subs) {
            visibleProjectIds.add(s.getProjectId());
            if (s.getVotes() != null) {
                for (ModjamSubmission.Vote v : s.getVotes()) {
                    userVoteCount.put(v.getVoterId(), userVoteCount.getOrDefault(v.getVoterId(), 0) + 1);
                }
            }
        }

        Map<String, Integer> userCommentCount = new HashMap<>();
        for (Mod p : projectMap.values()) {
            if (visibleProjectIds.contains(p.getId()) && p.getComments() != null) {
                for (net.modtale.model.resources.Comment c : p.getComments()) {
                    if (!c.getUser().equals(p.getAuthor())) {
                        userCommentCount.put(c.getUser(), userCommentCount.getOrDefault(c.getUser(), 0) + 1);
                    }
                }
            }
        }

        for (ModjamSubmission sub : subs) {
            Mod project = projectMap.get(sub.getProjectId());
            if (project != null) {
                sub.setProjectTitle(project.getTitle());
                sub.setProjectImageUrl(project.getImageUrl());
                sub.setProjectBannerUrl(project.getBannerUrl());
                sub.setProjectAuthor(project.getAuthor());
                sub.setProjectDescription(project.getDescription());
            }
            sub.setVotesCast(userVoteCount.getOrDefault(sub.getSubmitterId(), 0));
            sub.setCommentsGiven(userCommentCount.getOrDefault(sub.getProjectAuthor(), 0));
        }
    }

    private void enrichSubmissions(String jamId, List<ModjamSubmission> subs) {
        if (subs == null || subs.isEmpty()) return;
        List<String> projectIds = subs.stream().map(ModjamSubmission::getProjectId).toList();
        Iterable<Mod> projectsIterable = modRepository.findAllById(projectIds);
        Map<String, Mod> projectMap = new HashMap<>();
        projectsIterable.forEach(p -> projectMap.put(p.getId(), p));
        enrichSubmissions(jamId, subs, projectMap);
    }

    private String extractStorageKey(String fileUrl) {
        if (fileUrl == null) return null;
        if (fileUrl.startsWith("/api/files/proxy/")) {
            return fileUrl.replace("/api/files/proxy/", "");
        } else if (publicDomain != null && fileUrl.startsWith(publicDomain + "/")) {
            return fileUrl.replace(publicDomain + "/", "");
        } else if (publicDomain != null && fileUrl.startsWith(publicDomain)) {
            return fileUrl.replace(publicDomain, "");
        }
        return fileUrl;
    }

    public List<Modjam> getAllJams() {
        return modjamRepository.findAll().stream().map(this::enrichAndReturn).collect(Collectors.toList());
    }

    public List<Modjam> getUserHostedJams(String hostId) {
        return modjamRepository.findByHostId(hostId).stream().map(this::enrichAndReturn).collect(Collectors.toList());
    }

    public Modjam getJamBySlug(String slug) {
        return enrichAndReturn(modjamRepository.findBySlug(slug)
                .orElseThrow(() -> new IllegalArgumentException("Jam not found")));
    }

    public Modjam createJam(Modjam jam, String hostId, String hostName) {
        jam.setId(null);
        jam.setHostId(hostId);
        jam.setHostName(hostName);

        if (jam.getSlug() == null || jam.getSlug().trim().isEmpty()) {
            throw new IllegalArgumentException("A custom URL slug is required.");
        }
        String newSlug = jam.getSlug().toLowerCase();
        if (!newSlug.matches("^[a-z0-9](?:[a-z0-9-]{1,48}[a-z0-9])?$")) {
            throw new IllegalArgumentException("Invalid URL Slug. Must be 3-50 characters, lowercase alphanumeric with dashes, and cannot start or end with a dash.");
        }
        if (modjamRepository.findBySlug(newSlug).isPresent()) {
            throw new IllegalArgumentException("Jam URL '" + newSlug + "' is already taken.");
        }
        jam.setSlug(newSlug);

        if (jam.getStartDate() != null && jam.getStartDate().isAfter(Instant.now())) {
            jam.setStatus("UPCOMING");
        } else if (!"DRAFT".equals(jam.getStatus())) {
            jam.setStatus("ACTIVE");
        }

        jam.setCreatedAt(Instant.now());
        jam.setUpdatedAt(Instant.now());

        if (jam.getCategories() != null) {
            for (Modjam.Category cat : jam.getCategories()) {
                if (cat.getId() == null || cat.getId().trim().isEmpty()) {
                    cat.setId(UUID.randomUUID().toString());
                }
            }
        } else {
            jam.setCategories(new ArrayList<>());
        }

        if (jam.getJudgeIds() == null) jam.setJudgeIds(new ArrayList<>());
        if (jam.getPendingJudgeInvites() == null) jam.setPendingJudgeInvites(new ArrayList<>());

        return enrichAndReturn(modjamRepository.save(jam));
    }

    public Modjam updateJam(String id, Modjam updatedJam) {
        Modjam jam = modjamRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Jam not found"));

        if (updatedJam.getSlug() == null || updatedJam.getSlug().trim().isEmpty()) {
            throw new IllegalArgumentException("A custom URL slug is required.");
        }
        String newSlug = updatedJam.getSlug().toLowerCase();
        if (!newSlug.equals(jam.getSlug())) {
            if (!newSlug.matches("^[a-z0-9](?:[a-z0-9-]{1,48}[a-z0-9])?$")) {
                throw new IllegalArgumentException("Invalid URL Slug. Must be 3-50 characters, lowercase alphanumeric with dashes, and cannot start or end with a dash.");
            }
            if (modjamRepository.findBySlug(newSlug).isPresent()) {
                throw new IllegalArgumentException("Jam URL '" + newSlug + "' is already taken.");
            }
            jam.setSlug(newSlug);
        }

        String oldStatus = jam.getStatus();

        jam.setTitle(updatedJam.getTitle());
        jam.setDescription(updatedJam.getDescription());
        jam.setRules(updatedJam.getRules());
        jam.setStartDate(updatedJam.getStartDate());
        jam.setEndDate(updatedJam.getEndDate());
        jam.setVotingEndDate(updatedJam.getVotingEndDate());
        jam.setAllowPublicVoting(updatedJam.isAllowPublicVoting());
        jam.setAllowConcurrentVoting(updatedJam.isAllowConcurrentVoting());
        jam.setShowResultsBeforeVotingEnds(updatedJam.isShowResultsBeforeVotingEnds());
        jam.setOneEntryPerPerson(updatedJam.isOneEntryPerPerson());
        jam.setHideSubmissions(updatedJam.isHideSubmissions());

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
            Instant now = Instant.now();
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
        jam.setUpdatedAt(Instant.now());

        if (jam.isHideSubmissions() && !List.of("VOTING", "COMPLETED", "AWAITING_WINNERS").contains(oldStatus)
                && List.of("VOTING", "COMPLETED", "AWAITING_WINNERS").contains(targetStatus)) {
            modService.revealHiddenJamMods(jam.getId());
        }

        return enrichAndReturn(modjamRepository.save(jam));
    }

    public Modjam inviteJudge(String jamId, String username, String hostId) {
        Modjam jam = modjamRepository.findById(jamId).orElseThrow(() -> new IllegalArgumentException("Jam not found"));
        if (!jam.getHostId().equals(hostId)) throw new SecurityException("Only the host can invite judges.");

        Query query = new Query(Criteria.where("username").regex("^" + username + "$", "i"));
        User targetUser = mongoTemplate.findOne(query, User.class);

        if (targetUser == null) {
            throw new IllegalArgumentException("User '" + username + "' not found.");
        }

        if (targetUser.getId().equals(hostId)) {
            throw new IllegalArgumentException("You cannot invite yourself.");
        }

        if (jam.getPendingJudgeInvites() == null) jam.setPendingJudgeInvites(new ArrayList<>());
        if (jam.getJudgeIds() == null) jam.setJudgeIds(new ArrayList<>());

        if (jam.getJudgeIds().contains(targetUser.getId())) {
            throw new IllegalArgumentException("User is already a judge.");
        }
        if (jam.getPendingJudgeInvites().contains(targetUser.getUsername())) {
            throw new IllegalArgumentException("User is already invited.");
        }

        jam.getPendingJudgeInvites().add(targetUser.getUsername());

        org.bson.Document notif = new org.bson.Document();
        notif.put("userId", targetUser.getId());
        notif.put("title", "Jam Judge Invitation");
        notif.put("message", "You have been invited to be a judge for " + jam.getTitle());
        notif.put("link", "/jam/" + jam.getSlug() + "/overview");
        notif.put("read", false);
        notif.put("createdAt", Instant.now());
        mongoTemplate.save(notif, "notifications");

        return enrichAndReturn(modjamRepository.save(jam));
    }

    public Modjam removeJudge(String jamId, String username, String hostId) {
        Modjam jam = modjamRepository.findById(jamId).orElseThrow(() -> new IllegalArgumentException("Jam not found"));
        if (!jam.getHostId().equals(hostId)) throw new SecurityException("Only the host can remove judges.");

        if (jam.getPendingJudgeInvites() != null) {
            jam.getPendingJudgeInvites().removeIf(u -> u.equalsIgnoreCase(username));
        }

        if (jam.getJudgeIds() != null) {
            Query query = new Query(Criteria.where("username").regex("^" + username + "$", "i"));
            User targetUser = mongoTemplate.findOne(query, User.class);
            if (targetUser != null) {
                jam.getJudgeIds().remove(targetUser.getId());
            }
        }

        return enrichAndReturn(modjamRepository.save(jam));
    }

    public Modjam acceptJudgeInvite(String jamId, String userId, String username) {
        Modjam jam = modjamRepository.findById(jamId).orElseThrow(() -> new IllegalArgumentException("Jam not found"));

        if (jam.getPendingJudgeInvites() == null || jam.getPendingJudgeInvites().stream().noneMatch(u -> u.equalsIgnoreCase(username))) {
            throw new IllegalArgumentException("You don't have a pending invite for this jam.");
        }

        jam.getPendingJudgeInvites().removeIf(u -> u.equalsIgnoreCase(username));

        if (jam.getJudgeIds() == null) jam.setJudgeIds(new ArrayList<>());
        if (!jam.getJudgeIds().contains(userId)) {
            jam.getJudgeIds().add(userId);
        }

        return enrichAndReturn(modjamRepository.save(jam));
    }

    public Modjam declineJudgeInvite(String jamId, String username) {
        Modjam jam = modjamRepository.findById(jamId).orElseThrow(() -> new IllegalArgumentException("Jam not found"));

        if (jam.getPendingJudgeInvites() != null) {
            jam.getPendingJudgeInvites().removeIf(u -> u.equalsIgnoreCase(username));
        }

        return enrichAndReturn(modjamRepository.save(jam));
    }

    public void updateIcon(String jamId, MultipartFile file) {
        try {
            Modjam jam = modjamRepository.findById(jamId).orElseThrow(() -> new IllegalArgumentException("Jam not found"));
            String pathPrefix = "modjams/" + jamId + "/icon";
            String storageKey = storageService.upload(file, pathPrefix);
            String publicUrl = storageService.getPublicUrl(storageKey);
            jam.setImageUrl(publicUrl);
            jam.setUpdatedAt(Instant.now());
            modjamRepository.save(jam);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload icon", e);
        }
    }

    public void updateBanner(String jamId, MultipartFile file) {
        try {
            Modjam jam = modjamRepository.findById(jamId).orElseThrow(() -> new IllegalArgumentException("Jam not found"));
            String pathPrefix = "modjams/" + jamId + "/banner";
            String storageKey = storageService.upload(file, pathPrefix);
            String publicUrl = storageService.getPublicUrl(storageKey);
            jam.setBannerUrl(publicUrl);
            jam.setUpdatedAt(Instant.now());
            modjamRepository.save(jam);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload banner", e);
        }
    }

    public void deleteJam(String jamId, String userId) {
        Modjam jam = modjamRepository.findById(jamId)
                .orElseThrow(() -> new IllegalArgumentException("Jam not found"));

        if (!jam.getHostId().equals(userId)) {
            throw new SecurityException("Only the host can delete this jam");
        }

        List<ModjamSubmission> submissions = submissionRepository.findByJamId(jamId);
        if (submissions != null && !submissions.isEmpty()) {
            submissionRepository.deleteAll(submissions);
        }

        modjamRepository.delete(jam);
    }

    public List<ModjamSubmission> getSubmissions(String jamId) {
        Modjam jam = modjamRepository.findById(jamId)
                .orElseThrow(() -> new IllegalArgumentException("Jam not found"));
        List<ModjamSubmission> allSubs = submissionRepository.findByJamId(jamId);

        if (allSubs == null || allSubs.isEmpty()) return new ArrayList<>();

        User currentUser = userService.getCurrentUser();
        boolean isAdmin = currentUser != null && currentUser.getRoles() != null && currentUser.getRoles().contains("ADMIN");
        boolean isHost = currentUser != null && currentUser.getId().equals(jam.getHostId());

        boolean isJamHiding = jam.isHideSubmissions() && List.of("DRAFT", "UPCOMING", "ACTIVE").contains(jam.getStatus());

        List<String> projectIds = allSubs.stream().map(ModjamSubmission::getProjectId).toList();
        Iterable<Mod> projectsIterable = modRepository.findAllById(projectIds);
        Map<String, Mod> projectMap = new HashMap<>();
        projectsIterable.forEach(p -> projectMap.put(p.getId(), p));

        List<ModjamSubmission> visibleSubs = new ArrayList<>();

        for (ModjamSubmission sub : allSubs) {
            Mod project = projectMap.get(sub.getProjectId());
            if (project == null) continue;

            boolean isSubmitter = currentUser != null && currentUser.getId().equals(sub.getSubmitterId());
            boolean canSeeHidden = isAdmin || isHost || isSubmitter;
            boolean isPublicProject = "PUBLISHED".equals(project.getStatus()) || "ARCHIVED".equals(project.getStatus());

            if (!canSeeHidden) {
                if (!isPublicProject || isJamHiding) {
                    continue; // Backend enforces filtering out unreleased or jam-hidden projects
                }
            }
            visibleSubs.add(sub);
        }

        enrichSubmissions(jamId, visibleSubs, projectMap);
        return visibleSubs;
    }

    public Modjam participate(String jamId, String userId) {
        Modjam jam = modjamRepository.findById(jamId)
                .orElseThrow(() -> new IllegalArgumentException("Jam not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getJoinedModjamIds() != null) {
            for (String joinedId : user.getJoinedModjamIds()) {
                if (joinedId.equals(jamId)) continue;

                Optional<Modjam> optOtherJam = modjamRepository.findById(joinedId);
                if (optOtherJam.isPresent()) {
                    Modjam otherJam = optOtherJam.get();
                    boolean otherIsActive = "ACTIVE".equals(otherJam.getStatus()) || "UPCOMING".equals(otherJam.getStatus());
                    boolean thisIsActive = "ACTIVE".equals(jam.getStatus()) || "UPCOMING".equals(jam.getStatus());

                    if (otherIsActive && thisIsActive) {
                        boolean otherRequiresUnique = otherJam.getRestrictions() != null && otherJam.getRestrictions().isRequireUniqueSubmission();
                        boolean thisRequiresUnique = jam.getRestrictions() != null && jam.getRestrictions().isRequireUniqueSubmission();

                        if (otherRequiresUnique) {
                            throw new IllegalStateException("You are currently participating in '" + otherJam.getTitle() + "' which requires unique participation. You must leave it to join this jam.");
                        }
                        if (thisRequiresUnique) {
                            throw new IllegalStateException("This jam requires unique participation. You are currently in '" + otherJam.getTitle() + "'. You must leave it to join this jam.");
                        }
                    }
                }
            }
        }

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

        return enrichAndReturn(jam);
    }

    public Modjam leaveJam(String jamId, String userId) {
        Modjam jam = modjamRepository.findById(jamId)
                .orElseThrow(() -> new IllegalArgumentException("Jam not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<ModjamSubmission> existingSubs = submissionRepository.findByJamIdAndSubmitterId(jamId, userId);
        if (!existingSubs.isEmpty()) {
            throw new IllegalArgumentException("Cannot leave a jam after submitting a project.");
        }

        if (jam.getParticipantIds() != null) {
            jam.getParticipantIds().remove(userId);
            modjamRepository.save(jam);
        }

        if (user.getJoinedModjamIds() != null) {
            user.getJoinedModjamIds().remove(jamId);
            userRepository.save(user);
        }

        return enrichAndReturn(jam);
    }

    public ModjamSubmission submitProject(String jamId, String projectId, String userId) {
        Modjam jam = modjamRepository.findById(jamId)
                .orElseThrow(() -> new IllegalArgumentException("Jam not found"));

        if (!"ACTIVE".equals(jam.getStatus())) {
            throw new IllegalArgumentException("Submissions are closed.");
        }

        Mod project = modRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        if (!project.getAuthorId().equals(userId)) {
            throw new SecurityException("Not your project");
        }

        if (!List.of("PUBLISHED", "PENDING", "APPROVED_HIDDEN", "DRAFT").contains(project.getStatus())) {
            throw new IllegalArgumentException("Project cannot be submitted in its current state.");
        }

        if (jam.isHideSubmissions() && "PUBLISHED".equals(project.getStatus())) {
            throw new IllegalArgumentException("This jam hides submissions until voting opens. You cannot submit an already-public project.");
        }

        List<ModjamSubmission> existing = submissionRepository.findByJamIdAndSubmitterId(jamId, userId);

        if (jam.isOneEntryPerPerson() && !existing.isEmpty()) {
            throw new IllegalArgumentException("This jam is restricted to one entry per person.");
        }

        if (existing.stream().anyMatch(s -> s.getProjectId().equals(projectId))) {
            throw new IllegalArgumentException("Already submitted.");
        }

        if ("DRAFT".equals(project.getStatus())) {
            try {
                modService.submitMod(projectId, userId);
                project = modRepository.findById(projectId).orElseThrow(() -> new IllegalArgumentException("Project not found"));
            } catch (Exception e) {
                throw new IllegalArgumentException(e.getMessage());
            }
        }

        Modjam.Restrictions res = jam.getRestrictions();
        if (res != null) {
            if (res.isRequireNoPriorProjects() || res.isRequirePriorProjects()) {
                long priorPublishedProjects = modRepository.findByAuthorIdList(userId).stream()
                        .filter(p -> !p.getId().equals(projectId) && "PUBLISHED".equals(p.getStatus()))
                        .count();

                if (res.isRequireNoPriorProjects() && priorPublishedProjects > 0) {
                    throw new IllegalArgumentException("This jam is restricted to users who have never published a project before.");
                }
                if (res.isRequirePriorProjects() && priorPublishedProjects == 0) {
                    throw new IllegalArgumentException("This jam is restricted to users who have previously published at least one project.");
                }
            }

            if (res.isRequireNewProject() && jam.getStartDate() != null && project.getCreatedAt() != null) {
                try {
                    Instant projCreated;
                    try {
                        projCreated = Instant.parse(project.getCreatedAt());
                    } catch (Exception e) {
                        String cleanDate = project.getCreatedAt().replace("Z", "");
                        projCreated = LocalDateTime.parse(cleanDate).toInstant(ZoneOffset.UTC);
                    }
                    if (projCreated.isBefore(jam.getStartDate())) {
                        throw new IllegalArgumentException("Project must be created after the jam start date.");
                    }
                } catch (IllegalArgumentException rse) {
                    throw rse;
                } catch (Exception ignored) {}
            }

            if (res.isRequireSourceRepo() && (project.getRepositoryUrl() == null || project.getRepositoryUrl().trim().isEmpty())) {
                throw new IllegalArgumentException("Project must have a linked public source repository.");
            }

            if (res.isRequireOsiLicense()) {
                String l = project.getLicense() != null ? project.getLicense().toUpperCase().replaceAll("[^A-Z0-9]", "") : "";
                boolean isOsi = l.contains("MIT") || l.contains("APACHE") || l.contains("LGPL") || l.contains("AGPL") || l.contains("GPL") || l.contains("MPL") || l.contains("BSD") || l.contains("UNLICENSE") || l.contains("CC0");
                if (!isOsi) {
                    throw new IllegalArgumentException("Project must use an OSI-approved open source license.");
                }
            }

            if (res.getAllowedClassifications() != null && !res.getAllowedClassifications().isEmpty()) {
                if (!res.getAllowedClassifications().contains(project.getClassification())) {
                    throw new IllegalArgumentException("Project classification is not allowed for this jam.");
                }
            }

            if (res.getAllowedLicenses() != null && !res.getAllowedLicenses().isEmpty()) {
                if (!res.getAllowedLicenses().contains(project.getLicense())) {
                    throw new IllegalArgumentException("Project license is not allowed for this jam.");
                }
            }

            if (res.getAllowedGameVersions() != null && !res.getAllowedGameVersions().isEmpty()) {
                boolean hasValidVersion = false;
                if (project.getVersions() != null) {
                    for (ModVersion pv : project.getVersions()) {
                        if (pv.getGameVersions() != null) {
                            for (String gv : pv.getGameVersions()) {
                                if (res.getAllowedGameVersions().contains(gv)) {
                                    hasValidVersion = true;
                                    break;
                                }
                            }
                        }
                        if (hasValidVersion) break;
                    }
                }
                if (!hasValidVersion) {
                    throw new IllegalArgumentException("Project does not support any of the required game versions.");
                }
            }

            if (res.getRequiredDependencyId() != null && !res.getRequiredDependencyId().trim().isEmpty()) {
                if (project.getModIds() == null || !project.getModIds().contains(res.getRequiredDependencyId().trim())) {
                    throw new IllegalArgumentException("Project is missing the required dependency.");
                }
            }

            int contributorCount = (project.getContributors() != null ? project.getContributors().size() : 0) + 1;
            if (res.getMinContributors() != null && contributorCount < res.getMinContributors()) {
                throw new IllegalArgumentException("Project does not meet the minimum contributor requirement.");
            }

            if (res.getMaxContributors() != null && contributorCount > res.getMaxContributors()) {
                throw new IllegalArgumentException("Project exceeds the maximum contributor limit.");
            }

            if (res.isRequireUniqueSubmission() && project.getModjamIds() != null) {
                for (String otherJamId : project.getModjamIds()) {
                    if (otherJamId.equals(jamId)) continue;
                    modjamRepository.findById(otherJamId).ifPresent(otherJam -> {
                        if ("ACTIVE".equals(otherJam.getStatus()) || "VOTING".equals(otherJam.getStatus())) {
                            throw new IllegalArgumentException("Project is currently entered in another active jam.");
                        }
                    });
                }
            }

            if (res.isRequireNewbie()) {
                User u = userRepository.findById(userId).orElse(null);
                if (u != null && u.getJoinedModjamIds() != null) {
                    long activeJams = u.getJoinedModjamIds().stream().filter(id -> !id.equals(jamId)).count();
                    if (activeJams > 0) {
                        throw new IllegalArgumentException("This jam is restricted to first-time participants.");
                    }
                }
            }

            if (res.isRequirePriorJams()) {
                User u = userRepository.findById(userId).orElse(null);
                if (u == null || u.getJoinedModjamIds() == null || u.getJoinedModjamIds().stream().filter(id -> !id.equals(jamId)).count() == 0) {
                    throw new IllegalArgumentException("This jam is restricted to experienced participants who have joined a jam before.");
                }
            }

            if (res.getRequiredClassUsage() != null && !res.getRequiredClassUsage().trim().isEmpty()) {
                if (project.getVersions() == null || project.getVersions().isEmpty()) {
                    throw new IllegalArgumentException("Project has no uploaded files to check.");
                }

                ModVersion latestVersion = project.getVersions().get(project.getVersions().size() - 1);
                String fileUrl = latestVersion.getFileUrl();
                if (fileUrl == null || fileUrl.isEmpty()) {
                    throw new IllegalArgumentException("Project version has no file associated.");
                }

                String storageKey = extractStorageKey(fileUrl);
                File tempFile = null;
                try {
                    InputStream is = storageService.getStream(storageKey);
                    tempFile = Files.createTempFile("jam_check_", ".jar").toFile();
                    try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                        is.transferTo(fos);
                    }

                    boolean usesClass = CheckJarUseClass.checkUseClass(tempFile, res.getRequiredClassUsage().trim());
                    if (!usesClass) {
                        throw new IllegalArgumentException("Project does not use the required class/package: " + res.getRequiredClassUsage().trim());
                    }
                } catch (IllegalArgumentException rse) {
                    throw rse;
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to analyze project file for required class usage.");
                } finally {
                    if (tempFile != null && tempFile.exists()) {
                        tempFile.delete();
                    }
                }
            }
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

        enrichSubmissions(jamId, Collections.singletonList(sub));
        return sub;
    }

    public ModjamSubmission vote(String jamId, String submissionId, String categoryId, int score, String userId) {
        Modjam jam = modjamRepository.findById(jamId).orElseThrow(() -> new IllegalArgumentException("Jam not found"));

        if (jam.getVotingEndDate() != null && Instant.now().isAfter(jam.getVotingEndDate())) {
            throw new IllegalArgumentException("Voting has closed for this jam.");
        }

        ModjamSubmission sub = submissionRepository.findById(submissionId).orElseThrow(() -> new IllegalArgumentException("Submission not found"));

        if (sub.getSubmitterId().equals(userId)) throw new SecurityException("Cannot vote on self");

        boolean isJudge = jam.getJudgeIds() != null && jam.getJudgeIds().contains(userId);

        sub.getVotes().removeIf(v -> v.getVoterId().equals(userId) && v.getCategoryId().equals(categoryId));

        ModjamSubmission.Vote vote = new ModjamSubmission.Vote(UUID.randomUUID().toString(), userId, categoryId, score, isJudge);
        sub.getVotes().add(vote);

        submissionRepository.save(sub);
        calculateScores(jamId);

        ModjamSubmission updated = submissionRepository.findById(submissionId).orElse(sub);
        enrichSubmissions(jamId, Collections.singletonList(updated));
        return updated;
    }

    private void calculateScores(String jamId) {
        List<ModjamSubmission> submissions = submissionRepository.findByJamId(jamId);
        if (submissions == null || submissions.isEmpty()) return;

        for (ModjamSubmission sub : submissions) {
            Map<String, List<Integer>> allScoresMap = new HashMap<>();
            Map<String, List<Integer>> publicScoresMap = new HashMap<>();
            Map<String, List<Integer>> judgeScoresMap = new HashMap<>();

            if (sub.getVotes() != null) {
                for (ModjamSubmission.Vote vote : sub.getVotes()) {
                    allScoresMap.computeIfAbsent(vote.getCategoryId(), k -> new ArrayList<>()).add(vote.getScore());
                    if (vote.isJudge()) {
                        judgeScoresMap.computeIfAbsent(vote.getCategoryId(), k -> new ArrayList<>()).add(vote.getScore());
                    } else {
                        publicScoresMap.computeIfAbsent(vote.getCategoryId(), k -> new ArrayList<>()).add(vote.getScore());
                    }
                }
            }

            sub.setCategoryScores(calculateAveragesMap(allScoresMap));
            sub.setTotalScore(calculateOverallAverage(allScoresMap));

            sub.setJudgeCategoryScores(calculateAveragesMap(judgeScoresMap));
            sub.setTotalJudgeScore(calculateOverallAverage(judgeScoresMap));

            sub.setTotalPublicScore(calculateOverallAverage(publicScoresMap));
        }

        submissions.sort((s1, s2) -> Double.compare(s2.getTotalScore() != null ? s2.getTotalScore() : 0.0, s1.getTotalScore() != null ? s1.getTotalScore() : 0.0));

        int rank = 1;
        for (ModjamSubmission sub : submissions) {
            sub.setRank(rank++);
            submissionRepository.save(sub);
        }
    }

    private Map<String, Double> calculateAveragesMap(Map<String, List<Integer>> scoresMap) {
        Map<String, Double> averaged = new HashMap<>();
        for (Map.Entry<String, List<Integer>> entry : scoresMap.entrySet()) {
            double avg = entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0.0);
            averaged.put(entry.getKey(), avg);
        }
        return averaged;
    }

    private Double calculateOverallAverage(Map<String, List<Integer>> scoresMap) {
        double totalSum = 0;
        int count = 0;
        for (Map.Entry<String, List<Integer>> entry : scoresMap.entrySet()) {
            totalSum += entry.getValue().stream().mapToInt(Integer::intValue).average().orElse(0.0);
            count++;
        }
        return count > 0 ? totalSum / count : 0.0;
    }

    public Modjam finalizeJam(String jamId, String userId, List<Map<String, String>> winnersData) {
        Modjam jam = modjamRepository.findById(jamId).orElseThrow(() -> new IllegalArgumentException("Jam not found"));
        if (!jam.getHostId().equals(userId)) throw new SecurityException("Only the host can finalize the jam");

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
        jam.setUpdatedAt(Instant.now());
        return enrichAndReturn(modjamRepository.save(jam));
    }

    @Scheduled(fixedDelay = 60000)
    public void updateJamStates() {
        List<Modjam> jams = modjamRepository.findAll();
        Instant now = Instant.now();
        for (Modjam jam : jams) {
            if ("DRAFT".equals(jam.getStatus()) || "COMPLETED".equals(jam.getStatus())) continue;

            String newStatus = jam.getStatus();
            if (jam.getStartDate() != null && now.isBefore(jam.getStartDate())) {
                newStatus = "UPCOMING";
            } else if (jam.getEndDate() != null && now.isBefore(jam.getEndDate())) {
                newStatus = "ACTIVE";
            } else if (jam.getVotingEndDate() != null && now.isBefore(jam.getVotingEndDate())) {
                newStatus = "VOTING";
            } else {
                newStatus = "AWAITING_WINNERS";
            }

            if (!newStatus.equals(jam.getStatus())) {
                String oldStatus = jam.getStatus();
                jam.setStatus(newStatus);
                modjamRepository.save(jam);

                if (jam.isHideSubmissions() && !List.of("VOTING", "COMPLETED", "AWAITING_WINNERS").contains(oldStatus)
                        && List.of("VOTING", "COMPLETED", "AWAITING_WINNERS").contains(newStatus)) {
                    modService.revealHiddenJamMods(jam.getId());
                }
            }
        }
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void cleanupStaleDrafts() {
        Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
        List<Modjam> staleDrafts = modjamRepository.findByStatusAndUpdatedAtBefore("DRAFT", thirtyDaysAgo);
        for (Modjam jam : staleDrafts) {
            submissionRepository.deleteAll(submissionRepository.findByJamId(jam.getId()));
            modjamRepository.delete(jam);
        }
    }
}