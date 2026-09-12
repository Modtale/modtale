package net.modtale.service.social;

import com.mongodb.client.*;
import java.util.*;
import java.util.concurrent.*;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="WARDEN_FAVORITE_DB_TEST", matches="true")
class FavoritePersistenceIntegrationTest {
    private MongoClient client;
    private MongoTemplate mongo;
    private FavoritePersistence favorites;
    private String database;
    @BeforeEach void setup() {
        String port = System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT", "27030");
        if (!Set.of("27029", "27030").contains(port)) throw new IllegalArgumentException("Unexpected test database port");
        client = MongoClients.create("mongodb://127.0.0.1:" + port + "/?serverSelectionTimeoutMS=3000");
        database = "warden_favorite_test_" + UUID.randomUUID().toString().replace("-", "");
        mongo = new MongoTemplate(client, database); favorites = new FavoritePersistence(mongo);
        assertNotNull(mongo.executeCommand(new Document("hello", 1)).get("setName"), "Tests require a replica set");
        mongo.getCollection("projects").insertOne(new Document("_id", "project").append("status", "PENDING")
                .append("favoriteCount", 0).append("versions", List.of(new Document("_id", "v1").append("findingReviewHead", "current")
                        .append("unknownEvidence", "retained"))));
        addUser("user");
    }
    @AfterEach void cleanup() { if (client != null) { client.getDatabase(database).drop(); client.close(); } }
    private void addUser(String id) { mongo.getCollection("users").insertOne(new Document("_id", id)
            .append("roles", List.of("USER")).append("likedModIds", List.of()).append("unknownAuthority", "retained")); }
    private Document user() { return mongo.getCollection("users").find(new Document("_id", "user")).first(); }
    private Document project() { return mongo.getCollection("projects").find(new Document("_id", "project")).first(); }

    @Test void toggleChangesOnlyFavoritesAndPreservesReviewAndAccountAuthority() {
        var versions = project().get("versions");
        mongo.getCollection("users").updateOne(new Document("_id", "user"), new Document("$set", new Document("roles", List.of("MODERATOR"))));
        assertEquals(1, favorites.toggle("project", "user"));
        assertEquals(List.of("project"), user().get("likedModIds"));
        assertEquals(List.of("MODERATOR"), user().get("roles")); assertEquals("retained", user().get("unknownAuthority"));
        assertEquals(versions, project().get("versions")); assertEquals("PENDING", project().get("status"));
        assertEquals(0, favorites.toggle("project", "user")); assertEquals(List.of(), user().get("likedModIds"));
        assertEquals(0, project().getInteger("favoriteCount"));
    }
    @Test void failedProjectWriteRollsBackTheUserUpdate() {
        mongo.executeCommand(new Document("collMod", "projects").append("validator",
                new Document("favoriteCount", new Document("$lte", 0))).append("validationAction", "error"));
        assertThrows(com.mongodb.MongoWriteException.class, () -> favorites.toggle("project", "user"));
        assertEquals(List.of(), user().get("likedModIds")); assertEquals(0, project().getInteger("favoriteCount"));
    }
    @Test void concurrentTogglesAndDifferentUsersKeepExactCounts() throws Exception {
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = new ArrayList<Future<Integer>>();
            for (int i = 0; i < 20; i++) futures.add(executor.submit(() -> favorites.toggle("project", "user")));
            for (var future : futures) future.get(60, TimeUnit.SECONDS);
            assertEquals(List.of(), user().get("likedModIds")); assertEquals(0, project().getInteger("favoriteCount"));
            futures.clear();
            for (int i = 0; i < 20; i++) { String id = "other" + i; addUser(id); futures.add(executor.submit(() -> favorites.toggle("project", id))); }
            for (var future : futures) future.get(60, TimeUnit.SECONDS);
            assertEquals(20, project().getInteger("favoriteCount"));
            assertEquals(20, mongo.getCollection("users").countDocuments(new Document("likedModIds", "project")));
        }
    }
    @Test void nullLikesAndObjectIdReferencesWorkWithoutCreatingDuplicateAccounts() {
        mongo.getCollection("users").updateOne(new Document("_id", "user"), new Document("$set", new Document("likedModIds", null)));
        assertEquals(1, favorites.toggle("project", "user"));
        var id = new ObjectId(); mongo.getCollection("projects").insertOne(new Document("_id", id).append("favoriteCount", 0));
        assertEquals(1, favorites.toggle(id.toHexString(), "user"));
        assertTrue(user().getList("likedModIds", String.class).contains(id.toHexString()));
        mongo.getCollection("projects").insertOne(new Document("_id", id.toHexString()).append("favoriteCount", 0));
        assertThrows(net.modtale.exception.ResourceNotFoundException.class, () -> favorites.toggle(id.toHexString(), "user"));
    }
    @Test void missingOrDeletedParticipantsCannotChangeEitherRecord() {
        assertThrows(net.modtale.exception.ResourceNotFoundException.class, () -> favorites.toggle("missing", "user"));
        assertThrows(net.modtale.exception.ResourceNotFoundException.class, () -> favorites.toggle("project", "missing"));
        mongo.getCollection("users").updateOne(new Document("_id", "user"), new Document("$set", new Document("deletedAt", "deleted")));
        assertThrows(net.modtale.exception.ResourceNotFoundException.class, () -> favorites.toggle("project", "user"));
        assertEquals(0, project().getInteger("favoriteCount")); assertEquals(List.of(), user().get("likedModIds"));
    }
}
