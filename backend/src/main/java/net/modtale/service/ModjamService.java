package net.modtale.service;

import net.modtale.model.jam.Modjam;
import net.modtale.model.jam.ModjamSubmission;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.project.ProjectVersion;
import net.modtale.model.user.User;
import net.modtale.repository.jam.ModjamRepository;
import net.modtale.repository.jam.ModjamSubmissionRepository;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.storage.StorageService;
import net.modtale.service.security.validation.FileValidationService;
import net.modtale.service.jam.ModjamCustomizationService;
import net.modtale.service.jam.ModjamOrganizerService;
import net.modtale.service.jam.ModjamDiscordFeedService;
import net.modtale.model.jam.Modjam.JamPermission;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.beans.BeanWrapperImpl;
import net.modtale.service.jam.ModjamPhase;
import net.modtale.service.jam.ModjamVotePersistence;
import net.modtale.service.jam.ModjamMembershipPersistence;
import net.modtale.service.security.access.AccessControlService;
import net.modtale.service.project.lifecycle.LifecycleService;
import net.modtale.service.user.account.AccountService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.BeanUtils;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    @Autowired private ProjectRepository projectRepository;
    @Autowired private StorageService storageService;
    @Autowired private FileValidationService fileValidationService;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private AccountService accountService;
    @Autowired private ModjamCustomizationService customizationService;
    @Autowired private ModjamOrganizerService organizerService;
    @Autowired private ModjamDiscordFeedService feedService;
    @Autowired private LifecycleService lifecycleService;
    @Autowired private AccessControlService accessControlService;
    @Autowired private ModjamVotePersistence votePersistence;
    @Autowired private ModjamMembershipPersistence membershipPersistence;

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

    private User browserViewer() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream().anyMatch(authority -> "ROLE_API".equals(authority.getAuthority()))) return null;
        return accountService.getCurrentUser();
    }

    private Modjam publicJam(Modjam jam) {
        Modjam response = new Modjam();
        BeanUtils.copyProperties(jam, response);
        response.setPendingJudgeInvites(new ArrayList<>());
        response.setPendingJudgeInviteUsers(new LinkedHashMap<>());
        response.setPendingOrganizerInvites(new ArrayList<>());
        return enrichAndReturn(response);
    }

    public Modjam jamForViewer(Modjam jam, User viewer) {
        return jamForViewer(jam, viewer == null ? null : viewer.getId(), viewer == null ? null : viewer.getUsername());
    }

    private Modjam jamForViewer(Modjam jam, String viewerId, String viewerName) {
        if (jam == null) return null;
        if (viewerId != null && Objects.equals(jam.getHostId(), viewerId)) return enrichAndReturn(jam);
        Modjam response = publicJam(jam);
        if (viewerId != null && jam.getPendingOrganizerInvites() != null) {
            response.setPendingOrganizerInvites(jam.getPendingOrganizerInvites().stream().filter(invite -> viewerId.equals(invite.userId())).toList());
        }
        if (viewerId != null && jam.getPendingJudgeInviteUsers() != null && jam.getPendingJudgeInviteUsers().containsKey(viewerId)) {
            String name = viewerName == null ? jam.getPendingJudgeInviteUsers().get(viewerId) : viewerName;
            if (name != null) response.setPendingJudgeInvites(List.of(name));
        }
        return response;
    }

    private void requireUpdatePermissions(Modjam jam, Modjam update, String userId) {
        if (Objects.equals(jam.getHostId(), userId)) return;
        if (!organizerService.isOrganizer(jam, userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only organizers can edit this jam.");
        if (!Objects.equals(jam.getTitle(), update.getTitle()) || !Objects.equals(jam.getDescription(), update.getDescription())
                || !Objects.equals(jam.getSlug(), update.getSlug()) || !Objects.equals(jam.getCustomCss(), update.getCustomCss())) {
            organizerService.require(jam, userId, JamPermission.EDIT_DETAILS);
        }
        if (!Objects.equals(jam.getRules(), update.getRules())) organizerService.require(jam, userId, JamPermission.EDIT_RULES);
        if (!Objects.equals(jam.getStartDate(), update.getStartDate()) || !Objects.equals(jam.getEndDate(), update.getEndDate())
                || !Objects.equals(jam.getVotingEndDate(), update.getVotingEndDate()) || !Objects.equals(jam.getStatus(), update.getStatus())
                || jam.isAllowPublicVoting() != update.isAllowPublicVoting() || jam.isAllowConcurrentVoting() != update.isAllowConcurrentVoting()
                || jam.isShowResultsBeforeVotingEnds() != update.isShowResultsBeforeVotingEnds() || jam.isOneEntryPerPerson() != update.isOneEntryPerPerson()
                || jam.isHideSubmissions() != update.isHideSubmissions() || !Objects.equals(beanProperties(jam.getRestrictions()), beanProperties(update.getRestrictions()))
                || !Objects.equals(categoryProperties(jam.getCategories()), categoryProperties(update.getCategories()))) {
            organizerService.require(jam, userId, JamPermission.MANAGE_SETTINGS);
        }
    }

    private Modjam persistEditableFields(Modjam jam, String expectedStatus) {
        // Host edits must not replace participants, ballots, invitations or role
        // grants changed by another request while the edit form was open.
        org.springframework.data.mongodb.core.query.Update update = new org.springframework.data.mongodb.core.query.Update();
        for (String field : List.of("slug", "title", "description", "rules", "customCss", "startDate", "endDate", "votingEndDate",
                "allowPublicVoting", "allowConcurrentVoting", "showResultsBeforeVotingEnds", "oneEntryPerPerson", "hideSubmissions",
                "restrictions", "categories", "status", "publishedAt", "updatedAt")) {
            update.set(field, new BeanWrapperImpl(jam).getPropertyValue(field));
        }
        Criteria expected = Criteria.where("_id").is(jam.getId()).and("status").is(expectedStatus);
        if (!"COMPLETED".equals(expectedStatus)) expected.and("finalizationHash").exists(false);
        var persisted = mongoTemplate.updateFirst(Query.query(expected), update, Modjam.class);
        if (persisted.getMatchedCount() == 0) throw new ResponseStatusException(HttpStatus.CONFLICT, "This jam changed or was finalized. Refresh before saving.");
        return jam;
    }

    private static Map<String, Object> beanProperties(Object bean) {
        if (bean == null) return Map.of();
        var wrapper = new BeanWrapperImpl(bean);
        Map<String, Object> result = new HashMap<>();
        for (var property : wrapper.getPropertyDescriptors()) {
            if (!"class".equals(property.getName()) && wrapper.isReadableProperty(property.getName())) {
                result.put(property.getName(), wrapper.getPropertyValue(property.getName()));
            }
        }
        return result;
    }

    private static List<Map<String, Object>> categoryProperties(List<Modjam.Category> categories) {
        return categories == null ? List.of() : categories.stream().map(ModjamService::beanProperties).toList();
    }

    private void requireHost(Modjam jam, String userId) {
        if (!Objects.equals(jam.getHostId(), userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host can manage this jam");
        }
    }


    private static String effectivePhase(Modjam jam, Instant now) {
        return ModjamPhase.current(jam, now);
    }


    private boolean canManageResults(Modjam jam, String userId) {
        return userId != null && (organizerService.permits(jam, userId, JamPermission.VIEW_RESULTS)
                || (jam.getJudgeIds() != null && jam.getJudgeIds().contains(userId)));
    }

    private ModjamSubmission submissionForViewer(Modjam jam, ModjamSubmission submission, String userId, boolean isAdmin) {
        // Project a separate response object: never redact a document that will be saved.
        ModjamSubmission response = new ModjamSubmission();
        BeanUtils.copyProperties(submission, response);
        boolean canManage = isAdmin || canManageResults(jam, userId);
        response.setVotes(submission.getVotes() == null ? new ArrayList<>() : submission.getVotes().stream()
                .filter(vote -> canManage || (userId != null && Objects.equals(vote.getVoterId(), userId)))
                .toList());

        boolean resultsVisible = canManage || jam.isShowResultsBeforeVotingEnds()
                || List.of("COMPLETED", "AWAITING_WINNERS").contains(effectivePhase(jam, Instant.now()));
        if (!resultsVisible) {
            response.setCategoryScores(null);
            response.setTotalScore(null);
            response.setJudgeCategoryScores(null);
            response.setTotalJudgeScore(null);
            response.setTotalPublicScore(null);
            response.setRank(null);
        }
        return response;

    }

    private void enrichSubmissions(String jamId, List<ModjamSubmission> subs, Map<String, Project> projectMap) {
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
        for (Project p : projectMap.values()) {
            if (visibleProjectIds.contains(p.getId()) && p.getComments() != null) {
                for (net.modtale.model.project.Comment c : p.getComments()) {
                    if (!c.getUserId().equals(p.getAuthorId())) {
                        userCommentCount.put(c.getUserId(), userCommentCount.getOrDefault(c.getUserId(), 0) + 1);
                    }
                }
            }
        }

        for (ModjamSubmission sub : subs) {
            Project project = projectMap.get(sub.getProjectId());
            if (project != null) {
                sub.setProjectTitle(project.getTitle());
                sub.setProjectImageUrl(project.getImageUrl());
                sub.setProjectBannerUrl(project.getBannerUrl());
                sub.setProjectAuthor(project.getAuthor());
                sub.setProjectDescription(project.getDescription());
            }
            sub.setVotesCast(userVoteCount.getOrDefault(sub.getSubmitterId(), 0));
            sub.setCommentsGiven(userCommentCount.getOrDefault(sub.getSubmitterId(), 0));
        }
    }

    private void enrichSubmissions(String jamId, List<ModjamSubmission> subs) {
        if (subs == null || subs.isEmpty()) return;
        List<String> projectIds = subs.stream().map(ModjamSubmission::getProjectId).toList();
        Iterable<Project> projectsIterable = projectRepository.findAllById(projectIds);
        Map<String, Project> projectMap = new HashMap<>();
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
        return modjamRepository.findAll().stream().filter(jam -> !"DRAFT".equals(jam.getStatus())).map(this::publicJam).collect(Collectors.toList());
    }

    public List<Modjam> getUserHostedJams(String hostId) {
        return modjamRepository.findAll().stream().filter(jam -> organizerService.isOrganizer(jam, hostId)).map(jam -> jamForViewer(jam, hostId, null)).collect(Collectors.toList());
    }

    public Modjam getJamBySlug(String slug) {
        Modjam jam = modjamRepository.findBySlug(slug).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jam not found"));
        User viewer = browserViewer();
        String viewerId = viewer == null ? null : viewer.getId();
        boolean invited = viewerId != null && jam.getPendingOrganizerInvites() != null
                && jam.getPendingOrganizerInvites().stream().anyMatch(invite -> viewerId.equals(invite.userId()));
        if ("DRAFT".equals(jam.getStatus()) && !organizerService.isOrganizer(jam, viewerId) && !invited) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Jam not found");
        }
        return jamForViewer(jam, viewerId, viewer == null ? null : viewer.getUsername());
    }

    public Modjam createJam(Modjam jam, String hostId, String hostName) {
        jam.setId(null);
        jam.setHostId(hostId);
        jam.setHostName(hostName);
        jam.setPublishedAt(null);
        jam.setWinnersAnnouncedAt(null);
        customizationService.validate(jam);
        if ("COMPLETED".equals(jam.getStatus())) throw new IllegalArgumentException("Create a draft and announce winners after voting closes.");
        // Incoming document fields do not confer membership or management authority.
        jam.setParticipantIds(new ArrayList<>());
        jam.setJudgeIds(new ArrayList<>());
        jam.setPendingJudgeInvites(new ArrayList<>());
        jam.setPendingJudgeInviteUsers(new LinkedHashMap<>());
        jam.setOrganizerRoles(new ArrayList<>());
        jam.setOrganizerMembers(new ArrayList<>());
        jam.setPendingOrganizerInvites(new ArrayList<>());

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

        if (!"DRAFT".equals(jam.getStatus())) {
            jam.setStatus(effectivePhase(jam, Instant.now()));
            jam.setPublishedAt(Instant.now());
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

        Modjam saved = modjamRepository.save(jam);
        feedService.recordMilestones(saved);
        return enrichAndReturn(saved);
    }

    public Modjam updateJam(String id, Modjam updatedJam, String userId) {
        Modjam jam = modjamRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Jam not found"));
        requireUpdatePermissions(jam, updatedJam, userId);
        customizationService.validate(updatedJam);
        if ("COMPLETED".equals(updatedJam.getStatus()) != "COMPLETED".equals(jam.getStatus())) {
            throw new IllegalArgumentException("Announce winners to complete a jam; completed jams cannot be reopened.");
        }

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
        jam.setCustomCss(updatedJam.getCustomCss());
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
        if ("DRAFT".equals(oldStatus) && !"DRAFT".equals(targetStatus) && jam.getPublishedAt() == null) jam.setPublishedAt(Instant.now());
        jam.setUpdatedAt(Instant.now());

        if (jam.isHideSubmissions() && !List.of("VOTING", "COMPLETED", "AWAITING_WINNERS").contains(oldStatus)
                && List.of("VOTING", "COMPLETED", "AWAITING_WINNERS").contains(targetStatus)) {
            revealHiddenJamProjects(jam.getId());
        }

        Modjam saved = persistEditableFields(jam, oldStatus);
        feedService.recordMilestones(saved);
        return enrichAndReturn(saved);
    }

    public Modjam inviteJudge(String jamId, String username, String hostId) {
        Modjam jam = modjamRepository.findById(jamId).orElseThrow(() -> new IllegalArgumentException("Jam not found"));
        organizerService.require(jam, hostId, JamPermission.MANAGE_JUDGES);

        User targetUser = username == null ? null : userRepository.findByUsernameIgnoreCase(username.trim()).orElse(null);

        if (targetUser == null || targetUser.isDeleted()) {
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
        if (jam.getPendingJudgeInviteUsers() != null && jam.getPendingJudgeInviteUsers().containsKey(targetUser.getId())) {
            throw new IllegalArgumentException("User is already invited.");
        }

        Modjam updated = membershipPersistence.inviteJudge(jamId, targetUser.getId(), targetUser.getUsername());
        if (updated == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This invitation changed. Refresh the jam before inviting again.");
        }

        org.bson.Document notif = new org.bson.Document();
        notif.put("userId", targetUser.getId());
        notif.put("title", "Jam Judge Invitation");
        notif.put("message", "You have been invited to be a judge for " + jam.getTitle());
        notif.put("link", "/jam/" + jam.getSlug() + "/overview");
        notif.put("read", false);
        notif.put("createdAt", Instant.now());
        mongoTemplate.save(notif, "notifications");

        return enrichAndReturn(updated);
    }

    public Modjam removeJudge(String jamId, String username, String hostId) {
        Modjam jam = modjamRepository.findById(jamId).orElseThrow(() -> new IllegalArgumentException("Jam not found"));
        organizerService.require(jam, hostId, JamPermission.MANAGE_JUDGES);

        Set<String> userIds = new LinkedHashSet<>();
        if (jam.getPendingJudgeInviteUsers() != null) {
            jam.getPendingJudgeInviteUsers().entrySet().stream()
                    .filter(entry -> entry.getValue() != null && entry.getValue().equalsIgnoreCase(username))
                    .forEach(entry -> userIds.add(entry.getKey()));
        }
        User targetUser = username == null ? null : userRepository.findByUsernameIgnoreCase(username.trim()).orElse(null);
        if (targetUser != null) userIds.add(targetUser.getId());
        Modjam updated = membershipPersistence.removeJudges(jamId, new ArrayList<>(userIds));
        if (updated == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Jam no longer exists.");
        return enrichAndReturn(updated);
    }

    public Modjam acceptJudgeInvite(String jamId, String userId, String username) {
        Modjam jam = modjamRepository.findById(jamId).orElseThrow(() -> new IllegalArgumentException("Jam not found"));

        if (jam.getPendingJudgeInviteUsers() == null || !jam.getPendingJudgeInviteUsers().containsKey(userId)) {
            throw new IllegalArgumentException("You don't have a pending invite for this jam.");
        }

        Modjam updated = membershipPersistence.answerJudgeInvite(jamId, userId, true);
        if (updated == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "This invitation is no longer pending.");
        return jamForViewer(updated, userId, username);
    }

    public Modjam declineJudgeInvite(String jamId, String userId, String username) {
        Modjam jam = modjamRepository.findById(jamId).orElseThrow(() -> new IllegalArgumentException("Jam not found"));

        if (jam.getPendingJudgeInviteUsers() == null || !jam.getPendingJudgeInviteUsers().containsKey(userId)) {
            return jamForViewer(jam, userId, username);
        }
        Modjam updated = membershipPersistence.answerJudgeInvite(jamId, userId, false);
        if (updated == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "This invitation is no longer pending.");
        return jamForViewer(updated, userId, username);
    }

    public void updateIcon(String jamId, MultipartFile file, String userId) {
        Modjam jam = modjamRepository.findById(jamId)
                .orElseThrow(() -> new IllegalArgumentException("Jam not found"));
        organizerService.require(jam, userId, JamPermission.EDIT_DETAILS);
        fileValidationService.validateIcon(file);

        try {
            String pathPrefix = "modjams/" + jamId + "/icon";
            String storageKey = storageService.upload(file, pathPrefix);
            String publicUrl = storageService.getPublicUrl(storageKey);
            jam.setImageUrl(publicUrl);
            jam.setUpdatedAt(Instant.now());
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(jamId)),
                    new org.springframework.data.mongodb.core.query.Update().set("imageUrl", publicUrl).set("updatedAt", jam.getUpdatedAt()), Modjam.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to upload icon", e);
        }
    }

    public void updateBanner(String jamId, MultipartFile file, String userId) {
        Modjam jam = modjamRepository.findById(jamId)
                .orElseThrow(() -> new IllegalArgumentException("Jam not found"));
        organizerService.require(jam, userId, JamPermission.EDIT_DETAILS);
        fileValidationService.validateBanner(file);

        try {
            String pathPrefix = "modjams/" + jamId + "/banner";
            String storageKey = storageService.upload(file, pathPrefix);
            String publicUrl = storageService.getPublicUrl(storageKey);
            jam.setBannerUrl(publicUrl);
            jam.setUpdatedAt(Instant.now());
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(jamId)),
                    new org.springframework.data.mongodb.core.query.Update().set("bannerUrl", publicUrl).set("updatedAt", jam.getUpdatedAt()), Modjam.class);
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

        User currentUser = accountService.getCurrentUser();
        // A scoped API key is never a browser viewer with its owner's private access.
        if (accessControlService.isApiKey(SecurityContextHolder.getContext().getAuthentication())) currentUser = null;
        boolean isAdmin = accessControlService.canViewPrivilegedProjectData(currentUser);
        boolean isHost = currentUser != null && organizerService.permits(jam, currentUser.getId(), JamPermission.VIEW_RESULTS);

        boolean isJamHiding = ModjamPhase.hidesEntries(jam, Instant.now());

        List<String> projectIds = allSubs.stream().map(ModjamSubmission::getProjectId).toList();
        Iterable<Project> projectsIterable = projectRepository.findAllById(projectIds);
        Map<String, Project> projectMap = new HashMap<>();
        projectsIterable.forEach(p -> projectMap.put(p.getId(), p));

        List<ModjamSubmission> visibleSubs = new ArrayList<>();

        for (ModjamSubmission sub : allSubs) {
            Project project = projectMap.get(sub.getProjectId());
            if (project == null) continue;

            boolean isSubmitter = currentUser != null && currentUser.getId().equals(sub.getSubmitterId());
            boolean canSeeHidden = isAdmin || isHost || isSubmitter;
            boolean isPublicProject = project.getStatus() == ProjectStatus.PUBLISHED || project.getStatus() == ProjectStatus.ARCHIVED;

            if (!canSeeHidden) {
                if (!isPublicProject || isJamHiding) {
                    continue; // Backend enforces filtering out unreleased or jam-hidden projects
                }
            }
            visibleSubs.add(sub);
        }

        enrichSubmissions(jamId, visibleSubs, projectMap);
        String viewerId = currentUser == null ? null : currentUser.getId();
        return visibleSubs.stream().map(sub -> submissionForViewer(jam, sub, viewerId, isAdmin)).toList();
    }

    public Modjam participate(String jamId, String userId) {
        Modjam jam = modjamRepository.findById(jamId)
                .orElseThrow(() -> new IllegalArgumentException("Jam not found"));
        if (!List.of("UPCOMING", "ACTIVE").contains(effectivePhase(jam, Instant.now()))) {
            throw new IllegalArgumentException("Participation is closed for this jam.");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.getJoinedModjamIds() != null) {
            for (String joinedId : user.getJoinedModjamIds()) {
                if (joinedId.equals(jamId)) continue;

                Optional<Modjam> optOtherJam = modjamRepository.findById(joinedId);
                if (optOtherJam.isPresent()) {
                    Modjam otherJam = optOtherJam.get();
                    boolean otherIsActive = List.of("ACTIVE", "UPCOMING").contains(effectivePhase(otherJam, Instant.now()));
                    boolean thisIsActive = List.of("ACTIVE", "UPCOMING").contains(effectivePhase(jam, Instant.now()));

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

        Modjam updated = membershipPersistence.participate(jamId, userId, true);
        if (updated == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Jam no longer exists.");
        return jamForViewer(updated, userId, null);
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

        Modjam updated = membershipPersistence.participate(jamId, userId, false);
        if (updated == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Jam no longer exists.");
        return jamForViewer(updated, userId, null);
    }

    public ModjamSubmission submitProject(String jamId, String projectId, String userId) {
        Modjam jam = modjamRepository.findById(jamId)
                .orElseThrow(() -> new IllegalArgumentException("Jam not found"));

        if (!"ACTIVE".equals(effectivePhase(jam, Instant.now()))) {
            throw new IllegalArgumentException("Submissions are closed.");
        }

        if (jam.getParticipantIds() == null || !jam.getParticipantIds().contains(userId)) {
            throw new IllegalArgumentException("Join this jam before submitting a project.");
        }

        if (projectId == null || projectId.isBlank()) {
            throw new IllegalArgumentException("Select a project to submit.");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new IllegalArgumentException("Project not found"));

        if (!project.getAuthorId().equals(userId)) {
            throw new SecurityException("Not your project");
        }

        if (!List.of(ProjectStatus.PUBLISHED, ProjectStatus.PENDING, ProjectStatus.UNLISTED, ProjectStatus.DRAFT).contains(project.getStatus())) {
            throw new IllegalArgumentException("Project cannot be submitted in its current state.");
        }

        if (jam.isHideSubmissions() && accessControlService.isPubliclyReadable(project)) {
            throw new IllegalArgumentException("This jam hides submissions until voting opens. You cannot submit an already-public project.");
        }

        List<ModjamSubmission> existing = submissionRepository.findByJamIdAndSubmitterId(jamId, userId);

        if (jam.isOneEntryPerPerson() && !existing.isEmpty()) {
            throw new IllegalArgumentException("This jam is restricted to one entry per person.");
        }

        if (existing.stream().anyMatch(s -> s.getProjectId().equals(projectId))) {
            throw new IllegalArgumentException("Already submitted.");
        }
        if (submissionRepository.findByProjectId(projectId).stream().anyMatch(s -> Objects.equals(s.getJamId(), jamId))) {
            throw new IllegalArgumentException("This project already has an entry in this jam.");
        }

        Modjam.Restrictions res = jam.getRestrictions();
        if (res != null) {
            if (res.isRequireNoPriorProjects() || res.isRequirePriorProjects()) {
                long priorPublishedProjects = projectRepository.findByAuthorIdList(userId).stream()
                        .filter(p -> !p.getId().equals(projectId) && p.getStatus() == ProjectStatus.PUBLISHED)
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
                if (!res.getAllowedClassifications().contains(project.getClassification().name())) {
                    throw new IllegalArgumentException("Project classification is not allowed for this jam.");
                }
            }

            if (res.getAllowedLicenses() != null && !res.getAllowedLicenses().isEmpty()) {
                if (!res.getAllowedLicenses().contains(project.getLicense())) {
                    throw new IllegalArgumentException("Project license is not allowed for this jam.");
                }
            }

            customizationService.validateProjectVersions(res, project);

            if (res.getRequiredDependencyId() != null && !res.getRequiredDependencyId().trim().isEmpty()) {
                String requiredDependencyId = res.getRequiredDependencyId().trim();
                boolean hasRequiredDependency = project.getVersions() != null && project.getVersions().stream()
                        .filter(java.util.Objects::nonNull)
                        .filter(version -> version.getDependencies() != null)
                        .flatMap(version -> version.getDependencies().stream())
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(dependency -> requiredDependencyId.equals(dependency.getProjectId()));
                if (!hasRequiredDependency) {
                    throw new IllegalArgumentException("Project is missing the required dependency.");
                }
            }

            int contributorCount = (project.getTeamMembers() != null ? project.getTeamMembers().size() : 0) + 1;
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

                ProjectVersion latestVersion = project.getVersions().get(project.getVersions().size() - 1);
                String fileUrl = latestVersion.getFileUrl();
                if (fileUrl == null || fileUrl.isEmpty()) {
                    throw new IllegalArgumentException("Project version has no file associated.");
                }

                String storageKey = extractStorageKey(fileUrl);
                File tempFile = null;
                try {
                    tempFile = Files.createTempFile("jam_check_", ".jar").toFile();
                    try (InputStream is = storageService.getStream(storageKey);
                         FileOutputStream fos = new FileOutputStream(tempFile)) {
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

        // Jam eligibility is read-only. Only a fully eligible draft enters the
        // canonical review workflow, including verification, validation and scans.
        if (project.getStatus() == ProjectStatus.DRAFT) {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));
            lifecycleService.submitProjectForModjam(projectId, user, jamId);
            project = projectRepository.findById(projectId)
                    .orElseThrow(() -> new IllegalArgumentException("Project not found"));
        }

        ModjamSubmission sub = new ModjamSubmission();
        sub.setId(submissionIdentity(jamId, projectId, userId, jam.isOneEntryPerPerson()));
        sub.setJamId(jamId);
        sub.setProjectId(projectId);
        sub.setSubmitterId(userId);

        try {
            submissionRepository.insert(sub);
        } catch (org.springframework.dao.DuplicateKeyException duplicate) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This project or participant already has an entry in this jam.");
        }

        if (project.getModjamIds() == null) project.setModjamIds(new ArrayList<>());
        if (!project.getModjamIds().contains(jamId)) {
            project.getModjamIds().add(jamId);
            mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(projectId)),
                    new org.springframework.data.mongodb.core.query.Update().addToSet("modjamIds", jamId), Project.class);
        }

        enrichSubmissions(jamId, Collections.singletonList(sub));
        return sub;
    }

    private static String submissionIdentity(String jamId, String projectId, String userId, boolean oneEntryPerPerson) {
        String key = jamId + "\0" + (oneEntryPerPerson ? "person\0" + userId : "project\0" + projectId);
        try {
            return "modjam-" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(key.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Submission identity could not be generated.", impossible);
        }
    }

    public ModjamSubmission vote(String jamId, String submissionId, String categoryId, int score, String userId) {
        Modjam jam = modjamRepository.findById(jamId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Jam not found"));
        Instant now = Instant.now();
        Instant votingStart = jam.isAllowConcurrentVoting() ? jam.getStartDate() : jam.getEndDate();
        if (!List.of("ACTIVE", "VOTING").contains(effectivePhase(jam, now)) || votingStart == null
                || jam.getVotingEndDate() == null || now.isBefore(votingStart)
                || !now.isBefore(jam.getVotingEndDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voting is closed for this jam.");
        }

        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to vote.");
        }
        boolean isJudge = Objects.equals(jam.getHostId(), userId)
                || jam.getJudgeIds() != null && jam.getJudgeIds().contains(userId);
        if (!isJudge && !jam.isAllowPublicVoting()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the host and invited judges can vote.");
        }

        Modjam.Category category = jam.getCategories() == null ? null : jam.getCategories().stream()
                .filter(Objects::nonNull).filter(cat -> Objects.equals(cat.getId(), categoryId)).findFirst().orElse(null);
        if (categoryId == null || category == null || score < 1 || score > category.getMaxScore()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a valid category and a score within its range.");
        }

        ModjamSubmission sub = submissionRepository.findById(submissionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found"));
        if (!Objects.equals(sub.getJamId(), jamId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission not found in this jam.");
        }
        Project project = projectRepository.findById(sub.getProjectId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (Objects.equals(sub.getSubmitterId(), userId) || Objects.equals(project.getAuthorId(), userId)
                || (project.getTeamMembers() != null && project.getTeamMembers().stream()
                .filter(Objects::nonNull).anyMatch(member -> Objects.equals(member.getUserId(), userId)))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You cannot vote on your own entry.");
        }
        if (!List.of(ProjectStatus.PUBLISHED, ProjectStatus.ARCHIVED).contains(project.getStatus())
                || ModjamPhase.hidesEntries(jam, now)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This entry is not available for voting.");
        }

        ModjamSubmission.Vote vote = new ModjamSubmission.Vote(UUID.randomUUID().toString(), userId, categoryId, score, isJudge);
        if (votePersistence.replaceBallot(jamId, submissionId, vote) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Submission no longer exists in this jam.");
        }
        calculateScores(jamId);

        ModjamSubmission updated = submissionRepository.findById(submissionId).orElse(sub);
        enrichSubmissions(jamId, Collections.singletonList(updated));
        return submissionForViewer(jam, updated, userId, false);
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
            votePersistence.saveScores(sub);
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

    private static String finalizationHash(Map<String, String> awards) {
        StringBuilder canonical = new StringBuilder();
        new TreeMap<>(awards).forEach((id, title) -> canonical.append(id.length()).append(':').append(id)
                .append(title.length()).append(':').append(title));
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 is required for finalization integrity.", unavailable);
        }
    }

    public Modjam finalizeJam(String jamId, String userId, List<Map<String, String>> winnersData) {
        Modjam jam = modjamRepository.findById(jamId).orElseThrow(() -> new IllegalArgumentException("Jam not found"));
        organizerService.require(jam, userId, JamPermission.ANNOUNCE_WINNERS);
        String phase = effectivePhase(jam, Instant.now());
        if (!"AWAITING_WINNERS".equals(phase) && !"COMPLETED".equals(phase)) {
            throw new IllegalArgumentException("Wait for voting to close before finalizing this jam.");
        }

        List<ModjamSubmission> allSubs = submissionRepository.findByJamId(jamId);
        if (allSubs == null) allSubs = new ArrayList<>();
        Set<String> submissionIds = allSubs.stream().map(ModjamSubmission::getId).collect(Collectors.toSet());
        if (winnersData == null) {
            throw new IllegalArgumentException("Provide the winners for this jam.");
        }
        Map<String, String> awards = new LinkedHashMap<>();
        for (Map<String, String> winner : winnersData) {
            if (winner == null || winner.get("submissionId") == null
                    || !submissionIds.contains(winner.get("submissionId"))) {
                throw new IllegalArgumentException("Every winner must be a submission in this jam.");
            }
            String submissionId = winner.get("submissionId");
            String awardTitle = winner.get("awardTitle");
            if (awardTitle == null || awardTitle.isBlank() || awardTitle.length() > 200) {
                throw new IllegalArgumentException("Award titles must contain 1-200 characters.");
            }
            if (awards.putIfAbsent(submissionId, awardTitle.trim()) != null) {
                throw new IllegalArgumentException("A submission can only be selected once.");
            }
        }

        if ("COMPLETED".equals(jam.getStatus())) {
            boolean identical = allSubs.stream().allMatch(sub ->
                    sub.isWinner() == awards.containsKey(sub.getId())
                            && Objects.equals(sub.getAwardTitle(), awards.get(sub.getId())));
            if (!identical) {
                throw new IllegalArgumentException("This jam has already been finalized.");
            }
            return enrichAndReturn(jam);
        }

        String finalizationHash = finalizationHash(awards);
        Criteria matchingClaim = new Criteria().orOperator(Criteria.where("finalizationHash").exists(false),
                Criteria.where("finalizationHash").is(finalizationHash));
        Query claim = Query.query(new Criteria().andOperator(Criteria.where("_id").is(jamId).and("status").is(jam.getStatus()).and("updatedAt").is(jam.getUpdatedAt())
                .and("startDate").is(jam.getStartDate()).and("endDate").is(jam.getEndDate()).and("votingEndDate").is(jam.getVotingEndDate()), matchingClaim));
        Modjam claimed = mongoTemplate.findAndModify(claim,
                new org.springframework.data.mongodb.core.query.Update().set("finalizationHash", finalizationHash).set("finalizationAwards", awards),
                org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true), Modjam.class);
        if (claimed == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "This jam is already finalizing or completed. Refresh to see its winners.");

        calculateScores(jamId);
        allSubs = submissionRepository.findByJamId(jamId);
        if (allSubs == null) allSubs = new ArrayList<>();
        for (ModjamSubmission sub : allSubs) {
            sub.setWinner(awards.containsKey(sub.getId()));
            sub.setAwardTitle(awards.get(sub.getId()));
            votePersistence.saveAward(sub);
        }

        jam.setStatus("COMPLETED");
        jam.setWinnersAnnouncedAt(Instant.now());
        jam.setUpdatedAt(Instant.now());
        var persisted = mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(jamId).and("status").ne("COMPLETED").and("finalizationHash").is(finalizationHash)),
                new org.springframework.data.mongodb.core.query.Update().set("status", "COMPLETED")
                        .set("updatedAt", jam.getUpdatedAt()).set("winnersAnnouncedAt", jam.getWinnersAnnouncedAt()), Modjam.class);
        if (persisted.getMatchedCount() == 0) return enrichAndReturn(modjamRepository.findById(jamId).orElse(jam));
        Modjam saved = jam;
        feedService.recordMilestones(saved);
        return enrichAndReturn(saved);
    }

    @Scheduled(fixedDelay = 60000)
    public void updateJamStates() {
        List<Modjam> jams = modjamRepository.findAll();
        Instant now = Instant.now();
        for (Modjam jam : jams) {
            if ("DRAFT".equals(jam.getStatus())) continue;
            if ("COMPLETED".equals(jam.getStatus())) {
                revealHiddenJamProjects(jam.getId());
                continue;
            }

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
                mongoTemplate.updateFirst(Query.query(Criteria.where("_id").is(jam.getId()).and("status").is(oldStatus).and("finalizationHash").exists(false)),
                        new org.springframework.data.mongodb.core.query.Update().set("status", newStatus), Modjam.class);

            }
            // Retry release independently of a one-time phase transition. Review
            // and scanning can complete after the jam enters its voting phase.
            if (!ModjamPhase.hidesEntries(jam, now)) revealHiddenJamProjects(jam.getId());
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

    private void revealHiddenJamProjects(String jamId) {
        Query query = new Query(
                Criteria.where("modjamIds").is(jamId)
                        .and("modjamPublicationPending").is(true)
                        .and("status").is(ProjectStatus.PRIVATE)
                        .and("deletedAt").is(null)
        );
        List<Project> hidden = mongoTemplate.find(query, Project.class);
        for (Project project : hidden) {
            lifecycleService.releaseModjamEmbargo(project);
        }
    }
}
