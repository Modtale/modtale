package net.modtale.config;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import org.bson.Document;
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

    private static final int PROJECT_LIMIT = 50;
    private static final int REPORT_LIMIT = 20;

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

        String currentDbName = mongoTemplate.getDb().getName();

        if (currentDbName.equalsIgnoreCase(sourceDbName)) {
            logger.warn("Seeding ABORTED: Current DB is same as Source DB ({})", currentDbName);
            return;
        }

        if (userRepository.count() > 0) {
            logger.info("Database '{}' already contains data. Skipping seed.", currentDbName);
            return;
        }

        logger.info("Initializing Preview Environment with relational subset from '{}'...", sourceDbName);

        try {
            MongoDatabase sourceDb = mongoTemplate.getMongoDatabaseFactory().getMongoDatabase(sourceDbName);
            MongoDatabase targetDb = mongoTemplate.getDb();

            createDevAdmin();

            List<Document> projects = fetchSubset(sourceDb, "projects", PROJECT_LIMIT);

            if (projects.isEmpty()) {
                logger.info("No projects found in source. Seeding finished early.");
                return;
            }

            Set<String> authorIds = projects.stream()
                    .map(doc -> doc.getString("authorId"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            Set<ObjectId> projectIds = projects.stream()
                    .map(doc -> doc.getObjectId("_id"))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            logger.info("Identified {} projects and {} unique authors to clone.", projects.size(), authorIds.size());

            cloneSpecificUsers(sourceDb, targetDb, authorIds);

            targetDb.getCollection("projects").insertMany(projects);
            logger.info("Cloned {} projects.", projects.size());

            cloneProjectStats(sourceDb, targetDb, projectIds);

            cloneCollectionSubset(sourceDb, targetDb, "reports", REPORT_LIMIT);

            logger.info("Seeding completed successfully.");

        } catch (Exception e) {
            logger.error("Failed to seed preview database", e);
        }
    }

    private void createDevAdmin() {
        User user = new User();
        user.setUsername("dev_admin");
        user.setEmail("admin@modtale.net");
        user.setPassword(passwordEncoder.encode("password"));
        user.setRoles(List.of("USER", "ADMIN"));
        user.setBio("I am the generated admin for this preview environment.");
        userRepository.save(user);
        logger.info("Created Default Admin: dev_admin / password");
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
                .map(id -> {
                    try { return new ObjectId(id); } catch (IllegalArgumentException e) { return null; }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        List<Document> usersToClone = new ArrayList<>();

        sourceCol.find(Filters.in("_id", objectIds)).into(usersToClone);

        String defaultPasswordHash = passwordEncoder.encode("password");
        for (Document user : usersToClone) {
            user.put("password", defaultPasswordHash);
            user.put("email", "scrubbed_" + user.getObjectId("_id").toString() + "@modtale.local");
            user.put("githubAccessToken", null);
            user.put("gitlabAccessToken", null);
        }

        if (!usersToClone.isEmpty()) {
            targetCol.insertMany(usersToClone);
            logger.info("Cloned and sanitized {} users.", usersToClone.size());
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
            target.getCollection("project_monthly_stats").insertMany(stats);
            logger.info("Cloned {} project statistics records.", stats.size());
        }
    }

    private void cloneCollectionSubset(MongoDatabase source, MongoDatabase target, String collectionName, int limit) {
        try {
            List<Document> docs = fetchSubset(source, collectionName, limit);
            if (!docs.isEmpty()) {
                target.getCollection(collectionName).insertMany(docs);
                logger.info("Cloned {} documents from {}.", docs.size(), collectionName);
            }
        } catch (Exception e) {
            logger.warn("Could not clone subset of {}", collectionName);
        }
    }
}