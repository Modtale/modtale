package net.modtale.service.resources;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Refill;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
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
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.FileCopyUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
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

    private static final Map<String, String> CANONICAL_TAG_MAP = ALLOWED_TAGS.stream()
            .collect(Collectors.toMap(String::toLowerCase, Function.identity()));

    private static final Set<String> ALLOWED_GAME_VERSIONS = Set.of(
            "2026.01.13-dcad8778f", "2026.01.17-4b0f30090", "2026.01.24-6e2d4fc36", "2026.01.28-87d03be09", "2026.02.06-aa1b071c2", "2026.02.17-255364b8e", "2026.02.19-1a311a592"
    );

    private static final Set<String> ALLOWED_CLASSIFICATIONS = Set.of(
            "PLUGIN", "DATA", "ART", "SAVE", "MODPACK"
    );

    private static final Pattern STRICT_VERSION_PATTERN = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");
    private static final Pattern REPO_URL_PATTERN = Pattern.compile("^https:\\/\\/(github\\.com|gitlab\\.com|codeberg\\.org)\\/[\\w.-]+\\/[\\w.-]+$");
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
    @Qualifier("taskExecutor")
    @Autowired private Executor taskExecutor;

    @Lazy
    @Autowired
    private ModService self;

    @Value("${app.webhook.url}")
    private String webhookUrl;

    @Value("${app.webhook.key}")
    private String webhookKey;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.limits.max-projects-per-user:50}")
    private int maxProjectsPerUser;

    @Value("${app.limits.max-versions-per-day:5}")
    private int maxVersionsPerDay;

    @Value("${app.limits.max-versions-per-month:30}")
    private int maxVersionsPerMonth;

    @Value("${app.limits.max-gallery-images-per-project:20}")
    private int maxGalleryImagesPerProject;

    @Value("${app.limits.modpack-gen-per-hour:10}")
    private int modpackGenLimitPerHour;

    @Value("${app.limits.rescans-per-day:5}")
    private int rescanLimitPerDay;

    private final Map<String, Bucket> modpackGenBuckets = new ConcurrentHashMap<>();
    private final Map<String, Bucket> rescanBuckets = new ConcurrentHashMap<>();

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

    private void evictProjectDetails(Mod mod) {
        if (mod == null) return;
        Cache cache = cacheManager.getCache("projectDetails");
        if (cache != null) {
            if (mod.getId() != null) cache.evict(mod.getId());
            if (mod.getSlug() != null) cache.evict(mod.getSlug());
        }
    }

    private User getAuthorUser(Mod mod) {
        if (mod.getAuthorId() != null) {
            return userRepository.findById(mod.getAuthorId()).orElse(null);
        }
        if (mod.getAuthor() != null) {
            return userRepository.findByUsernameIgnoreCase(mod.getAuthor()).orElse(null);
        }
        return null;
    }

    public List<String> validateTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            throw new IllegalArgumentException("At least one tag is required.");
        }

        List<String> normalized = new ArrayList<>();
        List<String> invalidTags = new ArrayList<>();

        for (String tag : tags) {
            String canonical = CANONICAL_TAG_MAP.get(tag.toLowerCase());
            if (canonical != null) {
                normalized.add(canonical);
            } else {
                invalidTags.add(tag);
            }
        }

        if (!invalidTags.isEmpty()) {
            throw new IllegalArgumentException("Invalid tags detected: " + String.join(", ", invalidTags));
        }

        return normalized;
    }

    public void validateVersionNumber(String version) {
        if (version == null || !STRICT_VERSION_PATTERN.matcher(version).matches()) {
            throw new IllegalArgumentException("Version number must follow SemVer format (e.g., 1.0.0, 1.0.0-rc.1, 1.0.0+build).");
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

        if (mod.getAuthorId() != null && mod.getAuthorId().equals(user.getId())) return true;
        if (mod.getAuthor() != null && mod.getAuthor().equalsIgnoreCase(user.getUsername())) return true;

        User authorUser = getAuthorUser(mod);

        if (authorUser != null && authorUser.getAccountType() == User.AccountType.ORGANIZATION) {
            boolean isOrgMember = authorUser.getOrganizationMembers().stream()
                    .anyMatch(m -> m.getUserId().equals(user.getId()));

            if (isOrgMember) return true;
        }

        return (mod.getContributors() != null && mod.getContributors().stream().anyMatch(c -> c.equalsIgnoreCase(user.getUsername()) || c.equals(user.getId())));
    }

    public boolean isOwner(Mod mod, User user) {
        if (mod == null || user == null) return false;

        if (mod.getAuthorId() != null && mod.getAuthorId().equals(user.getId())) return true;
        if (mod.getAuthor() != null && mod.getAuthor().equalsIgnoreCase(user.getUsername())) return true;

        User authorUser = getAuthorUser(mod);

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
            String gameVersion, String contentType, Double minRating, Integer minDownloads, Integer minFavorites, String viewCategory,
            String dateRange, String author
    ) {
        List<String> normalizedTags = null;
        if (tags != null && !tags.isEmpty()) {
            normalizedTags = tags.stream()
                    .map(t -> CANONICAL_TAG_MAP.getOrDefault(t.toLowerCase(), t))
                    .collect(Collectors.toList());
        }

        return self.getModsCached(normalizedTags, search, page, size, sortBy, gameVersion, contentType, minRating, minDownloads, minFavorites, viewCategory, dateRange, author);
    }

    @Cacheable(
            value = "projectSearch",
            key = "{#tags, #search, #page, #size, #sortBy, #gameVersion, #contentType, #minRating, #minDownloads, #minFavorites, #viewCategory, #dateRange, #author}",
            condition = "!('Favorites'.equals(#viewCategory))"
    )
    public Page<Mod> getModsCached(
            List<String> tags, String search, int page, int size, String sortBy,
            String gameVersion, String contentType, Double minRating, Integer minDownloads, Integer minFavorites, String viewCategory,
            String dateRange, String author
    ) {
        Page<Mod> results;
        if ("Favorites".equals(viewCategory)) {
            User currentUserObj = userService.getCurrentUser();
            List<String> likedIds = (currentUserObj != null && currentUserObj.getLikedModIds() != null)
                    ? currentUserObj.getLikedModIds()
                    : new ArrayList<>();
            PageRequest pageable = PageRequest.of(page, size, Sort.by("title"));
            results = modRepository.findFavorites(likedIds, search != null ? search : "", pageable);
        } else {
            Sort sort = switch (sortBy != null ? sortBy : "relevance") {
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

            String authorIdParam = author;
            if (author != null && !author.isEmpty()) {
                Optional<User> u = userRepository.findByUsernameIgnoreCase(author);
                if (u.isPresent()) {
                    authorIdParam = u.get().getId();
                }
            }

            results = modRepository.searchMods(
                    search, tags, gameVersion, contentType, null, minDownloads, minFavorites, pageable,
                    currentUsername, sortBy, viewCategory,
                    dateCutoff, authorIdParam
            );
        }

        if (results != null && results.hasContent()) {
            results.getContent().forEach(mod -> {
                if (mod.getVersions() != null) {
                    mod.getVersions().forEach(v -> v.setScanResult(null));
                }
                if (mod.getAuthor() == null && mod.getAuthorId() != null) {
                    userRepository.findById(mod.getAuthorId()).ifPresent(u -> mod.setAuthor(u.getUsername()));
                }
            });
        }
        return results;
    }

    public Page<Mod> searchDeletedProjects(String query, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "deletedAt"));
        return modRepository.searchDeletedMods(query, pageable);
    }

    public List<Mod> getAllMods() { return modRepository.findAll(); }
    public List<Mod> getPublishedMods() { return modRepository.findAllPublished(); }

    @Cacheable("sitemapData")
    public List<Mod> getSitemapData() {
        return modRepository.findAllForSitemap();
    }

    public Mod getRawModById(String identifier) {
        Optional<Mod> direct = Optional.empty();
        if (identifier != null) {
            direct = modRepository.findById(identifier);
        }
        if (direct.isEmpty() && identifier != null) {
            direct = modRepository.findBySlug(identifier.toLowerCase());
        }
        return direct.orElse(null);
    }

    public Mod getAdminProjectDetails(String identifier) {
        Mod mod = getRawModById(identifier);
        if (mod == null) return null;

        if (mod.getAuthorId() != null) {
            userRepository.findById(mod.getAuthorId()).ifPresent(u -> mod.setAuthor(u.getUsername()));
        }

        return mod;
    }

    @Cacheable(value = "projectDetails", key = "#identifier")
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
            if (mod.getDeletedAt() != null) return null;

            if (mod.getAuthorId() != null) {
                userRepository.findById(mod.getAuthorId()).ifPresent(u -> mod.setAuthor(u.getUsername()));
            }

            User currentUser = userService.getCurrentUser();
            boolean isPrivileged = hasEditPermission(mod, currentUser) || isAdmin(currentUser);

            if (!isPrivileged && mod.getVersions() != null) {
                List<ModVersion> visibleVersions = mod.getVersions().stream()
                        .filter(v -> v.getReviewStatus() == ModVersion.ReviewStatus.APPROVED)
                        .collect(Collectors.toList());
                mod.setVersions(visibleVersions);
            }

            if (!isPrivileged && mod.getVersions() != null) {
                mod.getVersions().forEach(v -> v.setScanResult(null));
            }

            if (!mod.isAllowComments() && !isPrivileged) {
                mod.setComments(new ArrayList<>());
            }

            if (mod.getComments() != null && !mod.getComments().isEmpty()) {
                Set<String> usersToFetch = new HashSet<>();
                for (Comment c : mod.getComments()) {
                    if (c.getUserAvatarUrl() == null || c.getUserAvatarUrl().isEmpty()) {
                        usersToFetch.add(c.getUser());
                    }
                    if (c.getDeveloperReply() != null) {
                        if (c.getDeveloperReply().getUserAvatarUrl() == null || c.getDeveloperReply().getUserAvatarUrl().isEmpty()) {
                            usersToFetch.add(c.getDeveloperReply().getUser());
                        }
                    }
                }

                if (!usersToFetch.isEmpty()) {
                    try {
                        List<User> users = userRepository.findByUsernameIn(usersToFetch);
                        Map<String, String> avatarMap = users.stream()
                                .collect(Collectors.toMap(
                                        u -> u.getUsername().toLowerCase(),
                                        u -> u.getAvatarUrl() != null ? u.getAvatarUrl() : "",
                                        (existing, replacement) -> existing
                                ));

                        for (Comment c : mod.getComments()) {
                            if (c.getUserAvatarUrl() == null || c.getUserAvatarUrl().isEmpty()) {
                                String avatar = avatarMap.get(c.getUser().toLowerCase());
                                if (avatar != null && !avatar.isEmpty()) {
                                    c.setUserAvatarUrl(avatar);
                                }
                            }
                            if (c.getDeveloperReply() != null) {
                                if (c.getDeveloperReply().getUserAvatarUrl() == null || c.getDeveloperReply().getUserAvatarUrl().isEmpty()) {
                                    String avatar = avatarMap.get(c.getDeveloperReply().getUser().toLowerCase());
                                    if (avatar != null && !avatar.isEmpty()) {
                                        c.getDeveloperReply().setUserAvatarUrl(avatar);
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to batch load avatars for comments", e);
                    }
                }
            }
            return mod;
        }
        return null;
    }

    public Page<Mod> getContributedProjects(String userId, Pageable pageable) {
        Page<Mod> results = modRepository.findByContributors(userId, pageable);
        if (results.hasContent()) results.getContent().forEach(m -> {
            if (m.getVersions() != null) m.getVersions().forEach(v -> v.setScanResult(null));
            if (m.getAuthorId() != null && m.getAuthor() == null) {
                userRepository.findById(m.getAuthorId()).ifPresent(u -> m.setAuthor(u.getUsername()));
            }
        });
        return results;
    }

    public Page<Mod> getCreatorProjects(String userId, Pageable pageable) {
        User u = userRepository.findById(userId).orElse(null);
        if (u == null) return Page.empty();

        Page<Mod> results = modRepository.findByAuthorIdAndStatusExact(u.getId(), "PUBLISHED", pageable);
        if (results.hasContent()) results.getContent().forEach(m -> {
            if (m.getVersions() != null) m.getVersions().forEach(v -> v.setScanResult(null));
            m.setAuthor(u.getUsername());
        });
        return results;
    }

    public Page<Mod> getPrivilegedCreatorProjects(String userId, Pageable pageable) {
        User u = userRepository.findById(userId).orElse(null);
        if (u == null) return Page.empty();

        Page<Mod> results = modRepository.findByAuthorId(u.getId(), pageable);
        if (results.hasContent()) results.getContent().forEach(m -> m.setAuthor(u.getUsername()));
        return results;
    }

    public Mod createDraft(String title, String description, String classification, User user, String ownerName, String customSlug) {
        if (!user.isEmailVerified()) {
            throw new SecurityException("Email verification required.");
        }

        validateClassification(classification);
        if(isTitleTaken(title)) throw new IllegalArgumentException("Title already taken.");

        String finalAuthorId = user.getId();
        String finalAuthorName = user.getUsername();

        if (ownerName != null && !ownerName.isEmpty() && !ownerName.equalsIgnoreCase(user.getUsername())) {
            Query query = new Query(Criteria.where("username").regex("^" + Pattern.quote(ownerName) + "$", "i"));
            User org = mongoTemplate.findOne(query, User.class);

            if (org == null) throw new IllegalArgumentException("Organization not found");
            if (org.getAccountType() != User.AccountType.ORGANIZATION) throw new IllegalArgumentException("Target is not an organization");

            boolean isMember = org.getOrganizationMembers().stream()
                    .anyMatch(m -> m.getUserId().equals(user.getId()));
            if (!isMember) throw new SecurityException("You do not have permission to create projects for this organization.");

            finalAuthorId = org.getId();
            finalAuthorName = org.getUsername();
        }

        long projectCount = modRepository.countByAuthorId(finalAuthorId);
        if (projectCount >= maxProjectsPerUser) {
            throw new IllegalStateException("Project limit reached. Maximum allowed projects per user/org: " + maxProjectsPerUser);
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
        mod.setAuthorId(finalAuthorId);
        mod.setAuthor(finalAuthorName);

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
        mod.setAllowComments(true);
        mod.setTags(new ArrayList<>());

        return modRepository.save(mod);
    }

    public Mod createDraft(String title, String description, String classification, User user, String ownerName) {
        return createDraft(title, description, classification, user, ownerName, null);
    }

    public void submitMod(String id, String username) {
        Mod mod = getRawModById(id);
        User user = userService.getCurrentUser();
        if(mod == null || !hasEditPermission(mod, user)) throw new SecurityException("Permission denied.");

        if (user != null && !user.isEmailVerified()) {
            throw new SecurityException("Email verification required.");
        }

        ensureEditable(mod);
        validateForPublishing(mod);

        mod.setStatus("PENDING");
        mod.setExpiresAt(null);
        mod.setUpdatedAt(LocalDateTime.now().toString());

        if (mod.getVersions() != null) {
            for (ModVersion version : mod.getVersions()) {
                if (version.getReviewStatus() == null || version.getReviewStatus() == ModVersion.ReviewStatus.REJECTED) {
                    version.setReviewStatus(ModVersion.ReviewStatus.PENDING);
                }
            }
        }

        modRepository.save(mod);
        evictProjectDetails(mod);
    }

    public void revertModToDraft(String id, String username) {
        Mod mod = getRawModById(id);
        User user = userService.getCurrentUser();
        if (mod == null || !hasEditPermission(mod, user)) throw new SecurityException("Permission denied.");

        if (!"PENDING".equals(mod.getStatus())) {
            throw new IllegalArgumentException("Only pending projects can be reverted to draft.");
        }

        mod.setStatus("DRAFT");
        mod.setExpiresAt(LocalDate.now().plusDays(30).toString());
        modRepository.save(mod);
        evictProjectDetails(mod);
    }

    public void archiveMod(String id, String username) {
        Mod mod = getRawModById(id);
        User user = userService.getCurrentUser();
        if (mod == null || !isOwner(mod, user)) throw new SecurityException("Permission denied.");

        if (!"PUBLISHED".equals(mod.getStatus()) && !"UNLISTED".equals(mod.getStatus())) {
            throw new IllegalArgumentException("Only published or unlisted projects can be archived.");
        }

        mod.setStatus("ARCHIVED");
        mod.setExpiresAt(null);
        modRepository.save(mod);
        evictProjectDetails(mod);
    }

    public void unlistMod(String id, String username) {
        Mod mod = getRawModById(id);
        User user = userService.getCurrentUser();
        if (mod == null || !isOwner(mod, user)) throw new SecurityException("Permission denied.");

        if (!"PUBLISHED".equals(mod.getStatus()) && !"ARCHIVED".equals(mod.getStatus())) {
            throw new IllegalArgumentException("Project must be published or archived to unlist.");
        }

        mod.setStatus("UNLISTED");
        mod.setExpiresAt(null);
        modRepository.save(mod);
        evictProjectDetails(mod);
    }

    public void publishMod(String id, String username) {
        Mod mod = getRawModById(id);
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

        if (mod.getVersions() != null) {
            mod.getVersions().forEach(v -> {
                if (v.getReviewStatus() == ModVersion.ReviewStatus.PENDING) {
                    v.setReviewStatus(ModVersion.ReviewStatus.APPROVED);
                }
            });
        }

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
        evictProjectDetails(mod);

        if (isNewRelease) {
            notifyNewProject(saved);
            triggerWebhook(saved);
            analyticsService.logNewProject(saved.getId());
            User author = getAuthorUser(saved);
            if(author != null) {
                notificationService.sendNotification(List.of(author.getId()), "Project Approved", saved.getTitle() + " has been approved and is now live!", getProjectLink(saved), saved.getImageUrl());
            }
        }
    }

    public void approveVersion(String modId, String versionId) {
        User user = userService.getCurrentUser();
        if (!isAdmin(user)) throw new SecurityException("Access Denied");

        Mod mod = getRawModById(modId);
        if(mod == null) throw new IllegalArgumentException("Project not found");

        ModVersion ver = mod.getVersions().stream()
                .filter(v -> v.getId().equals(versionId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Version not found"));

        ver.setReviewStatus(ModVersion.ReviewStatus.APPROVED);
        ver.setRejectionReason(null);
        ver.setScheduledPublishDate(null);
        mod.setUpdatedAt(LocalDateTime.now().toString());

        modRepository.save(mod);
        evictProjectDetails(mod);
        notifyUpdates(mod, ver.getVersionNumber());
        notifyDependents(mod, ver.getVersionNumber());
    }

    public void rejectVersion(String modId, String versionId, String reason) {
        User user = userService.getCurrentUser();
        if (!isAdmin(user)) throw new SecurityException("Access Denied");

        Mod mod = getRawModById(modId);
        if(mod == null) throw new IllegalArgumentException("Project not found");

        ModVersion ver = mod.getVersions().stream()
                .filter(v -> v.getId().equals(versionId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Version not found"));

        ver.setReviewStatus(ModVersion.ReviewStatus.REJECTED);
        ver.setRejectionReason(reason);
        ver.setScheduledPublishDate(null);
        modRepository.save(mod);
        evictProjectDetails(mod);

        User author = getAuthorUser(mod);
        if(author != null) {
            notificationService.sendNotification(
                    List.of(author.getId()),
                    "Version Rejected",
                    "Version " + ver.getVersionNumber() + " of " + mod.getTitle() + " was rejected. Reason: " + reason,
                    "/dashboard/projects",
                    mod.getImageUrl()
            );
        }
    }

    private void triggerWebhook(Mod mod) {
        taskExecutor.execute(() -> {
            try {
                String authorName = mod.getAuthor();
                if (authorName == null && mod.getAuthorId() != null) {
                    User u = userRepository.findById(mod.getAuthorId()).orElse(null);
                    if (u != null) authorName = u.getUsername();
                }

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
                body.put("developerName", authorName != null ? authorName : "Unknown");

                HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
                restTemplate.postForEntity(webhookUrl, request, String.class);
            } catch (Exception e) {
                logger.error("Failed to trigger webhook", e);
            }
        });
    }

    public void triggerRescan(String modId, String versionId) {
        User user = userService.getCurrentUser();

        if (user != null && !isAdmin(user)) {
            Bucket bucket = rescanBuckets.computeIfAbsent(user.getId(),
                    k -> Bucket.builder()
                            .addLimit(Bandwidth.classic(rescanLimitPerDay, Refill.greedy(rescanLimitPerDay, Duration.ofDays(1))))
                            .build());

            if (!bucket.tryConsume(1)) {
                throw new IllegalStateException("Daily rescan limit reached. Please wait 24 hours.");
            }
        }

        Mod mod = getRawModById(modId);
        if (mod == null) throw new IllegalArgumentException("Project not found");

        ModVersion version = mod.getVersions().stream()
                .filter(v -> v.getId().equals(versionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Version not found"));

        if (version.getFileUrl() == null) throw new IllegalArgumentException("Version has no file to scan");

        ScanResult pending = new ScanResult();
        pending.setStatus("SCANNING");
        version.setScanResult(pending);

        modRepository.save(mod);
        evictProjectDetails(mod);

        String originalFilename = version.getFileUrl().substring(version.getFileUrl().lastIndexOf('/') + 1);
        if (originalFilename.length() > 37 && originalFilename.charAt(36) == '-') {
            originalFilename = originalFilename.substring(37);
        }

        self.performBackgroundScan(modId, versionId, version.getFileUrl(), originalFilename, true);
    }

    public void rejectMod(String id, String reason) {
        Mod mod = getRawModById(id);
        User user = userService.getCurrentUser();

        if (user == null || user.getRoles() == null || !user.getRoles().contains("ADMIN")) {
            throw new SecurityException("Only Admins can reject projects.");
        }
        if(mod == null) throw new IllegalArgumentException("Project not found.");

        mod.setStatus("DRAFT");
        mod.setExpiresAt(LocalDate.now().plusDays(30).toString());
        modRepository.save(mod);
        evictProjectDetails(mod);

        User author = getAuthorUser(mod);
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

        validateRepositoryUrl(mod.getRepositoryUrl());

        if (!"MODPACK".equals(mod.getClassification())) {
            if (mod.getLicense() == null || mod.getLicense().isEmpty()) {
                throw new IllegalArgumentException("You must select a license before submitting.");
            }
        } else {
            mod.setLicense(null);
        }
    }

    public List<Mod> getVerificationQueue() {
        Query pendingProjectsQuery = new Query(Criteria.where("status").is("PENDING"));
        pendingProjectsQuery.fields().exclude("about", "comments", "galleryImages");
        List<Mod> pendingProjects = mongoTemplate.find(pendingProjectsQuery, Mod.class);

        Query pendingVersionsQuery = new Query(Criteria.where("status").is("PUBLISHED").and("versions.reviewStatus").is("PENDING"));
        pendingVersionsQuery.fields().exclude("about", "comments", "galleryImages");
        List<Mod> pendingVersions = mongoTemplate.find(pendingVersionsQuery, Mod.class);

        Set<Mod> combined = new HashSet<>(pendingProjects);
        combined.addAll(pendingVersions);

        List<Mod> result = new ArrayList<>(combined);
        result.sort(Comparator.comparing(Mod::getUpdatedAt));
        return result;
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanupExpiredDrafts() {
        String today = LocalDate.now().toString();
        modRepository.deleteByStatusAndExpiresAtBefore("DRAFT", today);
    }

    public void requestTransfer(String modId, String targetUsername, User requester) {
        Mod mod = getRawModById(modId);
        if (mod == null) throw new IllegalArgumentException("Project not found");
        if (!isOwner(mod, requester)) throw new SecurityException("Only the owner can transfer ownership.");
        ensureEditable(mod);

        User target = userRepository.findByUsername(targetUsername).orElseThrow(() -> new IllegalArgumentException("Target user/org not found"));
        if (mod.getAuthorId() != null && target.getId().equals(mod.getAuthorId())) throw new IllegalArgumentException("Project is already owned by this user.");
        if (mod.getAuthor() != null && target.getUsername().equalsIgnoreCase(mod.getAuthor())) throw new IllegalArgumentException("Project is already owned by this user.");

        mod.setPendingTransferTo(targetUsername);
        modRepository.save(mod);
        evictProjectDetails(mod);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("modId", mod.getId());
        metadata.put("action", "TRANSFER_REQUEST");

        String authorName = mod.getAuthor();
        if (authorName == null && mod.getAuthorId() != null) {
            User u = userRepository.findById(mod.getAuthorId()).orElse(null);
            if (u != null) authorName = u.getUsername();
        }

        notificationService.sendActionableNotification(
                List.of(target.getId()),
                "Transfer Request",
                (authorName != null ? authorName : "Someone") + " wants to transfer '" + mod.getTitle() + "' to you.",
                "/dashboard/projects",
                mod.getImageUrl(),
                "TRANSFER_REQUEST",
                metadata
        );
    }

    public void resolveTransfer(String modId, boolean accept, User responder) {
        Mod mod = getRawModById(modId);
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
            User oldOwner = getAuthorUser(mod);
            User newOwner = userRepository.findByUsernameIgnoreCase(mod.getPendingTransferTo()).orElseThrow();

            mod.setAuthorId(newOwner.getId());
            mod.setAuthor(null);
            mod.setPendingTransferTo(null);
            mod.getContributors().remove(newOwner.getUsername());
            modRepository.save(mod);
            evictProjectDetails(mod);

            if(oldOwner != null) {
                notificationService.sendNotification(List.of(oldOwner.getId()), "Transfer Accepted", mod.getTitle() + " has been transferred to " + newOwner.getUsername(), "/projects/" + mod.getId(), mod.getImageUrl());
            }
        } else {
            mod.setPendingTransferTo(null);
            modRepository.save(mod);
            evictProjectDetails(mod);

            User oldOwner = getAuthorUser(mod);
            if(oldOwner != null) {
                notificationService.sendNotification(List.of(oldOwner.getId()), "Transfer Declined", "Transfer request for " + mod.getTitle() + " was declined.", "/dashboard/projects", mod.getImageUrl());
            }
        }
    }

    public void addMod(Mod mod) {
        mod.setTags(validateTags(mod.getTags()));
        validateClassification(mod.getClassification());

        if (mod.getAuthorId() == null && mod.getAuthor() != null) {
            userRepository.findByUsernameIgnoreCase(mod.getAuthor()).ifPresent(u -> mod.setAuthorId(u.getId()));
        }

        mod.setStatus("PUBLISHED");
        modRepository.save(mod);
        evictProjectDetails(mod);
    }

    public void updateMod(String id, Mod updatedMod) {
        User user = userService.getCurrentUser();
        Mod existing = getRawModById(id);
        if (existing == null || !hasEditPermission(existing, user)) {
            throw new SecurityException("You do not have permission to edit this project.");
        }
        ensureEditable(existing);

        boolean slugChanged = false;

        if (updatedMod.getTags() != null) {
            existing.setTags(validateTags(updatedMod.getTags()));
        }

        if (updatedMod.getRepositoryUrl() != null && !updatedMod.getRepositoryUrl().isEmpty()) {
            validateRepositoryUrl(updatedMod.getRepositoryUrl());
        }

        existing.setTitle(sanitizer.sanitizePlainText(updatedMod.getTitle()));

        existing.setDescription(sanitizer.sanitizePlainText(updatedMod.getDescription()));

        if (updatedMod.getAbout() != null) {
            existing.setAbout(updatedMod.getAbout());
        }

        existing.setCategories(updatedMod.getCategories());

        if (updatedMod.getSlug() != null) {
            String newSlug = updatedMod.getSlug().toLowerCase();

            if (newSlug.isEmpty()) {
                existing.setSlug(null);
                slugChanged = true;
            } else if (!newSlug.equals(existing.getSlug())) {
                validateSlug(newSlug);

                Optional<Mod> conflict = modRepository.findBySlug(newSlug);
                if (conflict.isPresent() && !conflict.get().getId().equals(existing.getId())) {
                    throw new IllegalArgumentException("Project URL '" + newSlug + "' is already taken.");
                }

                existing.setSlug(newSlug);
                slugChanged = true;
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
        existing.setAllowComments(updatedMod.isAllowComments());

        if (updatedMod.getLinks() != null) existing.setLinks(updatedMod.getLinks());
        if (updatedMod.getImageUrl() != null) existing.setImageUrl(updatedMod.getImageUrl());

        existing.setUpdatedAt(LocalDateTime.now().toString());
        modRepository.save(existing);
        evictProjectDetails(existing);

        if (slugChanged) {
            Objects.requireNonNull(cacheManager.getCache("sitemapData")).clear();
        }
    }

    public void updateProjectIcon(String id, MultipartFile file) throws IOException {
        User user = userService.getCurrentUser();
        Mod mod = getRawModById(id);
        if (mod == null || !hasEditPermission(mod, user)) throw new SecurityException("Permission denied.");
        ensureEditable(mod);

        if (mod.getImageUrl() != null && !mod.getImageUrl().contains("default.png") && !mod.getImageUrl().contains("placeholder") && !mod.getImageUrl().contains("favicon")) {
            try { storageService.deleteFile(mod.getImageUrl()); } catch (Exception ignore) {}
        }
        String path = storageService.upload(file, "images");
        mod.setImageUrl(storageService.getPublicUrl(path));
        mod.setUpdatedAt(LocalDateTime.now().toString());
        modRepository.save(mod);
        evictProjectDetails(mod);
    }

    public void updateProjectBanner(String id, MultipartFile file) throws IOException {
        User user = userService.getCurrentUser();
        Mod mod = getRawModById(id);
        if (mod == null || !hasEditPermission(mod, user)) throw new SecurityException("Permission denied.");
        ensureEditable(mod);

        if (mod.getBannerUrl() != null && !mod.getBannerUrl().isEmpty()) {
            try { storageService.deleteFile(mod.getBannerUrl()); } catch (Exception ignore) {}
        }

        String path = storageService.upload(file, "images");
        mod.setBannerUrl(storageService.getPublicUrl(path));
        mod.setUpdatedAt(LocalDateTime.now().toString());
        modRepository.save(mod);
        evictProjectDetails(mod);
    }

    private void validateDependency(Mod parentMod, Mod depMod) {
        if (parentMod.getId().equals(depMod.getId())) throw new IllegalArgumentException("A project cannot depend on itself.");
        if ("SAVE".equals(parentMod.getClassification())) throw new IllegalArgumentException("Worlds cannot have dependencies.");
        if ("MODPACK".equals(depMod.getClassification()) || "SAVE".equals(depMod.getClassification())) throw new IllegalArgumentException("Modpacks and Worlds cannot be added as dependencies.");
    }

    public void updateVersion(String modId, String versionId, List<String> modIds, List<String> gameVersions, String changelog, ModVersion.Channel channel) {
        User user = userService.getCurrentUser();
        Mod mod = getRawModById(modId);
        if (mod == null) throw new IllegalArgumentException("Project not found");
        if (!hasEditPermission(mod, user)) throw new SecurityException("No permission.");
        ensureEditable(mod);

        ModVersion version = mod.getVersions().stream().filter(v -> v.getId().equals(versionId)).findFirst().orElse(null);
        if (version == null) throw new IllegalArgumentException("Version not found");

        if (gameVersions != null) {
            for (String gv : gameVersions) {
                if (!ALLOWED_GAME_VERSIONS.contains(gv)) {
                    throw new IllegalArgumentException("Invalid game version: " + gv);
                }
            }
            version.setGameVersions(gameVersions);
        }

        if (changelog != null) {
            version.setChangelog(sanitizer.sanitizePlainText(changelog));
        }

        if (channel != null) {
            version.setChannel(channel);
        }

        boolean isModpack = "MODPACK".equals(mod.getClassification());

        if (modIds != null) {
            List<ModDependency> newDeps = new ArrayList<>();
            List<String> simpleModIds = new ArrayList<>();

            for (String entry : modIds) {
                String[] parts = entry.split(":");
                if (parts.length < 2) throw new IllegalArgumentException("Invalid dependency format: " + entry);
                String depId = parts[0].trim();
                String depVer = parts[1].trim();

                boolean isOptional = !isModpack && parts.length >= 3 && "optional".equalsIgnoreCase(parts[2].trim());

                Mod depMod = getRawModById(depId);
                if (depMod == null) throw new IllegalArgumentException("Dependency not found: " + depId);
                validateDependency(mod, depMod);
                boolean exists = depMod.getVersions().stream().anyMatch(v -> v.getVersionNumber().equalsIgnoreCase(depVer));
                if (!exists) throw new IllegalArgumentException("Version " + depVer + " not found on project " + depId);
                newDeps.add(new ModDependency(depMod.getId(), depMod.getTitle(), depVer, isOptional));
                simpleModIds.add(depId);
            }

            if (isModpack && newDeps.size() < 2) {
                throw new IllegalArgumentException("Modpacks must have at least two valid dependencies.");
            }

            if (isModpack && !newDeps.equals(version.getDependencies())) {
                if (version.getFileUrl() != null && version.getFileUrl().endsWith(".zip")) {
                    try { storageService.deleteFile(version.getFileUrl()); } catch (Exception ignore) {}
                    version.setFileUrl(null);
                }
            }

            version.setDependencies(newDeps);
            if (isModpack && mod.getVersions().get(0).getId().equals(versionId)) {
                mod.setModIds(simpleModIds);
            }
        }

        mod.setUpdatedAt(LocalDateTime.now().toString());
        modRepository.save(mod);
        evictProjectDetails(mod);
    }

    public void addVersionToMod(String modId, String versionNumber, List<String> gameVersions,
                                MultipartFile file, String changelog, List<String> modIds) throws IOException {
        addVersionToMod(modId, versionNumber, gameVersions, file, changelog, modIds, ModVersion.Channel.RELEASE);
    }

    public void addVersionToMod(String modId, String versionNumber, List<String> gameVersions,
                                MultipartFile file, String changelog, List<String> modIds, ModVersion.Channel channel) throws IOException {
        User user = userService.getCurrentUser();
        if (user != null && !user.isEmailVerified()) {
            throw new SecurityException("Email verification required.");
        }

        Mod mod = getRawModById(modId);
        if (mod == null) throw new IllegalArgumentException("Project not found");
        if (!hasEditPermission(mod, user)) throw new SecurityException("You do not have permission to update this project.");
        ensureEditable(mod);

        if ("DRAFT".equals(mod.getStatus()) && !mod.getVersions().isEmpty()) {
            throw new IllegalArgumentException("Drafts are limited to one version. Please delete the existing version or publish the project.");
        }

        LocalDate now = LocalDate.now();
        long versionsToday = mod.getVersions().stream()
                .filter(v -> {
                    try {
                        return LocalDate.parse(v.getReleaseDate()).equals(now);
                    } catch (Exception e) { return false; }
                })
                .count();

        if (versionsToday >= maxVersionsPerDay) {
            throw new IllegalStateException("Daily version limit reached (" + maxVersionsPerDay + "). Please try again tomorrow.");
        }

        LocalDate monthAgo = now.minusDays(30);
        long versionsMonth = mod.getVersions().stream()
                .filter(v -> {
                    try {
                        LocalDate d = LocalDate.parse(v.getReleaseDate());
                        return d.isAfter(monthAgo) || d.equals(monthAgo);
                    } catch (Exception e) { return false; }
                })
                .count();

        if (versionsMonth >= maxVersionsPerMonth) {
            throw new IllegalStateException("Monthly version limit reached (" + maxVersionsPerMonth + ").");
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
        String fileHash = null;

        if (file != null) {
            if (!isModpack) {
                try {
                    MessageDigest digest = MessageDigest.getInstance("SHA-256");
                    byte[] encodedhash = digest.digest(file.getBytes());
                    StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
                    for (byte b : encodedhash) {
                        String hex = Integer.toHexString(0xff & b);
                        if (hex.length() == 1) {
                            hexString.append('0');
                        }
                        hexString.append(hex);
                    }
                    fileHash = hexString.toString();
                } catch (Exception e) {
                    logger.error("Failed to calculate file hash", e);
                }
            }
            filePath = storageService.upload(file, "files/" + mod.getClassification().toLowerCase());
        }

        ModVersion ver = new ModVersion();
        ver.setId(UUID.randomUUID().toString());
        ver.setVersionNumber(versionNumber);
        ver.setGameVersions(gameVersions);
        ver.setFileUrl(filePath);
        ver.setReleaseDate(LocalDateTime.now().toString());
        ver.setDownloadCount(0);
        ver.setChangelog(sanitizer.sanitizePlainText(changelog));
        ver.setChannel(channel);
        ver.setHash(fileHash);

        ver.setReviewStatus(ModVersion.ReviewStatus.PENDING);

        ver.setDependencies(new ArrayList<>());
        List<String> simpleModIds = new ArrayList<>();

        if (modIds != null) {
            for (String entry : modIds) {
                String[] parts = entry.split(":");
                if (parts.length < 2) throw new IllegalArgumentException("Invalid dependency format.");
                String depId = parts[0].trim();
                String depVer = parts[1].trim();

                boolean isOptional = !isModpack && parts.length >= 3 && "optional".equalsIgnoreCase(parts[2].trim());

                Mod depMod = getRawModById(depId);

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
            ScanResult pendingScan = new ScanResult();
            pendingScan.setStatus("SCANNING");
            ver.setScanResult(pendingScan);
        }

        mod.getVersions().add(0, ver);
        modRepository.save(mod);
        evictProjectDetails(mod);

        if (file != null && !isModpack) {
            notificationService.sendNotification(
                    List.of(user.getId()),
                    "Version Submitted",
                    "Your project update is pending approval and will likely be online in under 24 hours.",
                    "/dashboard/projects",
                    mod.getImageUrl()
            );

            self.performBackgroundScan(mod.getId(), ver.getId(), filePath, file.getOriginalFilename(), false);
        }
    }

    public Optional<ModVersion> getVersionByHash(String hash) {
        Query query = new Query(Criteria.where("versions.hash").is(hash));
        query.fields().include("versions.$");
        Mod mod = mongoTemplate.findOne(query, Mod.class);
        if (mod != null && !mod.getVersions().isEmpty()) {
            return Optional.of(mod.getVersions().get(0));
        }
        return Optional.empty();
    }

    @Async
    public void performBackgroundScan(String modId, String versionId, String filePath, String originalFilename, boolean isManualRescan) {
        try {
            byte[] fileBytes = storageService.download(filePath);
            ScanResult scanResult = wardenService.scanFile(fileBytes, originalFilename);

            Mod mod = modRepository.findById(modId).orElse(null);
            if (mod == null) return;

            Update update = new Update()
                    .set("versions.$.scanResult", scanResult);

            boolean approvedImmediately = false;

            if ("CLEAN".equals(scanResult.getStatus())) {
                if (isManualRescan) {
                    update.set("versions.$.reviewStatus", ModVersion.ReviewStatus.APPROVED);
                    update.set("updatedAt", LocalDateTime.now().toString());
                    approvedImmediately = true;
                    logger.info("Manually re-scanned version {} for project {} approved immediately.", versionId, modId);
                } else {
                    long delayMinutes = ThreadLocalRandom.current().nextLong(30, 1440);
                    LocalDateTime scheduledTime = LocalDateTime.now().plusMinutes(delayMinutes);
                    update.set("versions.$.scheduledPublishDate", scheduledTime.toString());
                    logger.info("Clean version {} for project {} scheduled for release at {}", versionId, modId, scheduledTime);
                }
            } else {
                if ("INFECTED".equals(scanResult.getStatus())) {
                    logger.warn("Warden detected malware in project {} version {}", modId, versionId);
                }
            }

            Query query = new Query(Criteria.where("_id").is(modId).and("versions._id").is(versionId));
            mongoTemplate.updateFirst(query, update, Mod.class);
            evictProjectDetails(mod);

            if (approvedImmediately && "PUBLISHED".equals(mod.getStatus())) {
                ModVersion ver = mod.getVersions().stream()
                        .filter(v -> v.getId().equals(versionId))
                        .findFirst()
                        .orElse(null);

                if (ver != null) {
                    notifyUpdates(mod, ver.getVersionNumber());
                    notifyDependents(mod, ver.getVersionNumber());
                }
            }

        } catch (Exception e) {
            logger.error("Async Warden scan failed for mod " + modId, e);
            ScanResult failed = new ScanResult();
            failed.setStatus("FAILED");

            Query query = new Query(Criteria.where("_id").is(modId).and("versions._id").is(versionId));
            Update update = new Update()
                    .set("versions.$.scanResult", failed);
            mongoTemplate.updateFirst(query, update, Mod.class);

            Mod mod = modRepository.findById(modId).orElse(null);
            evictProjectDetails(mod);
        }
    }

    @Scheduled(fixedDelayString = "${app.scheduler.release-check:900000}")
    public void processScheduledReleases() {
        Query query = new Query(Criteria.where("versions").elemMatch(
                Criteria.where("reviewStatus").is("PENDING")
                        .and("scheduledPublishDate").lte(LocalDateTime.now().toString())
        ));

        List<Mod> modsToUpdate = mongoTemplate.find(query, Mod.class);

        for (Mod mod : modsToUpdate) {
            boolean updated = false;
            List<String> releasedVersions = new ArrayList<>();

            for (ModVersion version : mod.getVersions()) {
                if (version.getReviewStatus() == ModVersion.ReviewStatus.PENDING &&
                        version.getScheduledPublishDate() != null &&
                        LocalDateTime.parse(version.getScheduledPublishDate()).isBefore(LocalDateTime.now())) {

                    version.setReviewStatus(ModVersion.ReviewStatus.APPROVED);
                    version.setScheduledPublishDate(null);
                    releasedVersions.add(version.getVersionNumber());
                    updated = true;
                }
            }

            if (updated) {
                mod.setUpdatedAt(LocalDateTime.now().toString());
                modRepository.save(mod);
                evictProjectDetails(mod);

                for (String verNum : releasedVersions) {
                    notifyUpdates(mod, verNum);
                    notifyDependents(mod, verNum);
                }

                User author = getAuthorUser(mod);
                if (author != null) {
                    notificationService.sendNotification(
                            List.of(author.getId()),
                            "Version Published",
                            "Your version for " + mod.getTitle() + " has been processed and is now live.",
                            getProjectLink(mod),
                            mod.getImageUrl()
                    );
                }
            }
        }
    }

    public void deleteVersion(String modId, String versionId, String username) {
        User user = userService.getCurrentUser();
        Mod mod = getRawModById(modId);
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
            evictProjectDetails(mod);
        } else {
            throw new IllegalArgumentException("Version not found.");
        }
    }

    public void deleteMod(String id, String username) {
        Mod mod = getRawModById(id);
        User user = userService.getCurrentUser();

        if (mod == null || !isOwner(mod, user)) {
            throw new SecurityException("Permission denied or Project not found.");
        }

        ensureEditable(mod);
        performDeletionStrategy(mod);
    }

    public void adminDeleteProject(String id) {
        User user = userService.getCurrentUser();
        if (!isAdmin(user)) throw new SecurityException("Access Denied");
        Mod mod = getRawModById(id);
        if (mod == null) throw new IllegalArgumentException("Project not found");
        performDeletionStrategy(mod);
    }

    public void adminRestoreProject(String id, String targetStatus) {
        User user = userService.getCurrentUser();
        if (!isAdmin(user)) throw new SecurityException("Access Denied");

        Mod mod = getRawModById(id);
        if (mod == null) throw new IllegalArgumentException("Project not found");

        if (!"DELETED".equals(mod.getStatus()) || mod.getDeletedAt() == null) {
            throw new IllegalArgumentException("Project is not in a recoverable state.");
        }

        List<String> validStates = List.of("PUBLISHED", "DRAFT", "UNLISTED", "ARCHIVED");
        if (!validStates.contains(targetStatus)) {
            throw new IllegalArgumentException("Invalid target status. Must be one of: " + validStates);
        }

        mod.setStatus(targetStatus);
        mod.setDeletedAt(null);
        modRepository.save(mod);
        evictProjectDetails(mod);
        logger.info("Project " + mod.getId() + " restored to " + targetStatus + " by admin " + user.getUsername());
    }

    public void adminHardDeleteProject(String id) {
        User user = userService.getCurrentUser();
        if (!isAdmin(user)) throw new SecurityException("Access Denied");

        Mod mod = getRawModById(id);
        if (mod == null) throw new IllegalArgumentException("Project not found");

        if (!"DELETED".equals(mod.getStatus())) {
            throw new IllegalArgumentException("Project must be in DELETED state to perform a hard delete.");
        }

        logger.info("Admin " + user.getUsername() + " forcing hard delete of project " + id);
        performHardDelete(mod);
    }

    public void adminUnlistProject(String id) {
        User user = userService.getCurrentUser();
        if (!isAdmin(user)) throw new SecurityException("Access Denied");
        Mod mod = getRawModById(id);
        if (mod == null) throw new IllegalArgumentException("Project not found");
        mod.setStatus("UNLISTED");
        mod.setExpiresAt(null);
        modRepository.save(mod);
        evictProjectDetails(mod);
    }

    public void adminDeleteVersion(String modId, String versionId) {
        User user = userService.getCurrentUser();
        if (!isAdmin(user)) throw new SecurityException("Access Denied");
        Mod mod = getRawModById(modId);
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
            evictProjectDetails(mod);
        } else {
            throw new IllegalArgumentException("Version not found.");
        }
    }

    public void handleUserDeletion(User user) {
        List<Mod> userMods = modRepository.findByAuthor(user.getUsername());
        for (Mod mod : userMods) {
            performDeletionStrategy(mod);
        }
        Objects.requireNonNull(cacheManager.getCache("projectSearch")).clear();
        Objects.requireNonNull(cacheManager.getCache("sitemapData")).clear();
    }

    private void performDeletionStrategy(Mod mod) {
        mod.setStatus("DELETED");
        mod.setDeletedAt(LocalDateTime.now());
        modRepository.save(mod);
        evictProjectDetails(mod);

        logger.info("Soft deleted project " + mod.getId() + ". Grace period started.");
    }

    @Scheduled(cron = "0 0 0 * * ?")
    public void cleanupExpiredDeletedProjects() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        List<Mod> expiredMods = modRepository.findByDeletedAtBefore(cutoff);

        for (Mod mod : expiredMods) {
            try {
                performHardDelete(mod);
            } catch (Exception e) {
                logger.error("Failed to cleanup expired project " + mod.getId(), e);
            }
        }

        String today = LocalDate.now().toString();
        modRepository.deleteByStatusAndExpiresAtBefore("DRAFT", today);
    }

    private void performHardDelete(Mod mod) {
        List<Mod> dependents = modRepository.findByDependency(mod.getId());

        if (!dependents.isEmpty()) {
            logger.info("Converting expired project " + mod.getId() + " to skeleton due to dependencies.");
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
            mod.setComments(new ArrayList<>());
            mod.setTags(new ArrayList<>());

            mod.setDeletedAt(null);
            modRepository.save(mod);
            evictProjectDetails(mod);
        } else {
            logger.info("Hard deleting project " + mod.getId());
            analyticsService.deleteProjectAnalytics(mod.getId());

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
            evictProjectDetails(mod);

            for (String depId : dependencyIds) {
                cleanupOrphanedDependency(depId);
            }
        }
    }

    private void cleanupOrphanedDependency(String modId) {
        Mod mod = getRawModById(modId);
        if (mod != null && "DELETED".equals(mod.getStatus())) {
            List<Mod> remainingDependents = modRepository.findByDependency(modId);

            if (remainingDependents.isEmpty()) {
                logger.info("Project " + modId + " is no longer a dependency for anyone. Cleaning up orphan.");
                performHardDelete(mod);
            }
        }
    }

    public byte[] generateModpackZip(Mod pack, ModVersion version) throws IOException {
        User user = userService.getCurrentUser();

        if (user != null) {
            Bucket bucket = modpackGenBuckets.computeIfAbsent(user.getId(),
                    k -> Bucket.builder()
                            .addLimit(Bandwidth.classic(modpackGenLimitPerHour, Refill.greedy(modpackGenLimitPerHour, Duration.ofHours(1))))
                            .build());

            if (!bucket.tryConsume(1)) {
                throw new IllegalStateException("Modpack generation limit reached. Please wait a while before trying again.");
            }
        }

        if (version.getFileUrl() != null) {
            try {
                return storageService.download(version.getFileUrl());
            } catch (Exception e) {
                logger.warn("Cached modpack zip missing, regenerating: {}", version.getFileUrl());
                version.setFileUrl(null);
            }
        }

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
                Mod depMod = getRawModById(dep.getModId());
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

        byte[] zipBytes = baos.toByteArray();

        try {
            String fileName = (pack.getSlug() != null ? pack.getSlug() : pack.getId()) + "-" + version.getVersionNumber() + ".zip";
            MultipartFile multipart = new InMemoryMultipartFile(fileName, zipBytes);
            String uploadPath = storageService.upload(multipart, "modpacks");

            version.setFileUrl(uploadPath);
            modRepository.save(pack);
        } catch (Exception e) {
            logger.error("Failed to cache generated modpack", e);
        }

        return zipBytes;
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
        Mod mod = getRawModById(modId);
        if (mod != null) {
            mod.setDownloadCount(mod.getDownloadCount() + 1);
            modRepository.save(mod);

            int count = mod.getDownloadCount();
            if (count == 10000 || count == 100000 || count == 1000000 || count == 10000000) {
                String title = "Download Milestone Reached!";
                String msg = mod.getTitle() + " has hit " + String.format("%,d", count) + " downloads!";
                String link = "/dashboard";
                User author = getAuthorUser(mod);
                if (author != null) {
                    notificationService.sendNotification(List.of(author.getId()), title, msg, link, mod.getImageUrl());
                }
            }
        }
    }

    private void notifyUpdates(Mod mod, String versionNumber) {
        taskExecutor.execute(() -> {
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
        });
    }

    private void notifyNewProject(Mod mod) {
        taskExecutor.execute(() -> {
            try {
                User author = getAuthorUser(mod);
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
        });
    }

    private void notifyDependents(Mod updatedMod, String version) {
        taskExecutor.execute(() -> {
            List<Mod> dependents = modRepository.findByDependency(updatedMod.getId());
            for (Mod dependent : dependents) {
                User author = getAuthorUser(dependent);
                if (author != null && author.getNotificationPreferences().getDependencyUpdates() != User.NotificationLevel.OFF) {
                    String title = "Dependency Update";
                    String msg = updatedMod.getTitle() + " (used in " + dependent.getTitle() + ") has been updated to version " + version + ".";
                    notificationService.sendNotification(List.of(author.getId()), title, msg, getProjectLink(updatedMod), updatedMod.getImageUrl());
                }
            }
        });
    }

    public void checkTrendingNotifications() {
        String[] algos = {"trending", "popular", "gems", "relevance"};
        for (String algo : algos) {
            Page<Mod> topMods = getMods(null, null, 0, 12, algo, null, null, null, null, null, algo, null, null);
            for (Mod mod : topMods.getContent()) {
                LocalDateTime lastNotified = mod.getLastTrendingNotification() != null
                        ? LocalDateTime.parse(mod.getLastTrendingNotification()) : null;

                if (lastNotified == null || lastNotified.isBefore(LocalDateTime.now().minusWeeks(1))) {
                    User author = getAuthorUser(mod);
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
        Mod mod = getRawModById(modId);
        if (mod == null) throw new IllegalArgumentException("Project not found");
        if (!isOwner(mod, currentUser)) throw new SecurityException("Only the owner can manage contributors.");
        ensureEditable(mod);

        User invitee = userRepository.findByUsername(usernameToInvite).orElseThrow(() -> new IllegalArgumentException("User not found"));
        mod.getPendingInvites().add(usernameToInvite);
        modRepository.save(mod);
        evictProjectDetails(mod);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("modId", mod.getId());
        metadata.put("action", "CONTRIBUTOR_INVITE");
        notificationService.sendActionableNotification(List.of(invitee.getId()), "Contributor Invite", "You have been invited to contribute to " + mod.getTitle() + ".", "/dashboard/projects", mod.getImageUrl(), "CONTRIBUTOR_INVITE", metadata);
    }

    public void inviteContributor(String modId, String userId, boolean isId) {
        if (!isId) {
            inviteContributor(modId, userId);
            return;
        }
        User currentUser = userService.getCurrentUser();
        Mod mod = getRawModById(modId);
        if (mod == null) throw new IllegalArgumentException("Project not found");
        if (!isOwner(mod, currentUser)) throw new SecurityException("Only the owner can manage contributors.");
        ensureEditable(mod);

        User invitee = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        mod.getPendingInvites().add(invitee.getUsername());
        modRepository.save(mod);
        evictProjectDetails(mod);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("modId", mod.getId());
        metadata.put("action", "CONTRIBUTOR_INVITE");
        notificationService.sendActionableNotification(List.of(invitee.getId()), "Contributor Invite", "You have been invited to contribute to " + mod.getTitle() + ".", "/dashboard/projects", mod.getImageUrl(), "CONTRIBUTOR_INVITE", metadata);
    }

    public void removeContributor(String modId, String usernameToRemove) {
        User currentUser = userService.getCurrentUser();
        Mod mod = getRawModById(modId);
        if (mod != null && isOwner(mod, currentUser)) {
            ensureEditable(mod);
            mod.getContributors().remove(usernameToRemove);
            if (mod.getPendingInvites() != null) {
                mod.getPendingInvites().remove(usernameToRemove);
            }
            modRepository.save(mod);
            evictProjectDetails(mod);
        }
    }

    public void acceptInvite(String modId, String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Mod mod = getRawModById(modId);
        if (mod != null && mod.getPendingInvites().remove(user.getUsername())) {
            mod.getContributors().add(user.getUsername());
            modRepository.save(mod);
            evictProjectDetails(mod);
            User owner = getAuthorUser(mod);
            if (owner != null) {
                notificationService.sendNotification(
                        List.of(owner.getId()),
                        "Invite Accepted",
                        user.getUsername() + " joined the team for " + mod.getTitle(),
                        getProjectLink(mod) + "/contributors",
                        user.getAvatarUrl()
                );
            }
        }
    }

    public void declineInvite(String modId, String userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Mod mod = getRawModById(modId);
        if (mod != null && mod.getPendingInvites().remove(user.getUsername())) {
            modRepository.save(mod);
            evictProjectDetails(mod);
        }
    }

    public void addComment(String modId, String username, String content) {
        Mod mod = getRawModById(modId);
        if (mod != null) {
            if (!mod.isAllowComments()) {
                throw new IllegalStateException("Comments are disabled for this project.");
            }

            Comment comment = new Comment();
            comment.setId(UUID.randomUUID().toString());
            comment.setUser(username);

            userRepository.findByUsername(username).ifPresent(u -> comment.setUserAvatarUrl(u.getAvatarUrl()));

            comment.setContent(sanitizer.sanitizePlainText(content));
            comment.setDate(LocalDate.now().toString());

            if (mod.getComments() == null) mod.setComments(new ArrayList<>());
            mod.getComments().add(0, comment);

            modRepository.save(mod);
            evictProjectDetails(mod);

            User author = getAuthorUser(mod);
            if (author != null && !author.getUsername().equals(username) && author.getNotificationPreferences().getNewComments() != User.NotificationLevel.OFF) {
                notificationService.sendNotification(
                        List.of(author.getId()),
                        "New Comment",
                        username + " commented on " + mod.getTitle(),
                        getProjectLink(mod),
                        mod.getImageUrl()
                );
            }
        }
    }

    public void editComment(String modId, String commentId, String username, String newContent) {
        Mod mod = getRawModById(modId);
        if (mod != null) {
            Comment comment = mod.getComments().stream()
                    .filter(c -> c.getId().equals(commentId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Comment not found"));

            if (!comment.getUser().equalsIgnoreCase(username)) {
                throw new SecurityException("You can only edit your own comments.");
            }

            comment.setContent(sanitizer.sanitizePlainText(newContent));
            comment.setUpdatedAt(LocalDateTime.now().toString());

            modRepository.save(mod);
            evictProjectDetails(mod);
        }
    }

    public void deleteComment(String modId, String commentId, String username) {
        Mod mod = getRawModById(modId);
        User user = userRepository.findByUsername(username).orElse(null);
        if (mod != null && user != null) {
            boolean isModOwner = hasEditPermission(mod, user);

            mod.getComments().removeIf(c -> {
                if(c.getId().equals(commentId)) {
                    if(!c.getUser().equalsIgnoreCase(username) && !isModOwner) {
                        throw new SecurityException("Permission denied.");
                    }
                    return true;
                }
                return false;
            });
            modRepository.save(mod);
            evictProjectDetails(mod);
        }
    }

    public void replyToComment(String modId, String commentId, String replyContent, String username) {
        User user = userRepository.findByUsername(username).orElseThrow(() -> new IllegalArgumentException("User not found"));
        Mod mod = getRawModById(modId);

        if (mod != null) {
            if (!hasEditPermission(mod, user)) {
                throw new SecurityException("Only project team members can reply to comments.");
            }

            Comment comment = mod.getComments().stream()
                    .filter(c -> c.getId().equals(commentId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Comment not found"));

            Comment.Reply reply = new Comment.Reply();
            reply.setUser(user.getUsername());
            reply.setUserAvatarUrl(user.getAvatarUrl());
            reply.setContent(sanitizer.sanitizePlainText(replyContent));
            reply.setDate(LocalDateTime.now().toString());

            comment.setDeveloperReply(reply);

            modRepository.save(mod);
            evictProjectDetails(mod);

            User commenter = userRepository.findByUsername(comment.getUser()).orElse(null);
            if (commenter != null) {
                notificationService.sendNotification(
                        List.of(commenter.getId()),
                        "Developer Reply",
                        mod.getAuthor() + " replied to your comment on " + mod.getTitle(),
                        getProjectLink(mod),
                        mod.getImageUrl()
                );
            }
        }
    }

    public void toggleFavorite(String modId, String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        Mod mod = getRawModById(modId);
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
            evictProjectDetails(mod);
        }
    }

    public void addGalleryImage(String id, String imageUrl) {
        Mod mod = getRawModById(id);
        if (mod != null) {
            ensureEditable(mod);
            if (mod.getGalleryImages().size() >= maxGalleryImagesPerProject) {
                throw new IllegalStateException("Maximum gallery images reached (" + maxGalleryImagesPerProject + ").");
            }
            mod.getGalleryImages().add(imageUrl);
            modRepository.save(mod);
            evictProjectDetails(mod);
        }
    }

    public void removeGalleryImage(String modId, String imageUrl, String username) {
        User user = userService.getCurrentUser();
        Mod mod = getRawModById(modId);
        if (mod != null && hasEditPermission(mod, user)) {
            ensureEditable(mod);
            mod.getGalleryImages().remove(imageUrl);
            storageService.deleteFile(imageUrl);
            modRepository.save(mod);
            evictProjectDetails(mod);
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
        List<User> creators = userRepository.findByUsernameContainingIgnoreCase(query, PageRequest.of(0, 10));
        return creators;
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
            evictProjectDetails(mod);
            analyticsService.logDownload(mod.getId(), null, mod.getAuthor(), false, "internal");
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

    private static class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final byte[] content;

        public InMemoryMultipartFile(String name, byte[] content) {
            this.name = name;
            this.content = content;
        }

        @Override public String getName() { return name; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return "application/zip"; }
        @Override public boolean isEmpty() { return content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() throws IOException { return content; }
        @Override public InputStream getInputStream() throws IOException { return new ByteArrayInputStream(content); }
        @Override public void transferTo(File dest) throws IOException, IllegalStateException { FileCopyUtils.copy(content, dest); }
    }
}