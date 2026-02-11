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
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
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
            "2026.01.13-dcad8778f", "2026.01.17-4b0f30090", "2026.01.24-6e2d4fc36", "2026.01.28-87d03be09", "2026.02.06-aa1b071c2"
    );

    private static final Set<String> ALLOWED_CLASSIFICATIONS = Set.of(
            "PLUGIN", "DATA", "ART", "SAVE", "MODPACK"
    );

    private static final Pattern STRICT_VERSION_PATTERN = Pattern.compile("^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$");
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

        User authorUser = null;
        if (mod.getAuthorId() != null) {
            authorUser = userRepository.findById(mod.getAuthorId()).orElse(null);
        } else if (mod.getAuthor() != null) {
            authorUser = userRepository.findByUsernameIgnoreCase(mod.getAuthor()).orElse(null);
        }

        if (authorUser != null && authorUser.getAccountType() == User.AccountType.ORGANIZATION) {
            boolean isOrgMember = authorUser.getOrganizationMembers().stream()
                    .anyMatch(m -> m.getUserId().equals(user.getId()));

            if (isOrgMember) return true;
        }

        return (mod.getContributors() != null && mod.getContributors().stream().anyMatch(c -> c.equalsIgnoreCase(user.getUsername())));
    }

    public boolean isOwner(Mod mod, User user) {
        if (mod == null || user == null) return false;

        if (mod.getAuthorId() != null && mod.getAuthorId().equals(user.getId())) return true;
        if (mod.getAuthor() != null && mod.getAuthor().equalsIgnoreCase(user.getUsername())) return true;

        User authorUser = null;
        if (mod.getAuthorId() != null) {
            authorUser = userRepository.findById(mod.getAuthorId()).orElse(null);
        } else if (mod.getAuthor() != null) {
            authorUser = userRepository.findByUsernameIgnoreCase(mod.getAuthor()).orElse(null);
        }

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
        return self.getModsCached(tags, search, page, size, sortBy, gameVersion, contentType, minRating, minDownloads, viewCategory, dateRange, author);
    }

    @Cacheable(
            value = "projectSearch",
            key = "{#tags, #search, #page, #size, #sortBy, #gameVersion, #contentType, #minRating, #minDownloads, #viewCategory, #dateRange, #author}",
            condition = "!('Favorites'.equals(#viewCategory))"
    )
    public Page<Mod> getModsCached(
            List<String> tags, String search, int page, int size, String sortBy,
            String gameVersion, String contentType, Double minRating, Integer minDownloads, String viewCategory,
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

            String authorIdParam = author;
            if (author != null && !author.isEmpty()) {
                Optional<User> u = userRepository.findByUsernameIgnoreCase(author);
                if (u.isPresent()) {
                    authorIdParam = u.get().getId();
                }
            }

            results = modRepository.searchMods(
                    search, tags, gameVersion, contentType, minRating, minDownloads, pageable,
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

            if (!mod.isAllowReviews() && !isPrivileged) {
                mod.setReviews(new ArrayList<>());
            }

            if (mod.getReviews() != null && !mod.getReviews().isEmpty()) {
                Set<String> reviewersToFetch = new HashSet<>();
                for (Review r : mod.getReviews()) {
                    if (r.getUserAvatarUrl() == null || r.getUserAvatarUrl().isEmpty()) {
                        reviewersToFetch.add(r.getUser());
                    }
                }

                if (!reviewersToFetch.isEmpty()) {
                    try {
                        List<User> users = userRepository.findByUsernameIn(reviewersToFetch);
                        Map<String, String> avatarMap = users.stream()
                                .collect(Collectors.toMap(
                                        u -> u.getUsername().toLowerCase(),
                                        u -> u.getAvatarUrl() != null ? u.getAvatarUrl() : "",
                                        (existing, replacement) -> existing
                                ));

                        for (Review r : mod.getReviews()) {
                            if (r.getUserAvatarUrl() == null || r.getUserAvatarUrl().isEmpty()) {
                                String avatar = avatarMap.get(r.getUser().toLowerCase());
                                if (avatar != null && !avatar.isEmpty()) {
                                    r.setUserAvatarUrl(avatar);
                                }
                            }
                        }
                    } catch (Exception e) {
                        logger.warn("Failed to batch load avatars for reviews", e);
                    }
                }
            }
            return mod;
        }
        return null;
    }

    public Page<Mod> getContributedProjects(String username, Pageable pageable) {
        Page<Mod> results = modRepository.findByContributors(username, pageable);
        if (results.hasContent()) results.getContent().forEach(m -> {
            if (m.getVersions() != null) m.getVersions().forEach(v -> v.setScanResult(null));
            if (m.getAuthorId() != null && m.getAuthor() == null) {
                userRepository.findById(m.getAuthorId()).ifPresent(u -> m.setAuthor(u.getUsername()));
            }
        });
        return results;
    }

    public Page<Mod> getCreatorProjects(String username, Pageable pageable) {
        User u = userRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (u == null) return Page.empty();

        Page<Mod> results = modRepository.findByAuthorIdAndStatusExact(u.getId(), "PUBLISHED", pageable);

        if (results.hasContent()) results.getContent().forEach(m -> {
            if (m.getVersions() != null) m.getVersions().forEach(v -> v.setScanResult(null));
            m.setAuthor(u.getUsername());
        });
        return results;
    }

    public Page<Mod> getPrivilegedCreatorProjects(String username, Pageable pageable) {
        User u = userRepository.findByUsernameIgnoreCase(username).orElse(null);
        if (u == null) return Page.empty();

        Page<Mod> results = modRepository.findByAuthorId(u.getId(), pageable);
        if (results.hasContent()) results.getContent().forEach(m -> m.setAuthor(u.getUsername()));
        return results;
    }

    public Mod createDraft(String title, String description, String classification, User user, String ownerName, String customSlug) {
        validateClassification(classification);
        if(isTitleTaken(title)) throw new IllegalArgumentException("Title already taken.");

        String finalAuthorId = user.getId();
        String finalAuthorUsername = user.getUsername();

        if (ownerName != null && !ownerName.isEmpty() && !ownerName.equalsIgnoreCase(user.getUsername())) {
            Query query = new Query(Criteria.where("username").regex("^" + Pattern.quote(ownerName) + "$", "i"));
            User org = mongoTemplate.findOne(query, User.class);

            if (org == null) throw new IllegalArgumentException("Organization not found");
            if (org.getAccountType() != User.AccountType.ORGANIZATION) throw new IllegalArgumentException("Target is not an organization");

            boolean isMember = org.getOrganizationMembers().stream()
                    .anyMatch(m -> m.getUserId().equals(user.getId()));
            if (!isMember) throw new SecurityException("You do not have permission to create projects for this organization.");

            finalAuthorId = org.getId();
            finalAuthorUsername = org.getUsername();
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

        mod.setAuthor(finalAuthorUsername);

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
        mod.setAllowReviews(true);

        return modRepository.save(mod);
    }

    public Mod createDraft(String title, String description, String classification, User user, String ownerName) {
        return createDraft(title, description, classification, user, ownerName, null);
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
            String oldAuthor = mod.getAuthor();
            String oldAuthorId = mod.getAuthorId();

            User newOwner = userRepository.findByUsernameIgnoreCase(mod.getPendingTransferTo()).orElseThrow();

            mod.setAuthorId(newOwner.getId());
            mod.setAuthor(null);

            mod.setPendingTransferTo(null);

            mod.getContributors().remove(newOwner.getUsername());

            modRepository.save(mod);
            evictProjectDetails(mod);

            String notifyId = oldAuthorId;
            if (notifyId == null && oldAuthor != null) {
                userRepository.findByUsernameIgnoreCase(oldAuthor).ifPresent(u -> notificationService.sendNotification(List.of(u.getId()), "Transfer Accepted", mod.getTitle() + " has been transferred.", "/projects/" + mod.getId(), mod.getImageUrl()));
            } else if (notifyId != null) {
                notificationService.sendNotification(List.of(notifyId), "Transfer Accepted", mod.getTitle() + " has been transferred.", "/projects/" + mod.getId(), mod.getImageUrl());
            }
        } else {
            mod.setPendingTransferTo(null);
            modRepository.save(mod);
            evictProjectDetails(mod);

            String notifyId = mod.getAuthorId();
            if (notifyId == null && mod.getAuthor() != null) {
                userRepository.findByUsernameIgnoreCase(mod.getAuthor()).ifPresent(u -> notificationService.sendNotification(List.of(u.getId()), "Transfer Declined", "Transfer request for " + mod.getTitle() + " was declined.", "/dashboard/projects", mod.getImageUrl()));
            } else if (notifyId != null) {
                notificationService.sendNotification(List.of(notifyId), "Transfer Declined", "Transfer request for " + mod.getTitle() + " was declined.", "/dashboard/projects", mod.getImageUrl());
            }
        }
    }

    public void addMod(Mod mod) {
        validateTags(mod.getTags());
        validateClassification(mod.getClassification());

        if (mod.getAuthorId() == null && mod.getAuthor() != null) {
            userRepository.findByUsernameIgnoreCase(mod.getAuthor()).ifPresent(u -> mod.setAuthorId(u.getId()));
        }

        mod.setStatus("PUBLISHED");
        modRepository.save(mod);
        evictProjectDetails(mod);
    }

    private void notifyNewProject(Mod mod) {
        taskExecutor.execute(() -> {
            try {
                User author = null;
                if (mod.getAuthorId() != null) author = userRepository.findById(mod.getAuthorId()).orElse(null);
                else if (mod.getAuthor() != null) author = userRepository.findByUsernameIgnoreCase(mod.getAuthor()).orElse(null);

                if (author == null) return;

                String authorName = author.getUsername();

                List<User> followers = userRepository.findByFollowingIdsContaining(author.getId());
                String title = "New Project from " + authorName;
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

    private User getAuthorUser(Mod mod) {
        if (mod.getAuthorId() != null) return userRepository.findById(mod.getAuthorId()).orElse(null);
        if (mod.getAuthor() != null) return userRepository.findByUsernameIgnoreCase(mod.getAuthor()).orElse(null);
        return null;
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

    public void checkTrendingNotifications() {
        String[] algos = {"trending", "popular", "gems", "relevance"};
        for (String algo : algos) {
            Page<Mod> topMods = getMods(null, null, 0, 12, algo, null, null, null, null, algo, null, null);
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