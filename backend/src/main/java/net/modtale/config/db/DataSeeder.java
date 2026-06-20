package net.modtale.config.db;

import com.mongodb.MongoBulkWriteException;
import com.mongodb.MongoException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import net.modtale.config.properties.AppR2Properties;
import net.modtale.config.properties.AppSeedingProperties;
import net.modtale.model.project.ProjectClassification;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.auth.ReservedAccountGuardService;
import net.modtale.service.storage.StorageService;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.*;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final ReservedAccountGuardService reservedAccountGuardService;
    private final StorageService storageService;
    private final AppR2Properties r2Properties;
    private final AppSeedingProperties seedingProperties;

    private static final int PUBLISHED_PROJECT_LIMIT = 100;
    private static final int MONGO_SEED_PROGRESS_INTERVAL = 25;
    private static final int R2_SEED_PROGRESS_INTERVAL = 25;
    private static final String R2_SEED_MARKER_PREFIX = ".modtale/seeding/r2-artifacts/";
    private static final Set<String> VERSION_ARTIFACT_FIELDS = Set.of("fileUrl", "cachedFileUrl", "artifactUrl");
    private static final Set<String> DEPENDENCY_ARTIFACT_FIELDS = Set.of("cachedFileUrl", "externalFileUrl", "fileUrl");
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
            StorageService storageService,
            AppR2Properties r2Properties,
            AppSeedingProperties seedingProperties
    ) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.reservedAccountGuardService = reservedAccountGuardService;
        this.storageService = storageService;
        this.r2Properties = r2Properties;
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
        logger.info(
                "DataSeeder enabled: mode={}, reset={}, targetDb={}, sourceDb={}, targetR2BucketConfigured={}, sourceR2BucketConfigured={}.",
                seedingProperties.mode(),
                seedingProperties.reset(),
                currentDbName,
                seedingProperties.sourceDb(),
                hasText(r2Properties.bucket()),
                hasText(seedingProperties.sourceR2Bucket())
        );

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
            logger.info("Database '{}' already contains projects. Skipping content clone and checking R2 artifacts.", currentDbName);
            seedR2ObjectsFromCurrentProjects(false);
            return;
        }

        logger.info("Initializing Preview Environment...");

        try {
            logger.info("Attempting to clone relational subset from '{}'...", seedingProperties.sourceDb());

            MongoDatabase sourceDb = mongoTemplate.getMongoDatabaseFactory().getMongoDatabase(seedingProperties.sourceDb());
            MongoDatabase targetDb = mongoTemplate.getDb();

            long sourceProjectCount = sourceDb.getCollection("projects").countDocuments();
            logger.info(
                    "Mongo clone seed progress: source database '{}' has {} project documents.",
                    seedingProperties.sourceDb(),
                    sourceProjectCount
            );
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
            logger.info(
                    "Mongo clone seed progress: sampling up to {} published/archived projects from '{}'.",
                    PUBLISHED_PROJECT_LIMIT,
                    seedingProperties.sourceDb()
            );
            sourceProjectsCol.aggregate(publishedPipeline).into(compiledProjects);
            logger.info("Mongo clone seed progress: fetched {} random public projects.", compiledProjects.size());

            ensureClassificationCoverage(sourceProjectsCol, compiledProjects);
            logger.info(
                    "Mongo clone seed progress: project set after classification coverage contains {} projects.",
                    compiledProjects.size()
            );

            Set<String> selectedProjectIds = projectIds(compiledProjects);

            List<Document> dependencyProjects = collectDependencyProjects(sourceProjectsCol, selectedProjectIds);
            if (!dependencyProjects.isEmpty()) {
                compiledProjects.addAll(dependencyProjects);
                selectedProjectIds = projectIds(compiledProjects);
                logger.info(
                        "Mongo clone seed progress: included {} dependency projects; project set now contains {} projects.",
                        dependencyProjects.size(),
                        compiledProjects.size()
                );
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
                logger.info("Mongo clone seed progress: sanitizing {} public projects before insert.", compiledProjects.size());
                compiledProjects.forEach(project -> stripPrivateProjectFields(project, finalSelectedProjectIds));
                logger.info("Mongo clone seed progress: inserting {} public projects into target database '{}'.", compiledProjects.size(), currentDbName);
                targetDb.getCollection("projects").insertMany(compiledProjects);
                logger.info("Mongo clone seed progress: cloned {} public projects to target database '{}'.", compiledProjects.size(), currentDbName);
            } catch (MongoBulkWriteException e) {
                logger.warn("Project insertion warning (duplicates might exist): {}", e.getMessage());
            }

            seedR2ObjectsForProjects(compiledProjects, false);
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
            seedR2ObjectsFromCurrentProjects(true);
            return;
        }

        logger.info(
                "Initializing '{}' with generated synthetic mock database documents (reset={}).",
                currentDbName,
                seedingProperties.reset()
        );

        try {
            if (seedingProperties.reset()) {
                resetMockCollections(targetDb, "mock");
            }

            int collectionIndex = 0;
            for (String collectionName : MOCK_COLLECTIONS) {
                collectionIndex++;
                logger.info(
                        "Mongo mock seed progress: preparing collection {}/{} '{}' with synthetic documents.",
                        collectionIndex,
                        MOCK_COLLECTIONS.size(),
                        collectionName
                );
                List<Document> documents = syntheticMockDocuments(collectionName);
                MongoCollection<Document> collection = targetDb.getCollection(collectionName);

                upsertDocumentsWithProgress(collection, collectionName, documents, "mock", collectionIndex, MOCK_COLLECTIONS.size());
                if ("projects".equals(collectionName)) {
                    seedR2ObjectsForProjects(documents, true);
                }
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
            seedR2ObjectsFromCurrentProjects(false);
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
                resetMockCollections(targetDb, "template");
            }

            int collectionIndex = 0;
            for (String collectionName : MOCK_COLLECTIONS) {
                collectionIndex++;
                int limit = templateLimit(collectionName);
                logger.info(
                        "Mongo template seed progress: fetching collection {}/{} '{}' from '{}' (limit={}).",
                        collectionIndex,
                        MOCK_COLLECTIONS.size(),
                        collectionName,
                        sourceDbName,
                        limit
                );
                List<Document> documents = fetchSubset(sourceDb, collectionName, limit);
                logger.info(
                        "Mongo template seed progress: fetched {} documents for collection '{}'.",
                        documents.size(),
                        collectionName
                );
                if ("projects".equals(collectionName)) {
                    documents = completeDependencyProjects(sourceDb.getCollection("projects"), documents, "template");
                }
                MongoCollection<Document> collection = targetDb.getCollection(collectionName);

                upsertDocumentsWithProgress(collection, collectionName, documents, "template", collectionIndex, MOCK_COLLECTIONS.size());
                if ("projects".equals(collectionName)) {
                    seedR2ObjectsForProjects(documents, false);
                }
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
                        "gameVersions", List.of("2026.03.11"), "fileUrl", seedFileKey(slug, classification, "1.0.0"),
                        "hash", "sha256:mock-" + slug, "downloadCount", downloads, "releaseDate", "2026-06-18",
                        "changelog", "Synthetic changelog.", "dependencies", List.of(), "incompatibleProjectIds", List.of(),
                        "channel", "RELEASE", "reviewStatus", "PENDING".equals(status) ? "PENDING" : "APPROVED"))
        );
    }

    private String seedFileKey(String slug, String classification, String versionNumber) {
        String safeSlug = sanitizeStorageName(slug == null || slug.isBlank() ? "mock-project" : slug);
        String safeVersion = sanitizeStorageName(versionNumber == null || versionNumber.isBlank() ? "latest" : versionNumber);
        return switch (parseClassification(classification)) {
            case MODPACK -> "modpacks/" + safeSlug + "-" + safeVersion + ".zip";
            case DATA -> "files/data/" + safeSlug + "-" + safeVersion + ".zip";
            case ART -> "files/art/" + safeSlug + "-" + safeVersion + ".hmasset";
            case SAVE -> "files/save/" + safeSlug + "-" + safeVersion + ".zip";
            case PLUGIN -> "files/plugin/" + safeSlug + "-" + safeVersion + ".jar";
        };
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
        int collectionIndex = 0;
        for (String collectionName : MOCK_COLLECTIONS) {
            collectionIndex++;
            MongoCollection<Document> collection = targetDb.getCollection(collectionName);
            logger.info(
                    "Mongo supplemental seed progress: preparing collection {}/{} '{}'.",
                    collectionIndex,
                    MOCK_COLLECTIONS.size(),
                    collectionName
            );
            List<Document> documents = syntheticMockDocuments(collectionName);

            upsertDocumentsWithProgress(collection, collectionName, documents, "supplemental", collectionIndex, MOCK_COLLECTIONS.size());
            if ("projects".equals(collectionName)) {
                seedR2ObjectsForProjects(documents, true);
            }
        }
    }

    private void resetMockCollections(MongoDatabase targetDb, String seedLabel) {
        logger.info(
                "Mongo {} seed reset progress: clearing {} collections before import.",
                seedLabel,
                MOCK_COLLECTIONS.size()
        );
        int collectionIndex = 0;
        for (String collectionName : MOCK_COLLECTIONS) {
            collectionIndex++;
            long deleted = targetDb.getCollection(collectionName).deleteMany(new Document()).getDeletedCount();
            logger.info(
                    "Mongo {} seed reset progress: cleared collection {}/{} '{}' (deleted={}).",
                    seedLabel,
                    collectionIndex,
                    MOCK_COLLECTIONS.size(),
                    collectionName,
                    deleted
            );
        }
    }

    private void upsertDocumentsWithProgress(
            MongoCollection<Document> collection,
            String collectionName,
            List<Document> documents,
            String seedLabel,
            int collectionIndex,
            int collectionTotal
    ) {
        int total = documents == null ? 0 : documents.size();
        logger.info(
                "Mongo {} seed progress: importing collection {}/{} '{}' (documents={}).",
                seedLabel,
                collectionIndex,
                collectionTotal,
                collectionName,
                total
        );
        if (total == 0) {
            logger.info(
                    "Mongo {} seed progress: completed collection {}/{} '{}' (documents=0, elapsedMs=0).",
                    seedLabel,
                    collectionIndex,
                    collectionTotal,
                    collectionName
            );
            return;
        }

        int processed = 0;
        long startedAt = System.nanoTime();
        for (Document document : documents) {
            upsertMockDocument(collection, document);
            processed++;
            if (processed == 1 || processed % MONGO_SEED_PROGRESS_INTERVAL == 0 || processed == total) {
                logger.info(
                        "Mongo {} seed progress: collection {}/{} '{}' processed={}/{}.",
                        seedLabel,
                        collectionIndex,
                        collectionTotal,
                        collectionName,
                        processed,
                        total
                );
            }
        }

        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        logger.info(
                "Mongo {} seed progress: completed collection {}/{} '{}' (documents={}, elapsedMs={}).",
                seedLabel,
                collectionIndex,
                collectionTotal,
                collectionName,
                total,
                elapsedMillis
        );
    }

    private void seedR2ObjectsFromCurrentProjects(boolean allowSyntheticFallback) {
        try {
            logger.info(
                    "R2 artifact seed discovery: loading current project versions from database '{}' (syntheticFallback={}).",
                    mongoTemplate.getDb().getName(),
                    allowSyntheticFallback
            );
            List<Document> projects = new ArrayList<>();
            mongoTemplate.getCollection("projects")
                    .find()
                    .projection(new Document("title", 1)
                            .append("slug", 1)
                            .append("classification", 1)
                            .append("versions", 1))
                    .into(projects);
            logger.info(
                    "Checking existing database projects for R2 artifact seeding (projects={}, syntheticFallback={}).",
                    projects.size(),
                    allowSyntheticFallback
            );
            seedR2ObjectsForProjects(projects, allowSyntheticFallback);
        } catch (MongoException e) {
            logger.warn("Could not read existing projects for R2 artifact seeding: {}", e.getMessage());
        }
    }

    private void seedR2ObjectsForProjects(List<Document> projects, boolean allowSyntheticFallback) {
        if (projects == null || projects.isEmpty()) {
            logger.info("No projects available for R2 artifact seeding.");
            return;
        }

        int versionCount = countVersionDocuments(projects);
        Map<String, R2SeedObject> objects = new LinkedHashMap<>();
        for (Document project : projects) {
            collectProjectR2Objects(project, objects);
        }
        long syntheticFallbackKeys = objects.values().stream()
                .filter(R2SeedObject::syntheticFallback)
                .count();
        logger.info(
                "R2 artifact seed discovery: projects={}, versions={}, objectKeys={}, syntheticFallbackKeys={}, sourceCopyCandidateKeys={}.",
                projects.size(),
                versionCount,
                objects.size(),
                syntheticFallbackKeys,
                objects.size() - syntheticFallbackKeys
        );

        if (objects.isEmpty()) {
            logger.info(
                    "No R2 artifact keys found across {} projects and {} versions. Skipping R2 artifact seeding.",
                    projects.size(),
                    versionCount
            );
            return;
        }

        if (!hasText(r2Properties.bucket())) {
            logger.info("Target R2 bucket is not configured. Skipping R2 artifact seeding for {} object keys.", objects.size());
            return;
        }

        String markerKey = r2SeedMarkerKey(objects.values());
        if (hasR2SeedMarker(markerKey, objects.size())) {
            return;
        }

        Optional<R2SourceConfig> sourceConfig = r2SourceConfig();
        if (sourceConfig.isEmpty() && !allowSyntheticFallback) {
            logger.info("Source R2 is not configured. Skipping R2 artifact seeding for {} object keys.", objects.size());
            return;
        }

        R2SourceConfig source = sourceConfig.orElse(null);
        logger.info(
                "R2 artifact seed progress: targetBucket='{}', sourceBucket='{}', objectKeys={}, syntheticFallbackKeys={}.",
                r2Properties.bucket(),
                source == null ? "" : source.bucket(),
                objects.size(),
                syntheticFallbackKeys
        );
        S3Client sourceClient = source == null ? null : sourceR2Client(source);
        logger.info(
                "Checking {} R2 artifact keys against target storage (sourceBucketConfigured={}, syntheticFallback={}).",
                objects.size(),
                source != null,
                allowSyntheticFallback
        );
        int uploaded = 0;
        int skipped = 0;
        int copied = 0;
        int generated = 0;
        int missingSource = 0;
        int failed = 0;
        int processed = 0;
        long startedAt = System.nanoTime();

        try {
            for (R2SeedObject object : objects.values()) {
                processed++;
                if (processed == 1 || (processed - 1) % R2_SEED_PROGRESS_INTERVAL == 0) {
                    logger.info("R2 artifact seed progress: starting {}/{} ({})", processed, objects.size(), object.key());
                }

                try {
                    if (storageService.exists(object.key())) {
                        skipped++;
                    } else {
                        R2ObjectBytes bytes = sourceClient == null
                                ? null
                                : readSourceR2Object(sourceClient, source, object.key());
                        if (bytes != null) {
                            storageService.uploadDirect(
                                    object.key(),
                                    bytes.bytes(),
                                    firstNonBlank(bytes.contentType(), contentType(object.key()))
                            );
                            uploaded++;
                            copied++;
                        } else if (allowSyntheticFallback || object.syntheticFallback()) {
                            storageService.uploadDirect(object.key(), fixtureBytes(object), contentType(object.key()));
                            uploaded++;
                            generated++;
                        } else {
                            missingSource++;
                            logger.warn("Source R2 object '{}' was not found. Target object was not seeded.", object.key());
                        }
                    }
                } catch (RuntimeException e) {
                    failed++;
                    logger.warn("Could not seed R2 artifact object '{}': {}", object.key(), e.getMessage());
                }

                if (processed % R2_SEED_PROGRESS_INTERVAL == 0 || processed == objects.size()) {
                    logger.info(
                            "R2 artifact seed progress: processed={}/{} uploaded={} copiedFromSource={} generatedSynthetic={} alreadyPresent={} missingSource={} failed={}.",
                            processed,
                            objects.size(),
                            uploaded,
                            copied,
                            generated,
                            skipped,
                            missingSource,
                            failed
                    );
                }
            }
        } finally {
            if (sourceClient != null) {
                sourceClient.close();
            }
        }

        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000L;
        logger.info(
                "R2 artifact seed finished: uploaded={}, copiedFromSource={}, generatedSynthetic={}, alreadyPresent={}, missingSource={}, failed={}, elapsedMs={}.",
                uploaded,
                copied,
                generated,
                skipped,
                missingSource,
                failed,
                elapsedMillis
        );

        if (missingSource == 0 && failed == 0) {
            writeR2SeedMarker(markerKey, objects.size(), versionCount, uploaded, copied, generated, skipped, elapsedMillis);
        } else {
            logger.info(
                    "R2 artifact seed marker was not written because missingSource={} and failed={}. The next startup will re-check R2 artifacts.",
                    missingSource,
                    failed
            );
        }
    }

    private boolean hasR2SeedMarker(String markerKey, int objectCount) {
        try {
            if (storageService.exists(markerKey)) {
                logger.info(
                        "R2 artifact seed marker exists for current artifact set (objects={}, marker={}). Skipping R2 artifact validation.",
                        objectCount,
                        markerKey
                );
                return true;
            }
            logger.info("R2 artifact seed marker not found for current artifact set (marker={}). Validating target objects.", markerKey);
        } catch (RuntimeException e) {
            logger.warn(
                    "Could not check R2 artifact seed marker '{}': {}. Falling back to object validation.",
                    markerKey,
                    e.getMessage()
            );
        }
        return false;
    }

    private void writeR2SeedMarker(
            String markerKey,
            int objectCount,
            int versionCount,
            int uploaded,
            int copied,
            int generated,
            int alreadyPresent,
            long elapsedMillis
    ) {
        try {
            storageService.uploadDirect(
                    markerKey,
                    r2SeedMarkerJson(markerKey, objectCount, versionCount, uploaded, copied, generated, alreadyPresent, elapsedMillis)
                            .getBytes(StandardCharsets.UTF_8),
                    "application/json"
            );
            logger.info("Wrote R2 artifact seed marker for current artifact set (objects={}, marker={}).", objectCount, markerKey);
        } catch (RuntimeException e) {
            logger.warn("Could not write R2 artifact seed marker '{}': {}", markerKey, e.getMessage());
        }
    }

    private String r2SeedMarkerKey(Collection<R2SeedObject> objects) {
        return R2_SEED_MARKER_PREFIX + r2SeedFingerprint(objects) + ".json";
    }

    private String r2SeedFingerprint(Collection<R2SeedObject> objects) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            objects.stream()
                    .map(R2SeedObject::key)
                    .filter(Objects::nonNull)
                    .sorted()
                    .forEach(key -> {
                        digest.update(key.getBytes(StandardCharsets.UTF_8));
                        digest.update((byte) '\n');
                    });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available for R2 seed fingerprinting.", e);
        }
    }

    private String r2SeedMarkerJson(
            String markerKey,
            int objectCount,
            int versionCount,
            int uploaded,
            int copied,
            int generated,
            int alreadyPresent,
            long elapsedMillis
    ) {
        String fingerprint = markerKey
                .substring(R2_SEED_MARKER_PREFIX.length(), markerKey.length() - ".json".length());
        return "{\n"
                + "  \"type\": \"modtale-r2-artifact-seed\",\n"
                + "  \"fingerprint\": \"" + fingerprint + "\",\n"
                + "  \"objectCount\": " + objectCount + ",\n"
                + "  \"versionCount\": " + versionCount + ",\n"
                + "  \"uploaded\": " + uploaded + ",\n"
                + "  \"copiedFromSource\": " + copied + ",\n"
                + "  \"generatedSynthetic\": " + generated + ",\n"
                + "  \"alreadyPresent\": " + alreadyPresent + ",\n"
                + "  \"elapsedMs\": " + elapsedMillis + ",\n"
                + "  \"createdAt\": \"" + Instant.now() + "\"\n"
                + "}\n";
    }

    private int countVersionDocuments(List<Document> projects) {
        int count = 0;
        for (Document project : projects) {
            Object rawVersions = project.get("versions");
            if (rawVersions instanceof List<?> versions) {
                count += versions.size();
            }
        }
        return count;
    }

    private Optional<R2SourceConfig> r2SourceConfig() {
        String bucket = trimToNull(seedingProperties.sourceR2Bucket());
        if (bucket == null) {
            return Optional.empty();
        }

        String accessKey = trimToNull(seedingProperties.sourceR2AccessKey());
        String secretKey = trimToNull(seedingProperties.sourceR2SecretKey());
        String endpoint = trimToNull(seedingProperties.sourceR2Endpoint());
        if (accessKey == null || secretKey == null || endpoint == null) {
            logger.warn(
                    "Source R2 bucket '{}' is configured, but source endpoint/access/secret is incomplete. Skipping source R2 copy.",
                    bucket
            );
            return Optional.empty();
        }

        return Optional.of(new R2SourceConfig(bucket, accessKey, secretKey, endpoint));
    }

    private S3Client sourceR2Client(R2SourceConfig source) {
        URI endpoint = cleanEndpoint(source.endpoint());
        return S3Client.builder()
                .endpointOverride(endpoint)
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(source.accessKey(), source.secretKey())
                ))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    private URI cleanEndpoint(String endpoint) {
        URI uri = URI.create(endpoint);
        return URI.create(uri.getScheme() + "://" + uri.getAuthority());
    }

    private R2ObjectBytes readSourceR2Object(S3Client sourceClient, R2SourceConfig source, String key) {
        try {
            ResponseBytes<GetObjectResponse> response = sourceClient.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(source.bucket())
                    .key(key)
                    .build());
            return new R2ObjectBytes(response.asByteArray(), response.response().contentType());
        } catch (NoSuchKeyException e) {
            return null;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return null;
            }
            throw e;
        }
    }

    private void collectProjectR2Objects(Document project, Map<String, R2SeedObject> objects) {
        ProjectClassification classification = parseClassification(project.get("classification"));
        String projectName = firstNonBlank(
                stringValue(project.get("slug")),
                stringValue(project.get("title")),
                "project"
        );

        Object rawVersions = project.get("versions");
        if (!(rawVersions instanceof List<?> versions)) {
            return;
        }

        for (Object versionObj : versions) {
            if (!(versionObj instanceof Document versionDoc)) {
                continue;
            }

            String versionNumber = firstNonBlank(stringValue(versionDoc.get("versionNumber")), "latest");
            collectFieldR2Objects(versionDoc, VERSION_ARTIFACT_FIELDS, objects, projectName, versionNumber, classification);
            collectDependencyR2Objects(versionDoc, objects);
        }
    }

    private void collectDependencyR2Objects(Document versionDoc, Map<String, R2SeedObject> objects) {
        Object rawDependencies = versionDoc.get("dependencies");
        if (!(rawDependencies instanceof List<?> dependencies)) {
            return;
        }

        for (Object dependencyObj : dependencies) {
            if (!(dependencyObj instanceof Document dependencyDoc)) {
                continue;
            }

            String title = firstNonBlank(
                    stringValue(dependencyDoc.get("projectTitle")),
                    stringValue(dependencyDoc.get("modTitle")),
                    stringValue(dependencyDoc.get("title")),
                    stringValue(dependencyDoc.get("projectId")),
                    stringValue(dependencyDoc.get("modId")),
                    stringValue(dependencyDoc.get("externalId")),
                    "dependency"
            );
            String versionNumber = firstNonBlank(stringValue(dependencyDoc.get("versionNumber")), "latest");
            collectFieldR2Objects(dependencyDoc, DEPENDENCY_ARTIFACT_FIELDS, objects, title, versionNumber, ProjectClassification.PLUGIN);
        }
    }

    private void collectFieldR2Objects(
            Document document,
            Set<String> fieldNames,
            Map<String, R2SeedObject> objects,
            String projectName,
            String versionNumber,
            ProjectClassification classification
    ) {
        for (String fieldName : fieldNames) {
            addR2SeedObject(objects, stringValue(document.get(fieldName)), projectName, versionNumber, classification);
        }
    }

    private void addR2SeedObject(
            Map<String, R2SeedObject> objects,
            String rawLocation,
            String projectName,
            String versionNumber,
            ProjectClassification classification
    ) {
        R2SeedLocation location = r2SeedLocation(rawLocation);
        if (location == null) {
            return;
        }

        String key = location.key();
        objects.putIfAbsent(
                key,
                new R2SeedObject(
                        key,
                        firstNonBlank(projectName, filenameStem(key), "project"),
                        firstNonBlank(versionNumber, "latest"),
                        classification == null ? ProjectClassification.PLUGIN : classification,
                        location.syntheticFallback()
                )
        );
    }

    private R2SeedLocation r2SeedLocation(String rawLocation) {
        String value = trimToNull(rawLocation);
        if (value == null) {
            return null;
        }

        if (value.startsWith("/api/files/proxy/")) {
            return r2SeedLocationFromKey(value.substring("/api/files/proxy/".length()), false);
        }

        URI uri = parseUri(value);
        if (uri != null && uri.getScheme() != null) {
            String scheme = uri.getScheme().toLowerCase(Locale.ROOT);
            if (!"http".equals(scheme) && !"https".equals(scheme)) {
                return null;
            }

            String key = stripLeadingSlash(uri.getPath());
            if (key.isBlank()) {
                return null;
            }

            if (key.startsWith("api/files/proxy/")) {
                return r2SeedLocationFromKey(key.substring("api/files/proxy/".length()), false);
            }

            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if ("example.test".equals(host) && key.startsWith("mock-downloads/")) {
                return r2SeedLocationFromKey(key, true);
            }

            if (isFirstPartyStorageHost(host)) {
                return r2SeedLocationFromKey(key, false);
            }

            return null;
        }

        return r2SeedLocationFromKey(value, false);
    }

    private R2SeedLocation r2SeedLocationFromKey(String key, boolean syntheticFallback) {
        String normalized = normalizeObjectKey(key);
        if (normalized == null) {
            return null;
        }
        return new R2SeedLocation(normalized, syntheticFallback);
    }

    private boolean isFirstPartyStorageHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        if ("cdn.modtale.net".equals(host) || host.endsWith(".r2.dev")) {
            return true;
        }

        String publicDomainHost = hostFromUrl(r2Properties.publicDomain());
        return publicDomainHost != null && host.equals(publicDomainHost);
    }

    private String hostFromUrl(String rawUrl) {
        String value = trimToNull(rawUrl);
        if (value == null) {
            return null;
        }

        URI uri = parseUri(value);
        return uri == null || uri.getHost() == null ? null : uri.getHost().toLowerCase(Locale.ROOT);
    }

    private URI parseUri(String value) {
        try {
            return URI.create(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private String normalizeObjectKey(String key) {
        String value = trimToNull(key);
        if (value == null) {
            return null;
        }

        int query = value.indexOf('?');
        if (query >= 0) {
            value = value.substring(0, query);
        }
        int fragment = value.indexOf('#');
        if (fragment >= 0) {
            value = value.substring(0, fragment);
        }

        value = stripLeadingSlash(value);
        return value.isBlank() ? null : value;
    }

    private String stripLeadingSlash(String value) {
        String stripped = value == null ? "" : value.trim();
        while (stripped.startsWith("/")) {
            stripped = stripped.substring(1);
        }
        return stripped;
    }

    private byte[] fixtureBytes(R2SeedObject object) {
        String extension = extension(object.key());
        if (object.classification() == ProjectClassification.MODPACK || "zip".equals(extension)) {
            return zipFixtureBytes(object);
        }
        if ("jar".equals(extension)) {
            return jarFixtureBytes(object);
        }

        return ("Modtale fixture artifact\n"
                + "Project: " + object.projectName() + "\n"
                + "Version: " + object.versionNumber() + "\n"
                + "Key: " + object.key() + "\n").getBytes(StandardCharsets.UTF_8);
    }

    private byte[] zipFixtureBytes(R2SeedObject object) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
                writeZipEntry(zip, "README.txt", ("Modtale fixture archive for "
                        + object.projectName()
                        + " "
                        + object.versionNumber()
                        + "\n").getBytes(StandardCharsets.UTF_8));

                if (object.classification() == ProjectClassification.MODPACK) {
                    writeZipEntry(
                            zip,
                            sanitizeStorageName(object.projectName()) + "-" + sanitizeStorageName(object.versionNumber()) + ".jar",
                            jarFixtureBytes(object)
                    );
                }
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build fixture ZIP.", e);
        }
    }

    private byte[] jarFixtureBytes(R2SeedObject object) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ZipOutputStream jar = new ZipOutputStream(bytes)) {
                writeZipEntry(jar, "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nCreated-By: Modtale DataSeeder\n\n"
                        .getBytes(StandardCharsets.UTF_8));
                writeZipEntry(jar, "modtale-fixture.txt", ("Fixture mod artifact for "
                        + object.projectName()
                        + " "
                        + object.versionNumber()
                        + "\n").getBytes(StandardCharsets.UTF_8));
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not build fixture JAR.", e);
        }
    }

    private void writeZipEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes == null ? new byte[0] : bytes);
        zip.closeEntry();
    }

    private String contentType(String key) {
        return switch (extension(key)) {
            case "jar" -> "application/java-archive";
            case "zip" -> "application/zip";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "svg" -> "image/svg+xml";
            default -> "application/octet-stream";
        };
    }

    private ProjectClassification parseClassification(Object value) {
        if (value == null) {
            return ProjectClassification.PLUGIN;
        }

        try {
            return ProjectClassification.valueOf(value.toString().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ProjectClassification.PLUGIN;
        }
    }

    private String extension(String key) {
        String filename = filenameStem(key);
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private String filenameStem(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        String trimmed = key.trim();
        int query = trimmed.indexOf('?');
        if (query >= 0) {
            trimmed = trimmed.substring(0, query);
        }
        int slash = trimmed.lastIndexOf('/');
        return slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
    }

    private String sanitizeStorageName(String value) {
        String sanitized = firstNonBlank(value, "fixture")
                .replaceAll("[^A-Za-z0-9._-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("(^-|-$)", "");
        return sanitized.isBlank() ? "fixture" : sanitized;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record R2SeedLocation(String key, boolean syntheticFallback) {
    }

    private record R2SeedObject(
            String key,
            String projectName,
            String versionNumber,
            ProjectClassification classification,
            boolean syntheticFallback
    ) {
    }

    private record R2SourceConfig(String bucket, String accessKey, String secretKey, String endpoint) {
    }

    private record R2ObjectBytes(byte[] bytes, String contentType) {
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
        if (userIds.isEmpty()) {
            logger.info("Mongo clone seed progress: no referenced authors to clone.");
            return;
        }

        MongoCollection<Document> sourceCol = source.getCollection("users");
        MongoCollection<Document> targetCol = target.getCollection("users");

        List<ObjectId> objectIds = userIds.stream()
                .map(this::getSafeObjectId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (objectIds.isEmpty()) {
            logger.info(
                    "Mongo clone seed progress: no ObjectId-shaped author IDs found among {} referenced authors.",
                    userIds.size()
            );
            return;
        }

        List<Document> usersToClone = new ArrayList<>();
        logger.info(
                "Mongo clone seed progress: fetching {} referenced author documents from source users collection.",
                objectIds.size()
        );
        sourceCol.find(Filters.in("_id", objectIds)).into(usersToClone);

        if (usersToClone.isEmpty()) {
            List<String> stringIds = new ArrayList<>(userIds);
            logger.info(
                    "Mongo clone seed progress: no ObjectId author matches found; retrying {} string author IDs.",
                    stringIds.size()
            );
            sourceCol.find(Filters.in("_id", stringIds)).into(usersToClone);
        }

        logger.info(
                "Mongo clone seed progress: fetched {} referenced author documents; sanitizing before insert.",
                usersToClone.size()
        );
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
                logger.info(
                        "Mongo clone seed progress: inserting {} sanitized referenced author documents.",
                        safeToInsert.size()
                );
                targetCol.insertMany(safeToInsert);
                logger.info("Mongo clone seed progress: cloned and sanitized {} referenced author users.", safeToInsert.size());
            } catch (MongoBulkWriteException e) {
                logger.warn("Partial user insertion error: {}", e.getMessage());
            }
        } else {
            logger.info("Mongo clone seed progress: no referenced author documents remained after sanitization.");
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

    private List<Document> completeDependencyProjects(
            MongoCollection<Document> sourceProjectsCol,
            List<Document> projects,
            String seedLabel
    ) {
        if (projects == null || projects.isEmpty()) {
            return projects == null ? List.of() : projects;
        }

        Set<String> selectedProjectIds = projectIds(projects);
        List<Document> dependencyProjects = collectDependencyProjects(sourceProjectsCol, selectedProjectIds);
        if (dependencyProjects.isEmpty()) {
            logger.info(
                    "Mongo {} seed progress: no additional dependency projects needed for {} selected projects.",
                    seedLabel,
                    projects.size()
            );
            return projects;
        }

        List<Document> completed = new ArrayList<>(projects.size() + dependencyProjects.size());
        completed.addAll(projects);
        completed.addAll(dependencyProjects);
        logger.info(
                "Mongo {} seed progress: included {} dependency projects; project set now contains {} projects.",
                seedLabel,
                dependencyProjects.size(),
                completed.size()
        );
        return completed;
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
                Object rawSource = dependencyDoc.get("source");
                if (rawSource != null && !"MODTALE".equalsIgnoreCase(rawSource.toString())) {
                    continue;
                }
                Object rawProjectId = dependencyDoc.get("projectId");
                if (rawProjectId == null) {
                    rawProjectId = dependencyDoc.get("modId");
                }
                if (rawProjectId != null) {
                    dependencyIds.add(rawProjectId.toString());
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
