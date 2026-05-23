package net.modtale.config.db;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import net.modtale.model.project.ProjectStatus;
import net.modtale.model.user.ApiKey;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(DataSeeder.class);

    private final MongoTemplate mongoTemplate;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seeding.enabled:false}")
    private boolean seedingEnabled;

    @Value("${app.seeding.source-db:modtale}")
    private String sourceDbName;

    private static final int PUBLISHED_PROJECT_LIMIT = 100;
    private static final int REPORT_LIMIT = 20;
    private static final String SUPER_ADMIN_ID = "692620f7c2f3266e23ac0ded";

    public DataSeeder(MongoTemplate mongoTemplate, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.mongoTemplate = mongoTemplate;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (!seedingEnabled) {
            return;
        }

        ensureSuperAdmin();
        ensureNormalUser();

        String currentDbName = mongoTemplate.getDb().getName();

        if (currentDbName.equalsIgnoreCase(sourceDbName)) {
            logger.warn("Seeding ABORTED: Current DB is same as Source DB ({})", currentDbName);
            return;
        }

        if (mongoTemplate.getCollection("projects").countDocuments() > 0) {
            logger.info("Database '{}' already contains projects. Skipping content clone.", currentDbName);
            return;
        }

        logger.info("Initializing Preview Environment...");

        try {
            logger.info("Attempting to clone relational subset from '{}'...", sourceDbName);

            MongoDatabase sourceDb = mongoTemplate.getMongoDatabaseFactory().getMongoDatabase(sourceDbName);
            MongoDatabase targetDb = mongoTemplate.getDb();

            long sourceProjectCount = sourceDb.getCollection("projects").countDocuments();
            if (sourceProjectCount == 0) {
                logger.warn("SOURCE DB '{}' IS EMPTY! Cannot clone data.", sourceDbName);
                return;
            }

            List<Document> compiledProjects = new ArrayList<>();

            MongoCollection<Document> sourceProjectsCol = sourceDb.getCollection("projects");

            List<Bson> publishedPipeline = Arrays.asList(
                    Aggregates.match(Filters.eq("status", ProjectStatus.PUBLISHED.name())),
                    Aggregates.sample(PUBLISHED_PROJECT_LIMIT)
            );
            sourceProjectsCol.aggregate(publishedPipeline).into(compiledProjects);
            logger.info("Fetched {} random PUBLISHED projects.", compiledProjects.size());

            for (ProjectStatus status : ProjectStatus.values()) {
                if (status == ProjectStatus.PUBLISHED) {
                    continue;
                }

                Document altProject = sourceProjectsCol.find(Filters.eq("status", status.name())).first();
                if (altProject != null) {
                    compiledProjects.add(altProject);
                    logger.info("Guaranteed inclusion of project with status: {}", status.name());
                } else {
                    logger.warn("No source project found with status: {}", status.name());
                }
            }

            if (compiledProjects.isEmpty()) {
                logger.info("No projects successfully retrieved from source. Seeding finished.");
                return;
            }

            Set<String> authorIds = compiledProjects.stream()
                    .map(doc -> doc.getString("authorId"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Set<ObjectId> projectIds = compiledProjects.stream()
                    .map(doc -> getSafeObjectId(doc.get("_id")))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            logger.info("Cloning referenced authors ({}) for compiled projects...", authorIds.size());
            cloneSpecificUsers(sourceDb, targetDb, authorIds);

            try {
                targetDb.getCollection("projects").insertMany(compiledProjects);
                logger.info("Cloned {} mixed-status projects to local database.", compiledProjects.size());
            } catch (Exception e) {
                logger.warn("Project insertion warning (duplicates might exist): {}", e.getMessage());
            }

            cloneProjectStats(sourceDb, targetDb, projectIds);
            cloneCollectionSubset(sourceDb, targetDb, "reports", REPORT_LIMIT);

            logger.info("Seeding completed successfully.");

        } catch (Exception e) {
            logger.error("Failed to seed preview database", e);
        }
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
        user.setEmail("admin@modtale.net");
        user.setEmailVerified(true);
        user.setPassword(passwordEncoder.encode("password"));
        user.setRoles(List.of("USER", "ADMIN"));
        user.setBio("I am the Super Admin for this preview environment.");
        user.setTier(ApiKey.Tier.ENTERPRISE);
        userRepository.save(user);
        logger.info("Created Super Admin: super_admin / password (ID: {})", SUPER_ADMIN_ID);
    }

    private void ensureNormalUser() {
        if (userRepository.findByUsername("user").isPresent()) return;

        User user = new User();
        user.setUsername("user");
        user.setEmail("user@modtale.net");
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
            List<String> stringIds = userIds.stream().collect(Collectors.toList());
            sourceCol.find(Filters.in("_id", stringIds)).into(usersToClone);
        }

        String defaultPasswordHash = passwordEncoder.encode("password");
        List<Document> safeToInsert = new ArrayList<>();

        for (Document user : usersToClone) {
            String id = user.get("_id").toString();
            String username = user.getString("username");

            if (id.equals(SUPER_ADMIN_ID) || "user".equals(username) || "super_admin".equals(username)) {
                continue;
            }

            user.put("password", defaultPasswordHash);
            user.put("email", "scrubbed_" + id + "@modtale.local");
            user.put("emailVerified", true);
            user.put("githubAccessToken", null);
            user.put("gitlabAccessToken", null);
            safeToInsert.add(user);
        }

        if (!safeToInsert.isEmpty()) {
            try {
                targetCol.insertMany(safeToInsert);
                logger.info("Cloned and sanitized {} users.", safeToInsert.size());
            } catch (Exception e) {
                logger.warn("Partial user insertion error: {}", e.getMessage());
            }
        }
    }

    private void cloneProjectStats(MongoDatabase source, MongoDatabase target, Set<ObjectId> projectIds) {
        if (projectIds.isEmpty()) return;

        List<Document> stats = new ArrayList<>();

        source.getCollection("project_monthly_stats")
                .find(Filters.in("projectId", projectIds))
                .into(stats);

        if (stats.isEmpty()) {
            List<String> stringIds = projectIds.stream().map(ObjectId::toString).collect(Collectors.toList());
            source.getCollection("project_monthly_stats")
                    .find(Filters.in("projectId", stringIds))
                    .into(stats);
        }

        if (!stats.isEmpty()) {
            try {
                target.getCollection("project_monthly_stats").insertMany(stats);
                logger.info("Cloned {} project statistics records.", stats.size());
            } catch (Exception e) {
                logger.warn("Stats insertion error: {}", e.getMessage());
            }
        }
    }

    private void cloneCollectionSubset(MongoDatabase source, MongoDatabase target, String collectionName, int limit) {
        try {
            List<Document> docs = fetchSubset(source, collectionName, limit);
            if (!docs.isEmpty()) {
                try {
                    target.getCollection(collectionName).insertMany(docs);
                    logger.info("Cloned {} documents from {}.", docs.size(), collectionName);
                } catch (Exception e) {
                    logger.warn("Insertion error for {}: {}", collectionName, e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.warn("Could not clone subset of {}", collectionName);
        }
    }
}