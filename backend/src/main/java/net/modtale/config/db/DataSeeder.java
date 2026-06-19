package net.modtale.config.db;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import java.time.Instant;
import java.util.stream.Collectors;
import net.modtale.config.properties.AppSeedingProperties;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.auth.ReservedAccountGuardService;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ReservedAccountGuardService reservedAccountGuardService;
    private final AppSeedingProperties seedingProperties;

    private static final int PUBLISHED_PROJECT_LIMIT = 100;
    private static final String SUPER_ADMIN_ID = "692620f7c2f3266e23ac0ded";
    private static final String ADMIN_ID = "692620f7c2f3266e23ac0dee";
    private static final List<String> MOCK_COLLECTIONS = List.of(
            "users",
            "projects",
            "project_monthly_stats",
            "platform_monthly_stats",
            "admin_logs",
            "reports",
            "notifications",
            "api_keys",
            "banned_emails",
            "status_incidents",
            "status_history"
    );

    public DataSeeder(
            MongoTemplate mongoTemplate,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            ReservedAccountGuardService reservedAccountGuardService,
            AppSeedingProperties seedingProperties
    ) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.reservedAccountGuardService = reservedAccountGuardService;
        this.seedingProperties = seedingProperties;
    }

    @Override
    public void run(String... args) {
        reservedAccountGuardService.purgeReservedAccountsIfProduction();

        if (reservedAccountGuardService.isProductionDeployment()) {
            logger.warn("Seeding skipped: production deployment detected.");
            return;
        }

        if (!seedingProperties.enabled()) {
            return;
        }

        String currentDbName = mongoTemplate.getDb().getName();

        if (seedingProperties.mode() == AppSeedingProperties.Mode.MOCK) {
            seedMockDatabase(currentDbName);
            return;
        }

        if (seedingProperties.mode() == AppSeedingProperties.Mode.TEMPLATE) {
            seedFromSanitizedTemplate(currentDbName);
            return;
        }

        ensureSuperAdmin();
        ensureAdmin();
        ensureNormalUser();

        if (currentDbName.equalsIgnoreCase(seedingProperties.sourceDb())) {
            logger.warn("Seeding ABORTED: Current DB is same as Source DB ({})", currentDbName);
            return;
        }

        if (mongoTemplate.getCollection("projects").countDocuments() > 0) {
            logger.info("Database '{}' already contains projects. Skipping content clone.", currentDbName);
            return;
        }

        logger.info("Initializing Preview Environment...");

        try {
            logger.info("Attempting to clone relational subset from '{}'...", seedingProperties.sourceDb());

            MongoDatabase sourceDb = mongoTemplate.getMongoDatabaseFactory().getMongoDatabase(seedingProperties.sourceDb());
            MongoDatabase targetDb = mongoTemplate.getDb();

            long sourceProjectCount = sourceDb.getCollection("projects").countDocuments();
            if (sourceProjectCount == 0) {
                logger.warn("SOURCE DB '{}' IS EMPTY! Cannot clone data.", seedingProperties.sourceDb());
                return;
            }

            List<Document> compiledProjects = new ArrayList<>();

            MongoCollection<Document> sourceProjectsCol = sourceDb.getCollection("projects");

            List<Bson> publishedPipeline = Arrays.asList(
                    Aggregates.match(Filters.and(
                            Filters.in(
                                    "status",
                                    ProjectStatus.PUBLISHED.name(),
                                    ProjectStatus.ARCHIVED.name()
                            ),
                            Filters.eq("deletedAt", null)
                    )),
                    Aggregates.sample(PUBLISHED_PROJECT_LIMIT)
            );
            sourceProjectsCol.aggregate(publishedPipeline).into(compiledProjects);
            logger.info("Fetched {} random public projects.", compiledProjects.size());

            ensureClassificationCoverage(sourceProjectsCol, compiledProjects);

            Set<String> selectedProjectIds = projectIds(compiledProjects);

            List<Document> dependencyProjects = collectDependencyProjects(sourceProjectsCol, selectedProjectIds);
            if (!dependencyProjects.isEmpty()) {
                compiledProjects.addAll(dependencyProjects);
                selectedProjectIds = projectIds(compiledProjects);
                logger.info("Included {} dependency projects for selected projects.", dependencyProjects.size());
            }

            if (compiledProjects.isEmpty()) {
                logger.info("No projects successfully retrieved from source. Seeding finished.");
                return;
            }

            Set<String> authorIds = compiledProjects.stream()
                    .map(doc -> doc.getString("authorId"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            logger.info("Cloning referenced authors ({}) for compiled projects...", authorIds.size());
            cloneSpecificUsers(sourceDb, targetDb, authorIds);

            try {
                Set<String> finalSelectedProjectIds = selectedProjectIds;
                compiledProjects.forEach(project -> stripPrivateProjectFields(project, finalSelectedProjectIds));
                targetDb.getCollection("projects").insertMany(compiledProjects);
                logger.info("Cloned {} public projects to local database.", compiledProjects.size());
            } catch (MongoBulkWriteException e) {
                logger.warn("Project insertion warning (duplicates might exist): {}", e.getMessage());
            }

            seedSyntheticSupplementDocuments(targetDb);

            logger.info("Seeding completed successfully.");

        } catch (MongoException e) {
            logger.error("Failed to seed preview database", e);
        }
    }

    private void seedMockDatabase(String currentDbName) {
        MongoDatabase targetDb = mongoTemplate.getDb();

        if (!seedingProperties.reset() && mongoTemplate.getCollection("projects").countDocuments() > 0) {
            logger.info("Database '{}' already contains projects. Skipping mock database import.", currentDbName);
            return;
        }

        logger.info(
                "Initializing '{}' with generated synthetic mock database documents (reset={}).",
                currentDbName,
                seedingProperties.reset()
        );

        try {
            if (seedingProperties.reset()) {
                for (String collectionName : MOCK_COLLECTIONS) {
                    targetDb.getCollection(collectionName).deleteMany(new Document());
                }
            }

            for (String collectionName : MOCK_COLLECTIONS) {
                List<Document> documents = syntheticMockDocuments(collectionName);
                MongoCollection<Document> collection = targetDb.getCollection(collectionName);

                for (Document document : documents) {
                    upsertMockDocument(collection, document);
                }

                logger.info("Imported {} mock documents into '{}'.", documents.size(), collectionName);
            }

            logger.info("Mock database import completed successfully.");
        } catch (MongoException e) {
            logger.error("Failed to import generated mock database documents", e);
        }
    }

    private void seedFromSanitizedTemplate(String currentDbName) {
        String sourceDbName = seedingProperties.sourceDb();
        if (sourceDbName == null || sourceDbName.isBlank()) {
            logger.error("Template seeding aborted: app.seeding.source-db must be configured.");
            return;
        }

        String normalizedSource = sourceDbName.toLowerCase(Locale.ROOT);
        if (!normalizedSource.contains("mock") && !normalizedSource.contains("template")) {
            logger.error(
                    "Template seeding aborted: source database '{}' is not named like a mock/template database.",
                    sourceDbName
            );
            return;
        }

        if (currentDbName.equalsIgnoreCase(sourceDbName)) {
            logger.warn("Template seeding aborted: current DB is the same as source DB ({})", currentDbName);
            return;
        }

        MongoDatabase sourceDb = mongoTemplate.getMongoDatabaseFactory().getMongoDatabase(sourceDbName);
        MongoDatabase targetDb = mongoTemplate.getDb();

        if (!seedingProperties.reset() && mongoTemplate.getCollection("projects").countDocuments() > 0) {
            logger.info("Database '{}' already contains projects. Skipping template import.", currentDbName);
            return;
        }

        try {
            long sourceProjectCount = sourceDb.getCollection("projects").countDocuments();
            if (sourceProjectCount == 0) {
                logger.warn(
                        "Template database '{}' has no projects. Falling back to generated synthetic mock documents.",
                        sourceDbName
                );
                seedMockDatabase(currentDbName);
                return;
            }

            if (seedingProperties.reset()) {
                for (String collectionName : MOCK_COLLECTIONS) {
                    targetDb.getCollection(collectionName).deleteMany(new Document());
                }
            }

            for (String collectionName : MOCK_COLLECTIONS) {
                List<Document> documents = fetchSubset(sourceDb, collectionName, templateLimit(collectionName));
                MongoCollection<Document> collection = targetDb.getCollection(collectionName);

                for (Document document : documents) {
                    upsertMockDocument(collection, document);
                }

                logger.info("Imported {} sanitized template documents into '{}'.", documents.size(), collectionName);
            }

            logger.info("Sanitized template import completed successfully.");
        } catch (MongoException e) {
            logger.error("Failed to import sanitized template database '{}'", sourceDbName, e);
        }
    }

    private int templateLimit(String collectionName) {
        return switch (collectionName) {
            case "projects" -> 150;
            case "project_monthly_stats" -> 500;
            case "platform_monthly_stats", "status_history" -> 120;
            case "admin_logs", "reports", "notifications", "api_keys", "banned_emails", "status_incidents" -> 100;
            default -> 200;
        };
    }

    private List<Document> syntheticMockDocuments(String collectionName) {
        return switch (collectionName) {
            case "users" -> syntheticUsers();
            case "projects" -> syntheticProjects();
            case "project_monthly_stats" -> List.of(
                    doc("_id", "stats-mock-waystones-2026-06", "projectId", "mock-plugin-waystones",
                            "authorId", "mock-creator-1", "year", 2026, "month", 6, "totalViews", 4800,
                            "totalDownloads", 1180, "apiDownloads", 160, "frontendDownloads", 1020,
                            "days", doc("01", doc("v", 640, "d", 140), "08", doc("v", 920, "d", 220),
                                    "15", doc("v", 1260, "d", 310), "17", doc("v", 1380, "d", 340)),
                            "versionDownloads", doc("waystones-1-0-0", doc("2026.03.11", 1180))),
                    doc("_id", "stats-mock-frontier-2026-06", "projectId", "mock-modpack-frontier",
                            "authorId", "mock-org-1", "year", 2026, "month", 6, "totalViews", 3600,
                            "totalDownloads", 840, "apiDownloads", 90, "frontendDownloads", 750,
                            "days", doc("01", doc("v", 420, "d", 90), "08", doc("v", 780, "d", 180),
                                    "15", doc("v", 980, "d", 240), "17", doc("v", 1120, "d", 270)),
                            "versionDownloads", doc("frontier-1-0-0", doc("2026.03.11", 840)))
            );
            case "platform_monthly_stats" -> List.of(
                    doc("_id", "platform-2026-06", "year", 2026, "month", 6, "totalViews", 18400,
                            "totalDownloads", 4120, "apiDownloads", 520, "frontendDownloads", 3600,
                            "newProjects", 8, "newUsers", 6, "newOrgs", 1,
                            "days", doc("01", doc("v", 1800, "d", 390, "a", 42, "f", 348, "n", 1, "u", 1, "o", 0),
                                    "08", doc("v", 2400, "d", 540, "a", 64, "f", 476, "n", 2, "u", 2, "o", 1),
                                    "15", doc("v", 3100, "d", 720, "a", 80, "f", 640, "n", 1, "u", 1, "o", 0),
                                    "17", doc("v", 3400, "d", 790, "a", 90, "f", 700, "n", 0, "u", 1, "o", 0)))
            );
            case "admin_logs" -> List.of(
                    doc("_id", "mock-admin-log-1", "adminUsername", "admin", "action", "PROJECT_APPROVED",
                            "targetId", "mock-plugin-waystones", "targetType", "PROJECT",
                            "details", "Synthetic audit event for preview testing.", "timestamp", date("2026-06-18T14:30:00Z")),
                    doc("_id", "mock-admin-log-2", "adminUsername", "super_admin", "action", "REPORT_RESOLVED",
                            "targetId", "mock-report-1", "targetType", "REPORT",
                            "details", "Synthetic report resolution for preview testing.", "timestamp", date("2026-06-18T15:10:00Z"))
            );
            case "reports" -> List.of(
                    doc("_id", "mock-report-1", "reporterId", "mock-user-1", "reporterUsername", "user",
                            "targetId", "mock-plugin-review-me", "targetType", "PROJECT",
                            "targetSummary", "Review Me", "reason", "Synthetic queue item",
                            "description", "Mock report used only to populate admin review screens.",
                            "status", "OPEN", "createdAt", date("2026-06-18T13:45:00Z")),
                    doc("_id", "mock-report-2", "reporterId", "mock-creator-2", "reporterUsername", "pixelwright",
                            "targetId", "mock-comment-waystones-1", "targetType", "COMMENT",
                            "targetSummary", "Waystones comment", "reason", "Synthetic resolved report",
                            "description", "Resolved mock report for pagination and filters.",
                            "status", "RESOLVED", "createdAt", date("2026-06-17T16:20:00Z"),
                            "resolvedBy", ADMIN_ID, "resolutionNote", "Synthetic resolution note.")
            );
            case "notifications" -> List.of(
                    doc("_id", "mock-notification-1", "userId", "mock-user-1", "title", "Preview project updated",
                            "message", "Waystones published a synthetic preview update.",
                            "link", "/projects/waystones", "iconUrl", "https://placehold.co/128x128/2563eb/f8fafc?text=W",
                            "isRead", false, "type", "PROJECT_UPDATE", "metadata", doc("projectId", "mock-plugin-waystones"),
                            "createdAt", date("2026-06-18T12:00:00Z")),
                    doc("_id", "mock-notification-2", "userId", "mock-creator-1", "title", "New mock comment",
                            "message", "A synthetic user commented on Waystones.",
                            "link", "/projects/waystones?tab=comments", "iconUrl", "https://placehold.co/128x128/0f766e/f8fafc?text=U",
                            "isRead", true, "type", "COMMENT", "metadata", doc("projectId", "mock-plugin-waystones"),
                            "createdAt", date("2026-06-18T12:30:00Z"))
            );
            case "api_keys" -> List.of(
                    doc("_id", "mock-api-key-1", "userId", "mock-user-1", "name", "Preview API Key",
                            "keyHash", passwordEncoder.encode("md_mock-preview-key"),
                            "prefix", "md_mock-pr", "tier", "USER",
                            "contextPermissions", doc("global", List.of("PROJECT_READ", "VERSION_READ")),
                            "createdAt", date("2026-06-16T10:00:00Z"))
            );
            case "banned_emails" -> List.of(
                    doc("_id", "mock-banned-email-1", "email", "blocked-user@example.test",
                            "reason", "Synthetic banned email for admin UI testing.",
                            "bannedBy", ADMIN_ID, "bannedAt", date("2026-06-15T10:00:00Z"))
            );
            case "status_incidents" -> List.of(
                    doc("_id", "mock-status-incident-1", "kind", "INCIDENT", "state", "RESOLVED",
                            "impact", "DEGRADED", "title", "Synthetic preview incident",
                            "affectedServices", List.of("Backend API"), "startedAt", date("2026-06-12T14:00:00Z"),
                            "resolvedAt", date("2026-06-12T14:35:00Z"), "createdAt", date("2026-06-12T14:00:00Z"),
                            "updatedAt", date("2026-06-12T14:35:00Z"), "createdBy", ADMIN_ID,
                            "createdByUsername", "admin",
                            "updates", List.of(doc("id", "mock-status-update-1", "state", "RESOLVED",
                                    "impact", "OPERATIONAL", "message", "Synthetic incident resolved.",
                                    "createdAt", date("2026-06-12T14:35:00Z"), "createdBy", ADMIN_ID,
                                    "createdByUsername", "admin"))),
                    doc("_id", "mock-maintenance-1", "kind", "MAINTENANCE", "state", "SCHEDULED",
                            "impact", "DEGRADED", "title", "Synthetic scheduled maintenance",
                            "affectedServices", List.of("Frontend"), "scheduledStart", date("2026-06-25T04:00:00Z"),
                            "scheduledEnd", date("2026-06-25T05:00:00Z"), "createdAt", date("2026-06-18T09:00:00Z"),
                            "updatedAt", date("2026-06-18T09:00:00Z"), "createdBy", ADMIN_ID,
                            "createdByUsername", "admin", "updates", List.of())
            );
            case "status_history" -> List.of(
                    doc("_id", "mock-status-history-1", "timestamp", date("2026-06-18T12:00:00Z"),
                            "apiLatency", 42, "dbLatency", 18, "storageLatency", 95,
                            "overallStatus", "OPERATIONAL", "apiStatus", "OPERATIONAL",
                            "dbStatus", "OPERATIONAL", "storageStatus", "OPERATIONAL"),
                    doc("_id", "mock-status-history-2", "timestamp", date("2026-06-18T12:05:00Z"),
                            "apiLatency", 61, "dbLatency", 24, "storageLatency", 140,
                            "overallStatus", "DEGRADED", "apiStatus", "OPERATIONAL",
                            "dbStatus", "OPERATIONAL", "storageStatus", "DEGRADED")
            );
            default -> List.of();
        };
    }

    private List<Document> syntheticUsers() {
        String password = passwordEncoder.encode("password");
        return List.of(
                doc("_id", SUPER_ADMIN_ID, "username", "super_admin", "email", "super_admin@example.test",
                        "emailVerified", true, "password", password, "roles", List.of("USER", "ADMIN"),
                        "tier", "ENTERPRISE", "accountType", "USER", "bio", "Synthetic super admin account."),
                doc("_id", ADMIN_ID, "username", "admin", "email", "admin@example.test",
                        "emailVerified", true, "password", password, "roles", List.of("USER", "ADMIN"),
                        "tier", "ENTERPRISE", "accountType", "USER", "bio", "Synthetic admin account."),
                doc("_id", "mock-user-1", "username", "user", "email", "user@example.test",
                        "emailVerified", true, "password", password, "roles", List.of("USER"),
                        "tier", "USER", "accountType", "USER", "bio", "Synthetic standard user account."),
                doc("_id", "mock-creator-1", "username", "atlas_studio", "email", "atlas.studio@example.test",
                        "emailVerified", true, "password", password, "roles", List.of("USER"),
                        "tier", "ENTERPRISE", "accountType", "USER", "bio", "Synthetic creator account."),
                doc("_id", "mock-creator-2", "username", "pixelwright", "email", "pixelwright@example.test",
                        "emailVerified", true, "password", password, "roles", List.of("USER"),
                        "tier", "USER", "accountType", "USER", "bio", "Synthetic creator account."),
                doc("_id", "mock-org-1", "username", "northstar_collective", "email", "northstar.collective@example.test",
                        "emailVerified", true, "password", password, "roles", List.of("USER"),
                        "tier", "ENTERPRISE", "accountType", "ORGANIZATION", "bio", "Synthetic organization account.")
        );
    }

    private List<Document> syntheticProjects() {
        return List.of(
                project("mock-plugin-waystones", "waystones", "Waystones", "PLUGIN", "PUBLISHED", "mock-creator-1", "atlas_studio", 12840),
                project("mock-data-loot-weaver", "loot-weaver", "Loot Weaver", "DATA", "PUBLISHED", "mock-creator-1", "atlas_studio", 9540),
                project("mock-art-cozy-props", "cozy-props", "Cozy Props", "ART", "PUBLISHED", "mock-creator-2", "pixelwright", 7210),
                project("mock-save-skyharbor", "skyharbor", "Skyharbor", "SAVE", "PUBLISHED", "mock-org-1", "northstar_collective", 5430),
                project("mock-modpack-frontier", "frontier-pack", "Frontier Pack", "MODPACK", "PUBLISHED", "mock-org-1", "northstar_collective", 4380),
                project("mock-plugin-review-me", "review-me", "Review Me", "PLUGIN", "PENDING", "mock-creator-1", "atlas_studio", 0),
                project("mock-draft-sandbox", "draft-sandbox", "Draft Sandbox", "DATA", "DRAFT", "mock-user-1", "user", 0),
                project("mock-private-lab", "private-lab", "Private Lab", "ART", "PRIVATE", "mock-creator-2", "pixelwright", 0),
                project("mock-plugin-admin-kit", "admin-kit", "Admin Kit", "PLUGIN", "UNLISTED", "mock-creator-2", "pixelwright", 1260),
                project("mock-archive-tutorial-town", "tutorial-town-legacy", "Tutorial Town Legacy", "SAVE", "ARCHIVED", "mock-org-1", "northstar_collective", 2310)
        );
    }

    private Document project(String id, String slug, String title, String classification, String status, String authorId, String author, int downloads) {
        return doc(
                "_id", id,
                "slug", slug,
                "title", title,
                "about", "Synthetic mock project for preview review.",
                "description", "Synthetic mock project used for public PR previews and local testing.",
                "authorId", authorId,
                "author", author,
                "imageUrl", "https://placehold.co/512x512/334155/f8fafc?text=" + title.replace(" ", "+"),
                "bannerUrl", "https://placehold.co/1600x420/1f2937/f8fafc?text=" + title.replace(" ", "+"),
                "classification", classification,
                "categories", List.of("Utilities"),
                "tags", List.of("Utilities", "Preview"),
                "downloadCount", downloads,
                "favoriteCount", downloads / 20,
                "downloads7d", downloads / 25,
                "downloads30d", downloads / 8,
                "downloads90d", downloads / 3,
                "trendScore", Math.min(100, downloads / 120),
                "relevanceScore", Math.min(100.0, downloads / 110.0),
                "popularScore", Math.min(100.0, downloads / 130.0),
                "trendingRank", downloads == 0 ? 999 : Math.max(1, 10000 / downloads),
                "popularRank", downloads == 0 ? 999 : Math.max(1, 12000 / downloads),
                "relevanceRank", downloads == 0 ? 999 : Math.max(1, 14000 / downloads),
                "rankingDirty", downloads == 0,
                "repositoryUrl", "https://example.test/modtale-mock/" + slug,
                "updatedAt", "2026-06-18",
                "createdAt", "2026-01-15",
                "license", "MIT",
                "links", new Document(),
                "types", List.of(classification.toLowerCase(Locale.ROOT)),
                "childProjectIds", List.of(),
                "modIds", List.of(slug),
                "allowModpacks", true,
                "allowComments", !"PRIVATE".equals(status),
                "hmWikiEnabled", false,
                "galleryCarouselEnabled", false,
                "status", status,
                "approvedBy", "PUBLISHED".equals(status) || "ARCHIVED".equals(status) || "UNLISTED".equals(status) ? ADMIN_ID : null,
                "projectRoles", List.of(),
                "teamMembers", List.of(),
                "teamInvites", List.of(),
                "galleryImages", List.of(),
                "galleryImageCaptions", new Document(),
                "comments", List.of(),
                "versions", List.of(doc("_id", slug + "-1-0-0", "versionNumber", "1.0.0",
                        "gameVersions", List.of("2026.03.11"), "fileUrl", "https://example.test/mock-downloads/" + slug + "-1.0.0.zip",
                        "hash", "sha256:mock-" + slug, "downloadCount", downloads, "releaseDate", "2026-06-18",
                        "changelog", "Synthetic changelog.", "dependencies", List.of(), "incompatibleProjectIds", List.of(),
                        "channel", "RELEASE", "reviewStatus", "PENDING".equals(status) ? "PENDING" : "APPROVED"))
        );
    }

    private Document doc(Object... values) {
        Document document = new Document();
        for (int i = 0; i + 1 < values.length; i += 2) {
            if (values[i + 1] != null) {
                document.append((String) values[i], values[i + 1]);
            }
        }
        return document;
    }

    private Date date(String isoInstant) {
        return Date.from(Instant.parse(isoInstant));
    }

    private void upsertMockDocument(MongoCollection<Document> collection, Document document) {
        Object id = document.get("_id");
        if (id == null) {
            collection.insertOne(document);
            return;
        }

        collection.replaceOne(
                Filters.eq("_id", id),
                document,
                new ReplaceOptions().upsert(true)
        );
    }

    private void seedSyntheticSupplementDocuments(MongoDatabase targetDb) {
        for (String collectionName : MOCK_COLLECTIONS) {
            MongoCollection<Document> collection = targetDb.getCollection(collectionName);
            List<Document> documents = syntheticMockDocuments(collectionName);

            for (Document document : documents) {
                upsertMockDocument(collection, document);
            }

            logger.info("Upserted {} synthetic supplement documents into '{}'.", documents.size(), collectionName);
        }
    }

    private Set<String> projectIds(List<Document> projects) {
        return projects.stream()
                .map(doc -> doc.get("_id"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private void stripPrivateProjectFields(Document project, Set<String> selectedProjectIds) {
        project.remove("approvedBy");
        project.remove("lastTrendingNotification");
        project.remove("pendingTransferTo");
        project.remove("teamInvites");

        filterStringListField(project, "childProjectIds", selectedProjectIds);
        sanitizeProjectComments(project);

        Object rawVersions = project.get("versions");
        if (!(rawVersions instanceof List<?> versions)) {
            return;
        }

        for (Object versionObj : versions) {
            if (!(versionObj instanceof Document versionDoc)) {
                continue;
            }

            versionDoc.remove("scanResult");
            versionDoc.remove("approvedIssueBaselines");
            versionDoc.remove("rejectionReason");
            versionDoc.remove("scheduledPublishDate");
            filterVersionReferences(versionDoc, selectedProjectIds);
        }
    }

    private void filterVersionReferences(Document versionDoc, Set<String> selectedProjectIds) {
        Object rawDependencies = versionDoc.get("dependencies");
        if (rawDependencies instanceof List<?> dependencies) {
            List<Document> filteredDependencies = new ArrayList<>();
            for (Object dependencyObj : dependencies) {
                if (!(dependencyObj instanceof Document dependencyDoc)) {
                    continue;
                }

                Object dependencyId = dependencyDoc.get("modId");
                if (dependencyId == null) {
                    dependencyId = dependencyDoc.get("projectId");
                }

                if (dependencyId != null && selectedProjectIds.contains(dependencyId.toString())) {
                    filteredDependencies.add(dependencyDoc);
                }
            }
            versionDoc.put("dependencies", filteredDependencies);
        }

        filterStringListField(versionDoc, "incompatibleProjectIds", selectedProjectIds);
    }

    private void filterStringListField(Document document, String fieldName, Set<String> allowedValues) {
        Object rawValue = document.get(fieldName);
        if (!(rawValue instanceof List<?> values)) {
            return;
        }

        List<String> filtered = values.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .filter(allowedValues::contains)
                .collect(Collectors.toList());
        document.put(fieldName, filtered);
    }

    private void sanitizeProjectComments(Document project) {
        Object rawComments = project.get("comments");
        if (!(rawComments instanceof List<?> comments)) {
            project.put("comments", List.of());
            return;
        }

        List<Document> sanitizedComments = new ArrayList<>();
        int index = 1;
        for (Object commentObj : comments) {
            if (!(commentObj instanceof Document commentDoc)) {
                continue;
            }

            String content = boundedString(commentDoc.get("content"), "", 5000);
            if (content.isBlank()) {
                continue;
            }

            Object rawId = commentDoc.get("id") != null ? commentDoc.get("id") : commentDoc.get("_id");
            Document sanitized = doc(
                    "id", boundedString(rawId, "mock-comment-" + index, 120),
                    "userId", boundedString(commentDoc.get("userId"), "mock-user-1", 120),
                    "content", content,
                    "date", boundedString(commentDoc.get("date"), "2026-01-01", 80),
                    "updatedAt", boundedString(commentDoc.get("updatedAt"), null, 80),
                    "upvotes", syntheticVoteIds(commentDoc.get("upvotes"), "comment-upvote"),
                    "downvotes", syntheticVoteIds(commentDoc.get("downvotes"), "comment-downvote"),
                    "developerReply", sanitizeCommentReply(commentDoc.get("developerReply"))
            );
            sanitizedComments.add(sanitized);
            index++;
        }

        project.put("comments", sanitizedComments);
    }

    private Document sanitizeCommentReply(Object replyObj) {
        if (!(replyObj instanceof Document replyDoc)) {
            return null;
        }

        String content = boundedString(replyDoc.get("content"), "", 5000);
        if (content.isBlank()) {
            return null;
        }

        return doc(
                "userId", boundedString(replyDoc.get("userId"), "mock-user-1", 120),
                "content", content,
                "date", boundedString(replyDoc.get("date"), "2026-01-01", 80),
                "upvotes", syntheticVoteIds(replyDoc.get("upvotes"), "reply-upvote"),
                "downvotes", syntheticVoteIds(replyDoc.get("downvotes"), "reply-downvote")
        );
    }

    private List<String> syntheticVoteIds(Object rawVotes, String prefix) {
        int voteCount = rawVotes instanceof Collection<?> votes ? votes.size() : 0;
        List<String> syntheticIds = new ArrayList<>();
        for (int i = 0; i < Math.min(voteCount, 50); i++) {
            syntheticIds.add("mock-" + prefix + "-voter-" + (i + 1));
        }
        return syntheticIds;
    }

    private String boundedString(Object value, String fallback, int limit) {
        if (value == null) {
            return fallback;
        }

        String stringValue = value.toString().trim();
        if (stringValue.isBlank()) {
            return fallback;
        }

        return stringValue.length() > limit ? stringValue.substring(0, limit) : stringValue;
    }

    private ObjectId getSafeObjectId(Object id) {
        if (id == null) return null;
        if (id instanceof ObjectId) return (ObjectId) id;
        if (id instanceof String) {
            try {
                return new ObjectId((String) id);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return null;
    }

    private void ensureSuperAdmin() {
        if (userRepository.existsById(SUPER_ADMIN_ID)) return;

        userRepository.findByUsername("super_admin").ifPresent(userRepository::delete);

        User user = new User();
        user.setId(SUPER_ADMIN_ID);
        user.setUsername("super_admin");
        user.setEmail("super_admin@example.test");
        user.setEmailVerified(true);
        user.setPassword(passwordEncoder.encode("password"));
        user.setRoles(List.of("USER", "ADMIN"));
        user.setBio("I am the Super Admin for this preview environment.");
        user.setTier(ApiKey.Tier.ENTERPRISE);
        userRepository.save(user);
        logger.info("Created Super Admin: super_admin / password (ID: {})", SUPER_ADMIN_ID);
    }

    private void ensureAdmin() {
        if (userRepository.existsById(ADMIN_ID)) return;

        userRepository.findByUsername("admin").ifPresent(userRepository::delete);

        User user = new User();
        user.setId(ADMIN_ID);
        user.setUsername("admin");
        user.setEmail("admin@example.test");
        user.setEmailVerified(true);
        user.setPassword(passwordEncoder.encode("password"));
        user.setRoles(List.of("USER", "ADMIN"));
        user.setBio("I am the Admin for this preview environment.");
        user.setTier(ApiKey.Tier.ENTERPRISE);
        userRepository.save(user);
        logger.info("Created Admin: admin / password (ID: {})", ADMIN_ID);
    }

    private void ensureNormalUser() {
        if (userRepository.findByUsername("user").isPresent()) return;

        User user = new User();
        user.setUsername("user");
        user.setEmail("user@example.test");
        user.setEmailVerified(true);
        user.setPassword(passwordEncoder.encode("password"));
        user.setRoles(List.of("USER"));
        user.setBio("I am a standard user.");
        user.setTier(ApiKey.Tier.USER);
        userRepository.save(user);
        logger.info("Created Normal User: user / password");
    }

    private List<Document> fetchSubset(MongoDatabase db, String collectionName, int limit) {
        List<Document> buffer = new ArrayList<>();
        db.getCollection(collectionName).find().limit(limit).into(buffer);
        return buffer;
    }

    private void cloneSpecificUsers(MongoDatabase source, MongoDatabase target, Set<String> userIds) {
        if (userIds.isEmpty()) return;

        MongoCollection<Document> sourceCol = source.getCollection("users");
        MongoCollection<Document> targetCol = target.getCollection("users");

        List<ObjectId> objectIds = userIds.stream()
                .map(this::getSafeObjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (objectIds.isEmpty()) return;

        List<Document> usersToClone = new ArrayList<>();
        sourceCol.find(Filters.in("_id", objectIds)).into(usersToClone);

        if (usersToClone.isEmpty()) {
            List<String> stringIds = new ArrayList<>(userIds);
            sourceCol.find(Filters.in("_id", stringIds)).into(usersToClone);
        }

        String defaultPasswordHash = passwordEncoder.encode("password");
        List<Document> safeToInsert = new ArrayList<>();

        for (Document user : usersToClone) {
            Document safeUser = sanitizeAuthorUser(user, defaultPasswordHash);
            if (safeUser == null) {
                continue;
            }
            safeToInsert.add(safeUser);
        }

        if (!safeToInsert.isEmpty()) {
            try {
                targetCol.insertMany(safeToInsert);
                logger.info("Cloned and sanitized {} users.", safeToInsert.size());
            } catch (MongoBulkWriteException e) {
                logger.warn("Partial user insertion error: {}", e.getMessage());
            }
        }
    }

    private Document sanitizeAuthorUser(Document user, String defaultPasswordHash) {
        Object rawId = user.get("_id");
        if (rawId == null) {
            return null;
        }

        String id = rawId.toString();
        String username = boundedString(user.get("username"), "", 80);
        if (
                id.equals(SUPER_ADMIN_ID)
                        || id.equals(ADMIN_ID)
                        || "user".equals(username)
                        || "super_admin".equals(username)
                        || "admin".equals(username)
        ) {
            return null;
        }

        String accountType = boundedString(user.get("accountType"), "USER", 32);
        String tier = boundedString(user.get("tier"), "USER", 32);
        Document safeUser = doc(
                "_id", rawId,
                "username", username,
                "email", "scrubbed-" + emailToken(id) + "@example.test",
                "emailVerified", true,
                "password", defaultPasswordHash,
                "roles", List.of("USER"),
                "tier", tier,
                "accountType", accountType,
                "likedModIds", List.of(),
                "followingIds", List.of(),
                "followerIds", List.of(),
                "connectedAccounts", List.of()
        );

        appendBoundedIfPresent(safeUser, "avatarUrl", user.get("avatarUrl"), 1000);
        appendBoundedIfPresent(safeUser, "bannerUrl", user.get("bannerUrl"), 1000);
        appendBoundedIfPresent(safeUser, "bio", user.get("bio"), 5000);
        appendBoundedIfPresent(safeUser, "createdAt", user.get("createdAt"), 80);

        Object badges = user.get("badges");
        if (badges instanceof List<?> badgeList) {
            safeUser.put(
                    "badges",
                    badgeList.stream()
                            .filter(Objects::nonNull)
                            .map(Object::toString)
                            .limit(20)
                            .collect(Collectors.toList())
            );
        }

        return safeUser;
    }

    private void appendBoundedIfPresent(Document document, String fieldName, Object value, int limit) {
        String stringValue = boundedString(value, null, limit);
        if (stringValue != null && !stringValue.isBlank()) {
            document.put(fieldName, stringValue);
        }
    }

    private String emailToken(String value) {
        String token = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
        token = token.replaceAll("^-+|-+$", "");
        if (token.isBlank()) {
            return "author";
        }
        return token.length() > 40 ? token.substring(0, 40) : token;
    }

    private List<Document> collectDependencyProjects(MongoCollection<Document> sourceProjectsCol, Set<String> initialProjectIds) {
        if (initialProjectIds.isEmpty()) return List.of();

        Set<String> visited = new HashSet<>(initialProjectIds);
        Set<String> dependencyIds = new LinkedHashSet<>();
        Deque<String> queue = new ArrayDeque<>(initialProjectIds);

        while (!queue.isEmpty()) {
            String projectId = queue.removeFirst();
            Document project = findPublicSeedProjectById(sourceProjectsCol, projectId);
            if (project == null) {
                continue;
            }

            for (String dependencyId : extractDependencyIds(project)) {
                if (dependencyId == null || dependencyId.isBlank() || visited.contains(dependencyId)) {
                    continue;
                }
                visited.add(dependencyId);
                dependencyIds.add(dependencyId);
                queue.addLast(dependencyId);
            }
        }

        return dependencyIds.stream()
                .map(depId -> findPublicSeedProjectById(sourceProjectsCol, depId))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Document findPublicSeedProjectById(MongoCollection<Document> sourceProjectsCol, String projectId) {
        if (projectId == null || projectId.isBlank()) return null;

        ObjectId objectId = getSafeObjectId(projectId);
        if (objectId != null) {
            Document project = sourceProjectsCol.find(Filters.eq("_id", objectId)).first();
            if (isPublicSeedProject(project)) return project;
        }

        Document project = sourceProjectsCol.find(Filters.eq("_id", projectId)).first();
        return isPublicSeedProject(project) ? project : null;
    }

    private boolean isPublicSeedProject(Document project) {
        if (project == null || project.get("deletedAt") != null) {
            return false;
        }

        String status = project.getString("status");
        return ProjectStatus.PUBLISHED.name().equals(status) || ProjectStatus.ARCHIVED.name().equals(status);
    }

    private Set<String> extractDependencyIds(Document project) {
        Object rawVersions = project.get("versions");
        if (!(rawVersions instanceof List<?> versions)) {
            return Set.of();
        }

        Set<String> dependencyIds = new LinkedHashSet<>();

        for (Object versionObj : versions) {
            if (!(versionObj instanceof Document versionDoc)) {
                continue;
            }

            Object rawDependencies = versionDoc.get("dependencies");
            if (!(rawDependencies instanceof List<?> dependencies)) {
                continue;
            }

            for (Object dependencyObj : dependencies) {
                if (!(dependencyObj instanceof Document dependencyDoc)) {
                    continue;
                }
                Object rawModId = dependencyDoc.get("modId");
                if (rawModId != null) {
                    dependencyIds.add(rawModId.toString());
                }
            }
        }

        return dependencyIds;
    }

    private void ensureClassificationCoverage(MongoCollection<Document> sourceProjectsCol, List<Document> compiledProjects) {
        Set<String> existingProjectIds = compiledProjects.stream()
                .map(doc -> doc.get("_id"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toSet());

        Set<String> includedClassifications = compiledProjects.stream()
                .map(doc -> doc.getString("classification"))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (ProjectClassification classification : ProjectClassification.values()) {
            if (includedClassifications.contains(classification.name())) {
                continue;
            }

            Document projectForClassification = sourceProjectsCol.aggregate(Arrays.asList(
                    Aggregates.match(Filters.and(
                            Filters.eq("classification", classification.name()),
                            Filters.in(
                                    "status",
                                    ProjectStatus.PUBLISHED.name(),
                                    ProjectStatus.ARCHIVED.name()
                            ),
                            Filters.eq("deletedAt", null)
                    )),
                    Aggregates.sample(1)
            )).first();

            if (projectForClassification == null) {
                logger.warn("No source project found with classification: {}", classification.name());
                continue;
            }

            Object id = projectForClassification.get("_id");
            String projectId = id != null ? id.toString() : null;
            if (projectId != null && existingProjectIds.add(projectId)) {
                compiledProjects.add(projectForClassification);
                includedClassifications.add(classification.name());
                logger.info("Guaranteed inclusion of project with classification: {}", classification.name());
            }
        }
    }
}
