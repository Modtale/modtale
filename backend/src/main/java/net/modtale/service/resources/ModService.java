package net.modtale.service.resources;

import net.modtale.model.resources.*;
import net.modtale.model.user.User;
import net.modtale.repository.resources.ModRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.AnalyticsService;
import net.modtale.service.security.SanitizationService;
import net.modtale.service.security.FileValidationService;
import net.modtale.service.security.WardenClientService;
import net.modtale.service.user.NotificationService;
import net.modtale.service.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class ModService {

    private static final Logger logger = LoggerFactory.getLogger(ModService.class);

    private static final Set<String> ALLOWED_TAGS = Set.of(
            "Adventure", "RPG", "Sci-Fi", "Fantasy", "Survival", "Magic", "Tech", "Exploration",
            "Minigame", "PvP", "Parkour", "Hardcore", "Skyblock", "Puzzle", "Quests",
            "Economy", "Protection", "Admin Tools", "Chat", "Anti-Cheat", "Performance",
            "Library", "API", "Mechanics", "World Gen", "Recipes", "Loot Tables", "Functions",
            "Decoration", "Vanilla+", "Kitchen Sink", "City", "Landscape", "Spawn", "Lobby",
            "Medieval", "Modern", "Futuristic", "Models", "Textures", "Animations", "Particles"
    );

    private static final Set<String> ALLOWED_GAME_VERSIONS = Set.of(
            "Release 1.1", "Release 1.0", "Beta 0.9"
    );

    private static final Set<String> ALLOWED_CLASSIFICATIONS = Set.of(
            "PLUGIN", "DATA", "ART", "SAVE", "MODPACK"
    );

    private static final Pattern STRICT_VERSION_PATTERN = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)$");
    private static final Pattern REPO_URL_PATTERN = Pattern.compile("^https:\\/\\/(github\\.com|gitlab\\.com)\\/[\\w.-]+\\/[\\w.-]+$");
    private static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9](?:[a-z0-9-]{1,48}[a-z0-9])?$");

    @Autowired private ModRepository modRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserService userService;
    @Autowired private SanitizationService sanitizer;
    @Autowired private StorageService storageService;
    @Autowired private MongoTemplate mongoTemplate;
    @Autowired private FileValidationService validationService;
    @Autowired private AnalyticsService analyticsService;
    @Autowired private NotificationService notificationService;
    @Autowired private CacheManager cacheManager;
    @Autowired private WardenClientService wardenService;

    @Lazy
    @Autowired
    private ModService self;

    @Value("${app.webhook.url}")
    private String webhookUrl;

    @Value("${app.webhook.key}")
    private String webhookKey;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private boolean isAdmin(User user) {
        return user != null && user.getRoles() != null && user.getRoles().contains("ADMIN");
    }

    private void ensureEditable(Mod mod) {
        if ("PENDING".equals(mod.getStatus())) {
            throw new IllegalStateException("Pending projects cannot be modified. Please revert to draft first.");
        }
        if ("ARCHIVED".equals(mod.getStatus())) {
            throw new IllegalStateException("Archived projects are read-only. Please unarchive the project to make changes.");
        }
    }

    public void validateTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            throw new IllegalArgumentException("At least one tag is required.");
        }
        List<String> invalidTags = tags.stream()
                .filter(tag -> !ALLOWED_TAGS.contains(tag))
                .collect(Collectors.toList());

        if (!invalidTags.isEmpty()) {
            throw new IllegalArgumentException("Invalid tags detected: " + String.join(", ", invalidTags));
        }
    }

    public void validateVersionNumber(String version) {
        if (version == null || !STRICT_VERSION_PATTERN.matcher(version).matches()) {
            throw new IllegalArgumentException("Version number must follow strict X.Y.Z format (e.g., 1.0.0). No suffixes allowed.");
        }
    }

    public void validateSlug(String slug) {
        if (slug == null || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException("Invalid URL Slug. Must be 3-50 characters, lowercase alphanumeric with dashes, and cannot start or end with a dash.");
        }
    }

    public void validateClassification(String classification) {
        if (classification == null || !ALLOWED_CLASSIFICATIONS.contains(classification)) {
            throw new IllegalArgumentException("Invalid classification: " + classification + ". Allowed: " + ALLOWED_CLASSIFICATIONS);
        }
    }

    public void validateRepositoryUrl(String url) {
        if (url != null && !url.isEmpty() && !REPO_URL_PATTERN.matcher(url).matches()) {
            throw new IllegalArgumentException("Invalid Repository URL. Must be a valid HTTPS link to GitHub or GitLab.");
        }
    }

    public List<String> getAllowedTags() {
        return ALLOWED_TAGS.stream().sorted().collect(Collectors.toList());
    }

    public List<String> getAllowedGameVersions() {
        return ALLOWED_GAME_VERSIONS.stream().sorted(Comparator.reverseOrder()).collect(Collectors.toList());
    }

    public List<String> getAllowedClassifications() {
        return new ArrayList<>(ALLOWED_CLASSIFICATIONS);
    }

    public boolean hasEditPermission(Mod mod, User user) {
        if (mod == null || user == null) return false;

        if (mod.getAuthor().equalsIgnoreCase(user.getUsername())) return true;

        Query query = new Query(Criteria.where("username").regex("^" + Pattern.quote(mod.getAuthor()) + "$", "i"));
        User authorUser = mongoTemplate.findOne(query, User.class);

        if (authorUser != null && authorUser.getAccountType() == User.AccountType.ORGANIZATION) {
            boolean isOrgMember = authorUser.getOrganizationMembers().stream()
                    .anyMatch(m -> m.getUserId().equals(user.getId()));

            if (isOrgMember) return true;
        }

        return (mod.getContributors() != null && mod.getContributors().stream().anyMatch(c -> c.equalsIgnoreCase(user.getUsername())));
    }

    public boolean isOwner(Mod mod, User user) {
        if (mod == null || user == null) return false;

        if (mod.getAuthor().equalsIgnoreCase(user.getUsername())) return true;

        Query query = new Query(Criteria.where("username").regex("^" + Pattern.quote(mod.getAuthor()) + "$", "i"));
        User authorUser = mongoTemplate.findOne(query, User.class);

        if (authorUser != null && authorUser.getAccountType() == User.AccountType.ORGANIZATION) {
            return authorUser.getOrganizationMembers().stream()
                    .anyMatch(m -> m.getUserId().equals(user.getId()) && "ADMIN".equalsIgnoreCase(m.getRole()));
        }

        return false;
    }

    public boolean isTitleTaken(String title) {
        return modRepository.existsByTitleIgnoreCase(title);
    }

    public Page<Mod> getMods(
            List<String> tags, String search, int page, int size, String sortBy,
            String gameVersion, String contentType, Double minRating, Integer minDownloads, String viewCategory,
            String dateRange, String author
    ) {
        Page<Mod> results = self.getModsCached(tags, search, page, size, sortBy, gameVersion, contentType, minRating, minDownloads, viewCategory, dateRange, author);

        if (results != null && !results.isEmpty()) {
            List<String> idsToCheck = results.getContent().stream().map(Mod::getId).collect(Collectors.toList());
            Query query = new Query(Criteria.where("id").in(idsToCheck));
            long count = mongoTemplate.count(query, Mod.class);

            if (count != idsToCheck.size()) {
                logger.warn("Cache verification failed for getMods. Invalidating 'projectSearch_v3' and retrying.");
                Objects.requireNonNull(cacheManager.getCache("projectSearch_v3")).clear();
                return self.getModsCached(tags, search, page, size, sortBy, gameVersion, contentType, minRating, minDownloads, viewCategory, dateRange, author);
            }
        }

        return results;
    }

    @Cacheable(
            value = "projectSearch_v3",
            key = "{#tags, #search, #page, #size, #sortBy, #gameVersion, #contentType, #minRating, #minDownloads, #viewCategory, #dateRange, #author}",
            condition = "!('Favorites'.equals(#viewCategory))"
    )
    public Page<Mod> getModsCached(
            List<String> tags, String search, int page, int size, String sortBy,
            String gameVersion, String contentType, Double minRating, Integer minDownloads, String viewCategory,
            String dateRange, String author
    ) {
        if ("Favorites".equals(viewCategory)) {
            User currentUserObj = userService.getCurrentUser();
            List<String> likedIds = (currentUserObj != null && currentUserObj.getLikedModIds() != null)
                    ? currentUserObj.getLikedModIds()
                    : new ArrayList<>();
            PageRequest pageable = PageRequest.of(page, size, Sort.by("title"));
            return modRepository.findFavorites(likedIds, search != null ? search : "", pageable);
        }

        Sort sort = switch (sortBy != null ? sortBy : "relevance") {
            case "rating" -> Sort.by("rating").descending();
            case "downloads" -> Sort.by("downloadCount").descending();
            case "updated" -> Sort.by("updatedAt").descending();
            case "new", "newest" -> Sort.by("createdAt").descending();
            case "favorites" -> Sort.by("favoriteCount").descending();
            default -> Sort.unsorted();
        };

        PageRequest pageable = PageRequest.of(page, size, sort);
        User currentUserObj = userService.getCurrentUser();
        String currentUsername = (currentUserObj != null) ? currentUserObj.getUsername() : null;

        LocalDate dateCutoff = null;
        if (dateRange != null && !dateRange.equals("all") && !dateRange.isEmpty()) {
            try {
                if (dateRange.equals("7d")) dateCutoff = LocalDate.now().minusDays(7);
                else if (dateRange.equals("30d")) dateCutoff = LocalDate.now().minusDays(30);
                else if (dateRange.equals("90d")) dateCutoff = LocalDate.now().minusDays(90);
                else if (dateRange.equals("1y")) dateCutoff = LocalDate.now().minusYears(1);
                else dateCutoff = LocalDate.parse(dateRange.substring(0, 10));
            } catch (Exception e) {
                logger.warn("Invalid date range: " + dateRange);
            }
        }

        return modRepository.searchMods(
                search, tags, gameVersion, contentType, minRating, minDownloads, pageable,
                currentUsername, sortBy, viewCategory,
                dateCutoff, author
        );
    }

    public List<Mod> getAllMods() { return modRepository.findAll(); }
    public List<Mod> getPublishedMods() { return modRepository.findAllPublished(); }

    @Cacheable("sitemapData")
    public List<Mod> getSitemapData() {
        return modRepository.findAllForSitemap();
    }

    public Mod getModById(String identifier) {
        Optional<Mod> direct = Optional.empty();

        if (identifier != null) {
            direct = modRepository.findById(identifier);
        }

        if (direct.isEmpty() && identifier != null) {
            direct = modRepository.findBySlug(identifier.toLowerCase());
        }

        if (direct.isPresent()) {
            Mod mod = direct.get();
            if (mod.getReviews() != null && !mod.getReviews().isEmpty()) {
                for (Review r : mod.getReviews()) {
                    if (r.getUserAvatarUrl() == null || r.getUserAvatarUrl().isEmpty()) {
                        userRepository.findByUsername(r.getUser())
                                .ifPresent(u -> r.setUserAvatarUrl(u.getAvatarUrl()));
                    }
                }
            }
            return mod;
        }
        return null;
    }

    public Page<Mod> getContributedProjects(String username, Pageable pageable) {
        return modRepository.findByContributors(username, pageable);
    }

    public Page<Mod> getCreatorProjects(String username, Pageable pageable) {
        return modRepository.findByAuthorAndStatus(username, "PUBLISHED", pageable);
    }

    public Page<Mod> getPrivilegedCreatorProjects(String username, Pageable pageable) {
        return modRepository.findByAuthorIgnoreCase(username, pageable);
    }

    public Mod createDraft(String title, String description, String classification, User user, String ownerName, String customSlug) {
        validateClassification(classification);
        if(isTitleTaken(title)) throw new IllegalArgumentException("Title already taken.");

        String finalAuthor = user.getUsername();

        if (ownerName != null && !ownerName.isEmpty() && !ownerName.equalsIgnoreCase(user.getUsername())) {
            Query query = new Query(Criteria.where("username").regex("^" + Pattern.quote(ownerName) + "$", "i"));
            User org = mongoTemplate.findOne(query, User.class);

            if (org == null) throw new IllegalArgumentException("Organization not found");
            if (org.getAccountType() != User.AccountType.ORGANIZATION) throw new IllegalArgumentException("Target is not an organization");

            boolean isMember = org.getOrganizationMembers().stream()
                    .anyMatch(m -> m.getUserId().equals(user.getId()));
            if (!isMember) throw new SecurityException("You do not have permission to create projects for this organization.");

            finalAuthor = org.getUsername();
        }

        if (customSlug != null && !customSlug.isEmpty()) {
            validateSlug(customSlug);
            if (modRepository.existsBySlug(customSlug)) {
                throw new IllegalArgumentException("Project URL '" + customSlug + "' is already taken. Please choose another.");
            }
        }

        Mod mod = new Mod();
        mod.setId(UUID.randomUUID().toString());
        mod.setTitle(sanitizer.sanitizePlainText(title));
        mod.setDescription(sanitizer.sanitizePlainText(description));
        mod.setClassification(classification);
        mod.setAuthor(finalAuthor);

        if (customSlug != null && !customSlug.isEmpty()) {
            mod.setSlug(customSlug.toLowerCase());
        }

        mod.setStatus("DRAFT");
        mod.setExpiresAt(LocalDate.now().plusDays(30).toString());
        mod.setUpdatedAt(LocalDateTime.now().toString());
        mod.setContributors(new ArrayList<>());
        mod.setPendingInvites(new ArrayList<>());
        mod.setVersions(new ArrayList<>());
        mod.setAllowModpacks(true);

        return modRepository.save(mod);
    }

    public Mod createDraft(String title, String description, String classification, User user, String ownerName) {
        return createDraft(title, description, classification, user, ownerName, null);
    }

    @CacheEvict(value = {"projectSearch_v3", "sitemapData"}, allEntries = true)
    public void submitMod(String id, String username) {
        Mod mod = getModById(id);
        User user = userService.getCurrentUser();
        if(mod == null || !hasEditPermission(mod, user)) throw new SecurityException("Permission denied.");

        ensureEditable(mod);
        validateForPublishing(mod);

        mod.setStatus("PENDING");
        mod.setExpiresAt(null);
        mod.setUpdatedAt(LocalDateTime.now().toString());

        modRepository.save(mod);
    }

    public void revertModToDraft(String id, String username) {
        Mod mod = getModById(id);
        User user = userService.getCurrentUser();
        if (mod == null || !hasEditPermission(mod, user)) throw new SecurityException("Permission denied.");

        if (!"PENDING".equals(mod.getStatus())) {
            throw new IllegalArgumentException("Only pending projects can be reverted to draft.");
        }

        mod.setStatus("DRAFT");
        mod.setExpiresAt(LocalDate.now().plusDays(30).toString());
        modRepository.save(mod);
    }

    @CacheEvict(value = {"projectSearch_v3", "sitemapData"}, allEntries = true)
    public void archiveMod(String id, String username) {
        Mod mod = getModById(id);
        User user = userService.getCurrentUser();
        if (mod == null || !isOwner(mod, user)) throw new SecurityException("Permission denied.");

        if (!"PUBLISHED".equals(mod.getStatus()) && !"UNLISTED".equals(mod.getStatus())) {
            throw new IllegalArgumentException("Only published or unlisted projects can be archived.");
        }

        mod.setStatus("ARCHIVED");
        mod.setExpiresAt(null);
        modRepository.save(mod);
    }

    @CacheEvict(value = {"projectSearch_v3", "sitemapData"}, allEntries = true)
    public void unlistMod(String id, String username) {
        Mod mod = getModById(id);
        User user = userService.getCurrentUser();
        if (mod == null || !isOwner(mod, user)) throw new SecurityException("Permission denied.");

        if (!"PUBLISHED".equals(mod.getStatus()) && !"ARCHIVED".equals(mod.getStatus())) {
            throw new IllegalArgumentException("Project must be published or archived to unlist.");
        }

        mod.setStatus("UNLISTED");
        mod.setExpiresAt(null);
        modRepository.save(mod);
    }

    @CacheEvict(value = {"projectSearch_v3", "sitemapData"}, allEntries = true)
    public void publishMod(String id, String username) {
        Mod mod = getModById(id);
        User user = userService.getCurrentUser();

        if (mod == null) throw new IllegalArgumentException("Project not found");

        boolean isAdmin = user != null && user.getRoles() != null && user.getRoles().contains("ADMIN");
        boolean isRestoration = "ARCHIVED".equals(mod.getStatus()) || "UNLISTED".equals(mod.getStatus());

        if (isRestoration) {
            if (!hasEditPermission(mod, user)) throw new SecurityException("Permission denied.");
        } else {
            if (!isAdmin) throw new SecurityException("Only Admins can approve new projects.");
        }

        if (!isRestoration) {
            validateForPublishing(mod);
        }

        boolean isNewRelease = "PENDING".equals(mod.getStatus()) || mod.getCreatedAt() == null;

        mod.setStatus("PUBLISHED");
        mod.setExpiresAt(null);
        mod.setUpdatedAt(LocalDateTime.now().toString());

        if (isNewRelease) {
            mod.setCreatedAt(LocalDateTime.now().toString());
        }

        if (!isRestoration && isAdmin && user != null) {
            mod.setApprovedBy(user.getUsername());
        }

        if (mod.getImageUrl() == null || mod.getImageUrl().isEmpty()) {
            mod.setImageUrl("https://modtale.net/assets/favicon.svg");
        }

        Mod saved = modRepository.save(mod);

        if (isNewRelease) {
            notifyNewProject(saved);
            triggerWebhook(saved);
            User author = userRepository.findByUsername(saved.getAuthor()).orElse(null);
            if(author != null) {
                notificationService.sendNotification(List.of(author.getId()), "Project Approved", saved.getTitle() + " has been approved and is now live!", getProjectLink(saved), saved.getImageUrl());
            }
        }
    }

    private void triggerWebhook(Mod mod) {
        new Thread(() -> {
            try {
                RestTemplate restTemplate = new RestTemplate();
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                Map<String, Object> body = new HashMap<>();
                body.put("apiKey", webhookKey);
                body.put("type", "New");
                body.put("title", mod.getTitle());
                body.put("description", mod.getDescription());
                body.put("iconLink", mod.getImageUrl());
                body.put("modLink", frontendUrl + getProjectLink(mod));
                body.put("developerName", mod.getAuthor());

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
                restTemplate.postForEntity(webhookUrl, request, String.class);
            } catch (Exception e) {
                logger.error("Failed to trigger webhook", e);
            }
        }).start();
    }

    public void rejectMod(String id, String reason) {
        Mod mod = getModById(id);
        User user = userService.getCurrentUser();

        if (user == null || user.getRoles() == null || !user.getRoles().contains("ADMIN")) {
            throw new SecurityException("Only Admins can reject projects.");
        }
        if(mod == null) throw new IllegalArgumentException("Project not found.");

        mod.setStatus("DRAFT");
        mod.setExpiresAt(LocalDate.now().plusDays(30).toString());
        modRepository.save(mod);

        User author = userRepository.findByUsername(mod.getAuthor()).orElse(null);
        if(author != null) {
            notificationService.sendNotification(
                    List.of(author.getId()),
                    "Project Returned",
                    "Your submission '" + mod.getTitle() + "' was returned to drafts. Reason: " + (reason != null ? reason : "Quality Standards"),
                    "/dashboard/projects",
                    mod.getImageUrl()
            );
        }
    }

    private void validateForPublishing(Mod mod) {
        if(mod.getVersions().isEmpty() && !"MODPACK".equals(mod.getClassification())) {
            throw new IllegalArgumentException("You must upload at least one version before submitting.");
        }
        if(mod.getDescription() == null || mod.getDescription().length() < 10) {
            throw new IllegalArgumentException("Short summary must be at least 10 characters.");
        }
        if(mod.getTags() == null || mod.getTags().isEmpty()) {
            throw new IllegalArgumentException("At least one tag is required.");
        }

        if (mod.getSlug() != null) {
            validateSlug(mod.getSlug());
        }

        if("PLUGIN".equals(mod.getClassification())) {
            if(mod.getRepositoryUrl() == null || mod.getRepositoryUrl().isEmpty()) {
                throw new IllegalArgumentException("Repository URL is required for Plugins.");
            }
            validateRepositoryUrl(mod.getRepositoryUrl());
        }

        if (!"MODPACK".equals(mod.getClassification())) {
            if (mod.getLicense() == null || mod.getLicense().isEmpty()) {
                throw new IllegalArgumentException("You must select a license before submitting.");
            }
        } else {
            mod.setLicense(null);
        }
    }

    public List<Mod> getPendingProjects() {
        Query query = new Query(Criteria.where("status").is("PENDING"));
        query.with(Sort.by(Sort.Direction.ASC, "updatedAt"));
        return mongoTemplate.find(query, Mod.class);
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanupExpiredDrafts() {
        String today = LocalDate.now().toString();
        modRepository.deleteByStatusAndExpiresAtBefore("DRAFT", today);
    }

    public void requestTransfer(String modId, String targetUsername, User requester) {
        Mod mod = getModById(modId);
        if (mod == null) throw new IllegalArgumentException("Project not found");
        if (!isOwner(mod, requester)) throw new SecurityException("Only the owner can transfer ownership.");
        ensureEditable(mod);

        User target = userRepository.findByUsername(targetUsername).orElseThrow(() -> new IllegalArgumentException("Target user/org not found"));
        if (target.getUsername().equalsIgnoreCase(mod.getAuthor())) throw new IllegalArgumentException("Project is already owned by this user.");

        mod.setPendingTransferTo(targetUsername);
        modRepository.save(mod);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("modId", mod.getId());
        metadata.put("action", "TRANSFER_REQUEST");

        notificationService.sendActionableNotification(
                List.of(target.getId()),
                "Transfer Request",
                mod.getAuthor() + " wants to transfer '" + mod.getTitle() + "' to you.",
                "/dashboard/projects",
                mod.getImageUrl(),
                "TRANSFER_REQUEST",
                metadata
        );
    }

    public void resolveTransfer(String modId, boolean accept, User responder) {
        Mod mod = getModById(modId);
        if (mod == null) throw new IllegalArgumentException("Project not found");
        if (mod.getPendingTransferTo() == null) throw new IllegalArgumentException("No pending transfer request.");

        ensureEditable(mod);

        if (!responder.getUsername().equalsIgnoreCase(mod.getPendingTransferTo())) {
            User targetUser = userRepository.findByUsername(mod.getPendingTransferTo()).orElse(null);
            if (targetUser != null && targetUser.getAccountType() == User.AccountType.ORGANIZATION) {
                boolean isAdmin = targetUser.getOrganizationMembers().stream()
                        .anyMatch(m -> m.getUserId().equals(responder.getId()) && "ADMIN".equalsIgnoreCase(m.getRole()));
                if (!isAdmin) throw new SecurityException("Insufficient permissions to accept for this organization.");
            } else {
                throw new SecurityException("Unauthorized to resolve this transfer.");
            }
        }

        if (accept) {
            String oldAuthor = mod.getAuthor();
            mod.setAuthor(mod.getPendingTransferTo());
            mod.setPendingTransferTo(null);
            mod.getContributors().remove(mod.getAuthor());
            modRepository.save(mod);

            User oldOwner = userRepository.findByUsername(oldAuthor).orElse(null);
            if(oldOwner != null) {
                notificationService.sendNotification(List.of(oldOwner.getId()), "Transfer Accepted", mod.getTitle() + " has been transferred to " + mod.getAuthor(), "/projects/" + mod.getId(), mod.getImageUrl());
            }
        } else {
            mod.setPendingTransferTo(null);
            modRepository.save(mod);
            User oldOwner = userRepository.findByUsername(mod.getAuthor()).orElse(null);
            if(oldOwner != null) {
                notificationService.sendNotification(List.of(oldOwner.getId()), "Transfer Declined", "Transfer request for " + mod.getTitle() + " was declined.", "/dashboard/projects", mod.getImageUrl());
            }
        }
    }

    @CacheEvict(value = {"projectSearch_v3", "sitemapData"}, allEntries = true)
    public void addMod(Mod mod) {
        validateTags(mod.getTags());
        validateClassification(mod.getClassification());
        mod.setStatus("PUBLISHED");
        modRepository.save(mod);
    }

    @CacheEvict(value = {"projectSearch_v3", "sitemapData"}, allEntries = true)
    public void updateMod(String id, Mod updatedMod) {
        User user = userService.getCurrentUser();
        Mod existing = getModById(id);
        if (existing == null || !hasEditPermission(existing, user)) {
            throw new SecurityException("You do not have permission to edit this project.");
        }
        ensureEditable(existing);

        if (updatedMod.getTags() != null) {
            existing.setTags(updatedMod.getTags());
        }

        if (updatedMod.getRepositoryUrl() != null && !updatedMod.getRepositoryUrl().isEmpty()) {
            validateRepositoryUrl(updatedMod.getRepositoryUrl());
        }

        existing.setTitle(sanitizer.sanitizePlainText(updatedMod.getTitle()));
        if (updatedMod.getAbout() != null) existing.setAbout(sanitizer.sanitize(updatedMod.getAbout()));
        existing.setDescription(sanitizer.sanitize(updatedMod.getDescription()));
        existing.setCategory(updatedMod.getCategory());
        existing.setCategories(updatedMod.getCategories());

        if (updatedMod.getSlug() != null) {
            String newSlug = updatedMod.getSlug().toLowerCase();

            if (newSlug.isEmpty()) {
                existing.setSlug(null);
            } else if (!newSlug.equals(existing.getSlug())) {
                validateSlug(newSlug);

                Optional<Mod> conflict = modRepository.findBySlug(newSlug);
                if (conflict.isPresent() && !conflict.get().getId().equals(existing.getId())) {
                    throw new IllegalArgumentException("URL Slug '" + newSlug + "' is already taken.");
                }

                existing.setSlug(newSlug);
            }
        }

        if ("MODPACK".equals(existing.getClassification())) {
            existing.setLicense(null);
        } else {
            existing.setLicense(updatedMod.getLicense());
        }

        existing.setRepositoryUrl(updatedMod.getRepositoryUrl());
        existing.setTypes(updatedMod.getTypes());
        existing.setAllowModpacks(updatedMod.isAllowModpacks());

        if (updatedMod.getLinks() != null) existing.setLinks(updatedMod.getLinks());
        if (updatedMod.getImageUrl() != null) existing.setImageUrl(updatedMod.getImageUrl());

        existing.setUpdatedAt(LocalDateTime.now().toString());
        modRepository.save(existing);
    }

    @CacheEvict(value = {"projectSearch_v3", "sitemapData"}, allEntries = true)
    public void updateProjectIcon(String id, MultipartFile file) throws IOException {
        User user = userService.getCurrentUser();
        Mod mod = getModById(id);
        if (mod == null || !hasEditPermission(mod, user)) throw new SecurityException("Permission denied.");
        ensureEditable(mod);

        if (mod.getImageUrl() != null && !mod.getImageUrl().contains("default.png") && !mod.getImageUrl().contains("placeholder") && !mod.getImageUrl().contains("favicon")) {
            try { storageService.deleteFile(mod.getImageUrl()); } catch (Exception ignore) {}
        }
        String path = storageService.upload(file, "images");
        mod.setImageUrl(storageService.getPublicUrl(path));
        mod.setUpdatedAt(LocalDateTime.now().toString());
        modRepository.save(mod);
    }

    @CacheEvict(value = "projectSearch_v3", allEntries = true)
    public void updateProjectBanner(String id, MultipartFile file) throws IOException {
        User user = userService.getCurrentUser();
        Mod mod = getModById(id);
        if (mod == null || !hasEditPermission(mod, user)) throw new SecurityException("Permission denied.");
        ensureEditable(mod);

        if (mod.getBannerUrl() != null && !mod.getBannerUrl().isEmpty()) {
            try { storageService.deleteFile(mod.getBannerUrl()); } catch (Exception ignore) {}
        }

        String path = storageService.upload(file, "images");
        mod.setBannerUrl(storageService.getPublicUrl(path));
        mod.setUpdatedAt(LocalDateTime.now().toString());
        modRepository.save(mod);
    }

    private void validateDependency(Mod parentMod, Mod depMod) {
        if (parentMod.getId().equals(depMod.getId())) throw new IllegalArgumentException("A project cannot depend on itself.");
        if ("SAVE".equals(parentMod.getClassification())) throw new IllegalArgumentException("Worlds cannot have dependencies.");
        if ("MODPACK".equals(depMod.getClassification()) || "SAVE".equals(depMod.getClassification())) throw new IllegalArgumentException("Modpacks and Worlds cannot be added as dependencies.");
    }

    @CacheEvict(value = "projectSearch_v3", allEntries = true)
    public void updateVersionDependencies(String modId, String versionId, List<String> modIds) {
        User user = userService.getCurrentUser();
        Mod mod = getModById(modId);
        if (mod == null) throw new IllegalArgumentException("Project not found");
        if (!hasEditPermission(mod, user)) throw new SecurityException("No permission.");
        ensureEditable(mod);

        ModVersion version = mod.getVersions().stream().filter(v -> v.getId().equals(versionId)).findFirst().orElse(null);
        if (version == null) throw new IllegalArgumentException("Version not found");
        List<ModDependency> newDeps = new ArrayList<>();
        List<String> simpleModIds = new ArrayList<>();

        boolean isModpack = "MODPACK".equals(mod.getClassification());

        if (modIds != null) {
            for (String entry : modIds) {
                String[] parts = entry.split(":");
                if (parts.length < 2) throw new IllegalArgumentException("Invalid format: " + entry);
                String depId = parts[0].trim();
                String depVer = parts[1].trim();

                boolean isOptional = !isModpack && parts.length >= 3 && "optional".equalsIgnoreCase(parts[2].trim());

                Mod depMod = getModById(depId);
                if (depMod == null) throw new IllegalArgumentException("Dependency not found: " + depId);
                validateDependency(mod, depMod);
                boolean exists = depMod.getVersions().stream().anyMatch(v -> v.getVersionNumber().equalsIgnoreCase(depVer));
                if (!exists) throw new IllegalArgumentException("Version " + depVer + " not found on project " + depId);
                newDeps.add(new ModDependency(depMod.getId(), depMod.getTitle(), depVer, isOptional));
                simpleModIds.add(depId);
            }
        }

        if (isModpack && newDeps.size() < 2) {
            throw new IllegalArgumentException("Modpacks must have at least two valid dependencies.");
        }

        version.setDependencies(newDeps);
        if (isModpack && mod.getVersions().get(0).getId().equals(versionId)) {
            mod.setModIds(simpleModIds);
        }
        mod.setUpdatedAt(LocalDateTime.now().toString());
        modRepository.save(mod);
    }

    public void addVersionToMod(String modId, String versionNumber, List<String> gameVersions,
                                MultipartFile file, String changelog, List<String> modIds) throws IOException {
        addVersionToMod(modId, versionNumber, gameVersions, file, changelog, modIds, ModVersion.Channel.RELEASE);
    }

    @CacheEvict(value = "projectSearch_v3", allEntries = true)
    public void addVersionToMod(String modId, String versionNumber, List<String> gameVersions,
                                MultipartFile file, String changelog, List<String> modIds, ModVersion.Channel channel) throws IOException {
        User user = userService.getCurrentUser();
        Mod mod = getModById(modId);
        if (mod == null) throw new IllegalArgumentException("Project not found");
        if (!hasEditPermission(mod, user)) throw new SecurityException("You do not have permission to update this project.");
        ensureEditable(mod);

        if ("DRAFT".equals(mod.getStatus()) && !mod.getVersions().isEmpty()) {
            throw new IllegalArgumentException("Drafts are limited to one version. Please delete the existing version or publish the project.");
        }

        validateVersionNumber(versionNumber);

        if (gameVersions != null) {
            for (String gv : gameVersions) {
                if (!ALLOWED_GAME_VERSIONS.contains(gv)) {
                    throw new IllegalArgumentException("Invalid game version: " + gv);
                }
            }
        }

        boolean isModpack = "MODPACK".equals(mod.getClassification());

        if (!isModpack) {
            validationService.validateProjectFile(file, mod.getClassification());
        }

        boolean versionExists = mod.getVersions().stream().anyMatch(v -> v.getVersionNumber().equalsIgnoreCase(versionNumber));
        if (versionExists) throw new IllegalArgumentException("Version " + versionNumber + " already exists.");

        String filePath = null;
        if (file != null) filePath = storageService.upload(file, "files/" + mod.getClassification().toLowerCase());

        ModVersion ver = new ModVersion();
        ver.setId(UUID.randomUUID().toString());
        ver.setVersionNumber(versionNumber);
        ver.setGameVersions(gameVersions);
        ver.setFileUrl(filePath);
        ver.setReleaseDate(LocalDate.now().toString());
        ver.setDownloadCount(0);
        ver.setChangelog(sanitizer.sanitizePlainText(changelog));
        ver.setChannel(channel);

        ver.setDependencies(new ArrayList<>());
        List<String> simpleModIds = new ArrayList<>();

        if (modIds != null) {
            for (String entry : modIds) {
                String[] parts = entry.split(":");
                if (parts.length < 2) throw new IllegalArgumentException("Invalid dependency format.");
                String depId = parts[0].trim();
                String depVer = parts[1].trim();

                boolean isOptional = !isModpack && parts.length >= 3 && "optional".equalsIgnoreCase(parts[2].trim());

                Mod depMod = getModById(depId);

                if (depMod == null || "DRAFT".equals(depMod.getStatus())) throw new IllegalArgumentException("Dependency not found or not published: " + depId);
                validateDependency(mod, depMod);

                if (depMod.getVersions().stream().noneMatch(v -> v.getVersionNumber().equalsIgnoreCase(depVer))) {
                    throw new IllegalArgumentException("Version " + depVer + " invalid for dependency " + depId);
                }
                simpleModIds.add(depId);
                ver.getDependencies().add(new ModDependency(depMod.getId(), depMod.getTitle(), depVer, isOptional));
            }
        }

        if (isModpack) {
            if (ver.getDependencies().size() < 2) {
                throw new IllegalArgumentException("Modpack must have at least two valid dependencies.");
            }
            mod.setModIds(simpleModIds);
        }

        if (file != null && !isModpack) {
            try {
                byte[] fileBytes = storageService.download(filePath);
                ScanResult scanResult = wardenService.scanFile(fileBytes, file.getOriginalFilename());
                ver.setScanResult(scanResult);

                if ("INFECTED".equals(scanResult.getStatus())) {
                    logger.warn("Warden detected malware in project {} version {}", mod.getTitle(), versionNumber);
                }
            } catch (Exception e) {
                logger.error("Warden scan failed", e);
                ScanResult failed = new ScanResult();
                failed.setStatus("FAILED");
                ver.setScanResult(failed);
            }
        }

        mod.getVersions().add(0, ver);
        mod.setUpdatedAt(LocalDateTime.now().toString());
        modRepository.save(mod);

        if("PUBLISHED".equals(mod.getStatus())) {
            notifyUpdates(mod, versionNumber);
            notifyDependents(mod, versionNumber);
        }
    }

    public void deleteVersion(String modId, String versionId, String username) {
        User user = userService.getCurrentUser();
        Mod mod = getModById(modId);
        if (mod == null) throw new IllegalArgumentException("Project not found");
        ensureEditable(mod);

        if (!isOwner(mod, user)) {
            throw new SecurityException("Only the owner can delete versions.");
        }

        if (!"DRAFT".equals(mod.getStatus()) && mod.getVersions().size() <= 1) {
            throw new IllegalArgumentException("Cannot delete the only version of a published project. Unlist or delete the project instead.");
        }

        boolean removed = mod.getVersions().removeIf(v -> {
            if (v.getId().equals(versionId)) {
                if (v.getFileUrl() != null) storageService.deleteFile(v.getFileUrl());
                return true;
            }
            return false;
        });

        if (removed) {
            modRepository.save(mod);
        } else {
            throw new IllegalArgumentException("Version not found.");
        }
    }

    @CacheEvict(value = {"projectSearch_v3", "sitemapData"}, allEntries = true)
    public void deleteMod(String id, String username) {
        Mod mod = getModById(id);
        User user = userService.getCurrentUser();

        if (mod == null || !isOwner(mod, user)) {
            throw new SecurityException("Permission denied or Project not found.");
        }

        ensureEditable(mod);
        performDeletionStrategy(mod);
    }

    @CacheEvict(value = {"projectSearch_v3", "sitemapData"}, allEntries = true)
    public void adminDeleteProject(String id) {
        User user = userService.getCurrentUser();
        if (!isAdmin(user)) throw new SecurityException("Access Denied");
        Mod mod = getModById(id);
        if (mod == null) throw new IllegalArgumentException("Project not found");
        performDeletionStrategy(mod);
    }

    @CacheEvict(value = {"projectSearch_v3", "sitemapData"}, allEntries = true)
    public void adminUnlistProject(String id) {
        User user = userService.getCurrentUser();
        if (!isAdmin(user)) throw new SecurityException("Access Denied");
        Mod mod = getModById(id);
        if (mod == null) throw new IllegalArgumentException("Project not found");
        mod.setStatus("UNLISTED");
        mod.setExpiresAt(null);
        modRepository.save(mod);
    }

    @CacheEvict(value = {"projectSearch_v3", "sitemapData"}, allEntries = true)
    public void adminDeleteVersion(String modId, String versionId) {
        User user = userService.getCurrentUser();
        if (!isAdmin(user)) throw new SecurityException("Access Denied");
        Mod mod = getModById(modId);
        if (mod == null) throw new IllegalArgumentException("Project not found");

        boolean removed = mod.getVersions().removeIf(v -> {
            if (v.getId().equals(versionId)) {
                if (v.getFileUrl() != null) storageService.deleteFile(v.getFileUrl());
                return true;
            }
            return false;
        });

        if (removed) {
            modRepository.save(mod);
        } else {
            throw new IllegalArgumentException("Version not found.");
        }
    }

    public void handleUserDeletion(User user) {
        List<Mod> userMods = modRepository.findByAuthor(user.getUsername());
        for (Mod mod : userMods) {
            performDeletionStrategy(mod);
        }
        Objects.requireNonNull(cacheManager.getCache("projectSearch_v3")).clear();
        Objects.requireNonNull(cacheManager.getCache("sitemapData")).clear();
    }

    private void performDeletionStrategy(Mod mod) {
        List<Mod> dependents = modRepository.findByDependency(mod.getId());

        if (!dependents.isEmpty()) {
            logger.info("Soft deleting project " + mod.getId() + " because it is a dependency.");
            mod.setStatus("DELETED");
            mod.setTitle("Deleted Project");
            mod.setDescription("This project has been deleted.");
            mod.setAbout("This project was deleted by the author but is retained for dependency resolution.");

            if (mod.getImageUrl() != null) storageService.deleteFile(mod.getImageUrl());
            if (mod.getBannerUrl() != null) storageService.deleteFile(mod.getBannerUrl());
            mod.setImageUrl(null);
            mod.setBannerUrl(null);
            mod.setSlug(null);

            if (mod.getGalleryImages() != null) {
                for (String img : mod.getGalleryImages()) {
                    storageService.deleteFile(img);
                }
                mod.getGalleryImages().clear();
            }

            mod.setContributors(new ArrayList<>());
            mod.setPendingInvites(new ArrayList<>());
            mod.setReviews(new ArrayList<>());
            mod.setTags(new ArrayList<>());
            modRepository.save(mod);
        } else {
            logger.info("Hard deleting project " + mod.getId());
            Set<String> dependencyIds = new HashSet<>();
            if (mod.getVersions() != null) {
                for (ModVersion v : mod.getVersions()) {
                    if (v.getDependencies() != null) {
                        for (ModDependency dep : v.getDependencies()) {
                            dependencyIds.add(dep.getModId());
                        }
                    }
                }
            }
            if (mod.getModIds() != null) {
                dependencyIds.addAll(mod.getModIds());
            }

            if (mod.getVersions() != null) {
                for (ModVersion version : mod.getVersions()) {
                    if(version.getFileUrl() != null) storageService.deleteFile(version.getFileUrl());
                }
            }
            storageService.deleteFile(mod.getImageUrl());
            if (mod.getBannerUrl() != null) storageService.deleteFile(mod.getBannerUrl());

            if (mod.getGalleryImages() != null) {
                for (String img : mod.getGalleryImages()) {
                    storageService.deleteFile(img);
                }
            }

            mongoTemplate.updateMulti(new Query(Criteria.where("likedModIds").is(mod.getId())), new Update().pull("likedModIds", mod.getId()), User.class);
            modRepository.delete(mod);

            for (String depId : dependencyIds) {
                cleanupOrphanedDependency(depId);
            }
        }
    }

    private void cleanupOrphanedDependency(String modId) {
        Mod mod = getModById(modId);
        if (mod != null && "DELETED".equals(mod.getStatus())) {
            List<Mod> remainingDependents = modRepository.findByDependency(modId);

            if (remainingDependents.isEmpty()) {
                logger.info("Project " + modId + " is no longer a dependency for anyone. Cleaning up orphan.");
                performDeletionStrategy(mod);
            }
        }
    }

    public byte[] generateModpackZip(Mod pack, ModVersion version) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry readme = new ZipEntry("modpack.json");
            zos.putNextEntry(readme);
            StringBuilder json = new StringBuilder("{\n  \"name\": \"" + pack.getTitle() + "\",\n  \"files\": [\n");

            for(int i=0; i<version.getDependencies().size(); i++) {
                ModDependency dep = version.getDependencies().get(i);
                json.append("    { \"id\": \"").append(dep.getModId())
                        .append("\", \"version\": \"").append(dep.getVersionNumber()).append("\" }");
                if(i < version.getDependencies().size() - 1) json.append(",");
                json.append("\n");
            }
            json.append("  ]\n}");
            zos.write(json.toString().getBytes());
            zos.closeEntry();

            for (ModDependency dep : version.getDependencies()) {
                Mod depMod = getModById(dep.getModId());
                if (depMod == null) continue;

                ModVersion depVer = depMod.getVersions().stream()
                        .filter(v -> v.getVersionNumber().equals(dep.getVersionNumber()))
                        .findFirst()
                        .orElse(null);

                if (depVer != null && depVer.getFileUrl() != null) {
                    try {
                        byte[] fileData = storageService.download(depVer.getFileUrl());

                        String folder = "PLUGIN".equals(depMod.getClassification()) ? "plugins/" : "asset-packs/";
                        String originalFilename = depVer.getFileUrl().substring(depVer.getFileUrl().lastIndexOf('/') + 1);
                        if (originalFilename.length() > 37 && originalFilename.charAt(36) == '-') {
                            originalFilename = originalFilename.substring(37);
                        }

                        ZipEntry entry = new ZipEntry(folder + originalFilename);
                        zos.putNextEntry(entry);
                        zos.write(fileData);
                        zos.closeEntry();
                    } catch (Exception e) {
                        logger.error("Failed to include dependency in modpack: " + dep.getModId(), e);
                    }
                }
            }
        }
        return baos.toByteArray();
    }

    public ModVersion findVersion(Mod pack, String versionNumber) {
        if ("latest".equalsIgnoreCase(versionNumber)) {
            return pack.getVersions().isEmpty() ? null : pack.getVersions().get(0);
        }
        return pack.getVersions().stream()
                .filter(v -> v.getVersionNumber().equalsIgnoreCase(versionNumber))
                .findFirst()
                .orElse(null);
    }

    public void incrementDownloadCount(String modId) {
        Mod mod = getModById(modId);
        if (mod != null) {
            mod.setDownloadCount(mod.getDownloadCount() + 1);
            modRepository.save(mod);

            int count = mod.getDownloadCount();
            if (count == 10000 || count == 100000 || count == 1000000 || count == 10000000) {
                String title = "Download Milestone Reached!";
                String msg = mod.getTitle() + " has hit " + String.format("%,d", count) + " downloads!";
                String link = "/dashboard";
                User author = userRepository.findByUsername(mod.getAuthor()).orElse(null);
                if (author != null) {
                    notificationService.sendNotification(List.of(author.getId()), title, msg, link, mod.getImageUrl());
                }
            }
        }
    }

    private void notifyUpdates(Mod mod, String versionNumber) {
        new Thread(() -> {
            try {
                List<User> fans = userRepository.findByLikedModIdsContaining(mod.getId());
                String msg = "Version " + versionNumber + " is now available.";

                List<String> usersToNotify = fans.stream()
                        .filter(u -> u.getNotificationPreferences().getProjectUpdates() == User.NotificationLevel.ON)
                        .map(User::getId).toList();

                if (!usersToNotify.isEmpty()) {
                    notificationService.sendNotification(usersToNotify, "Update: " + mod.getTitle(), msg, getProjectLink(mod), mod.getImageUrl());
                }
            } catch (Exception e) { logger.error("Failed to send notifications", e); }
        }).start();
    }

    private void notifyNewProject(Mod mod) {
        new Thread(() -> {
            try {
                User author = userRepository.findByUsername(mod.getAuthor()).orElse(null);
                if (author == null) return;
                List<User> followers = userRepository.findByFollowingIdsContaining(author.getId());
                String title = "New Project from " + mod.getAuthor();
                String msg = mod.getTitle() + " has been released.";

                List<String> usersToNotify = followers.stream()
                        .filter(u -> u.getNotificationPreferences().getCreatorUploads() == User.NotificationLevel.ON)
                        .map(User::getId).toList();

                if (!usersToNotify.isEmpty()) {
                    notificationService.sendNotification(usersToNotify, title, msg, getProjectLink(mod), mod.getImageUrl());
                }
            } catch (Exception e) { logger.error("Failed to send new project notifications", e); }
        }).start();
    }

    private void notifyDependents(Mod updatedMod, String version) {
        new Thread(() -> {
            List<Mod> dependents = modRepository.findByDependency(updatedMod.getId());
            for (Mod dependent : dependents) {
                User author = userRepository.findByUsername(dependent.getAuthor()).orElse(null);
                if (author != null && author.getNotificationPreferences().getDependencyUpdates() != User.NotificationLevel.OFF) {
                    String title = "Dependency Update";
                    String msg = updatedMod.getTitle() + " (used in " + dependent.getTitle() + ") has been updated to version " + version + ".";
                    notificationService.sendNotification(List.of(author.getId()), title, msg, getProjectLink(updatedMod), updatedMod.getImageUrl());
                }
            }
        }).start();
    }

    public void checkTrendingNotifications() {
        String[] algos = {"trending", "popular", "gems", "relevance"};
        for (String algo : algos) {
            Page<Mod> topMods = getMods(null, null, 0, 12, algo, null, null, null, null, algo, null, null);
            for (Mod mod : topMods.getContent()) {
                LocalDateTime lastNotified = mod.getLastTrendingNotification() != null
                        ? LocalDateTime.parse(mod.getLastTrendingNotification()) : null;

                if (lastNotified == null || lastNotified.isBefore(LocalDateTime.now().minusWeeks(1))) {
                    User author = userRepository.findByUsername(mod.getAuthor()).orElse(null);
                    if (author != null) {
                        String friendlyName = switch (algo) {
                            case "gems" -> "Hidden Gems";
                            case "popular" -> "Popular";
                            case "trending" -> "Trending";
                            case "relevance" -> "Recommended";
                            default -> algo;
                        };
                        String title = "Your Project is Trending!";
                        String msg = mod.getTitle() + " has hit " + friendlyName + "!";
                        String link = "/dashboard/analytics";
                        notificationService.sendNotification(List.of(author.getId()), title, msg, link, mod.getImageUrl());
                        mod.setLastTrendingNotification(LocalDateTime.now().toString());
                        modRepository.save(mod);
                    }
                }
            }
        }
    }

    public void inviteContributor(String modId, String usernameToInvite) {
        User currentUser = userService.getCurrentUser();
        Mod mod = getModById(modId);
        if (mod == null) throw new IllegalArgumentException("Project not found");
        if (!isOwner(mod, currentUser)) throw new SecurityException("Only the owner can manage contributors.");
        ensureEditable(mod);

        User invitee = userRepository.findByUsername(usernameToInvite).orElseThrow(() -> new IllegalArgumentException("User not found"));
        mod.getPendingInvites().add(usernameToInvite);
        modRepository.save(mod);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("modId", mod.getId());
        metadata.put("action", "CONTRIBUTOR_INVITE");
        notificationService.sendActionableNotification(List.of(invitee.getId()), "Contributor Invite", "You have been invited to contribute to " + mod.getTitle() + ".", "/dashboard/projects", mod.getImageUrl(), "CONTRIBUTOR_INVITE", metadata);
    }

    public void removeContributor(String modId, String usernameToRemove) {
        User currentUser = userService.getCurrentUser();
        Mod mod = getModById(modId);
        if (mod != null && isOwner(mod, currentUser)) {
            ensureEditable(mod);
            mod.getContributors().remove(usernameToRemove);
            modRepository.save(mod);
        }
    }

    public void acceptInvite(String modId) {
        User currentUser = userService.getCurrentUser();
        Mod mod = getModById(modId);
        if (mod != null && mod.getPendingInvites().remove(currentUser.getUsername())) {
            mod.getContributors().add(currentUser.getUsername());
            modRepository.save(mod);
            User owner = userRepository.findByUsername(mod.getAuthor()).orElse(null);
            if (owner != null) {
                notificationService.sendNotification(
                        List.of(owner.getId()),
                        "Invite Accepted",
                        currentUser.getUsername() + " joined the team for " + mod.getTitle(),
                        getProjectLink(mod) + "/contributors",
                        currentUser.getAvatarUrl()
                );
            }
        }
    }

    public void declineInvite(String modId) {
        User currentUser = userService.getCurrentUser();
        Mod mod = getModById(modId);
        if (mod != null && mod.getPendingInvites().remove(currentUser.getUsername())) {
            modRepository.save(mod);
        }
    }

    public void addReview(String modId, String username, String comment, int rating, String version) {
        Mod mod = getModById(modId);
        if (mod != null) {
            Review review = new Review();
            review.setId(UUID.randomUUID().toString());
            review.setUser(username);

            userRepository.findByUsername(username).ifPresent(u -> review.setUserAvatarUrl(u.getAvatarUrl()));

            review.setComment(sanitizer.sanitizePlainText(comment));
            review.setRating(rating);
            review.setDate(LocalDate.now().toString());
            if (mod.getReviews() == null) mod.setReviews(new ArrayList<>());
            mod.getReviews().add(0, review);
            double avg = mod.getReviews().stream().mapToInt(Review::getRating).average().orElse(0.0);
            mod.setRating(Math.round(avg * 10.0) / 10.0);
            modRepository.save(mod);
            User author = userRepository.findByUsername(mod.getAuthor()).orElse(null);
            if (author != null && author.getNotificationPreferences().getNewReviews() != User.NotificationLevel.OFF) {
                notificationService.sendNotification(
                        List.of(author.getId()),
                        "New Review: " + rating + "/5",
                        username + " reviewed " + mod.getTitle(),
                        getProjectLink(mod),
                        mod.getImageUrl()
                );
            }
        }
    }

    public void toggleFavorite(String modId, String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        Mod mod = getModById(modId);
        if (user != null && mod != null) {
            List<String> likes = user.getLikedModIds();
            if (likes == null) { likes = new ArrayList<>(); user.setLikedModIds(likes); }
            if (likes.contains(modId)) {
                likes.remove(modId);
                mod.setFavoriteCount(Math.max(0, mod.getFavoriteCount() - 1));
            } else {
                likes.add(modId);
                mod.setFavoriteCount(mod.getFavoriteCount() + 1);
            }
            userRepository.save(user);
            modRepository.save(mod);
        }
    }

    public void addGalleryImage(String id, String imageUrl) {
        Mod mod = getModById(id);
        if (mod != null) {
            ensureEditable(mod);
            mod.getGalleryImages().add(imageUrl);
            modRepository.save(mod);
        }
    }

    public void removeGalleryImage(String modId, String imageUrl, String username) {
        User user = userService.getCurrentUser();
        Mod mod = getModById(modId);
        if (mod != null && hasEditPermission(mod, user)) {
            ensureEditable(mod);
            mod.getGalleryImages().remove(imageUrl);
            storageService.deleteFile(imageUrl);
            modRepository.save(mod);
        }
    }

    public List<String> getGalleryImages(String id) {
        Mod mod = getModById(id);
        return mod != null ? mod.getGalleryImages() : List.of();
    }

    public List<String> getProjectTags(String id) {
        Mod mod = getModById(id);
        return mod != null ? mod.getTags() : List.of();
    }

    @Cacheable("allTags")
    public List<String> getAllTags() {
        return getAllowedTags();
    }

    @Cacheable("hytaleVersions")
    public List<String> getAllHytaleVersions() {
        return getAllowedGameVersions();
    }

    public List<User> searchCreators(String query) {
        return userRepository.findAll().stream().filter(u -> u.getUsername().toLowerCase().contains(query.toLowerCase())).limit(10).collect(Collectors.toList());
    }

    public boolean verifyFileExistsInDb(String fileUrl) {
        Optional<Mod> modOpt = modRepository.findByVersionsFileUrl(fileUrl);
        if (modOpt.isPresent()) return true;
        if (fileUrl.contains("/")) {
            String filename = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            Query suffixQuery = new Query(Criteria.where("versions.fileUrl").regex(filename + "$"));
            return mongoTemplate.exists(suffixQuery, Mod.class);
        }
        return false;
    }

    public void incrementDownloadCountByFileUrl(String fileUrl) {
        Optional<Mod> modOpt = modRepository.findByVersionsFileUrl(fileUrl);
        if (modOpt.isEmpty() && fileUrl.contains("/")) {
            String filename = fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            Query query = new Query(Criteria.where("versions.fileUrl").regex(filename + "$"));
            Mod found = mongoTemplate.findOne(query, Mod.class);
            modOpt = Optional.ofNullable(found);
        }
        if (modOpt.isPresent()) {
            Mod mod = modOpt.get();
            mod.setDownloadCount(mod.getDownloadCount() + 1);
            modRepository.save(mod);
            analyticsService.logDownload(mod.getId(), null, mod.getAuthor(), false);
        }
    }

    private String getLinkSlug(Mod mod) {
        if (mod.getSlug() != null && !mod.getSlug().isEmpty()) {
            return mod.getSlug();
        }
        return mod.getId();
    }

    private String getProjectLink(Mod mod) {
        String slug = getLinkSlug(mod);
        if ("MODPACK".equals(mod.getClassification())) {
            return "/modpack/" + slug;
        } else if ("SAVE".equals(mod.getClassification())) {
            return "/world/" + slug;
        } else {
            return "/mod/" + slug;
        }
    }
}