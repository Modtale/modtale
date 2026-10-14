package net.modtale.service.project.version;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.util.List;
import java.util.UUID;
import net.modtale.model.project.ProjectVersion;
import net.modtale.service.admin.review.ProjectReviewPersistence;
import net.modtale.service.project.access.ProjectAccessService;
import net.modtale.service.project.access.ProjectMutationGuard;
import net.modtale.service.project.access.ProjectVersionAccessService;
import net.modtale.service.project.lifecycle.ProjectDeletionService;
import net.modtale.service.project.query.ProjectService;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

@EnabledIfEnvironmentVariable(named = "WARDEN_REVIEW_DB_TEST", matches = "true")
class PublicHashLookupIntegrationTest {
    private MongoClient client;
    private MongoTemplate mongo;
    private VersionService versions;

    @BeforeEach
    void setUp() {
        int port = Integer.parseInt(System.getenv().getOrDefault("WARDEN_REVIEW_DB_PORT", "27029"));
        client = MongoClients.create("mongodb://127.0.0.1:" + port + "/?serverSelectionTimeoutMS=3000");
        mongo = new MongoTemplate(client, "warden_public_hash_" + UUID.randomUUID().toString().replace("-", ""));
        versions = new VersionService(
                mock(ProjectReviewPersistence.class), mock(ProjectService.class),
                mock(ProjectAccessService.class), mock(ProjectMutationGuard.class),
                mock(ProjectVersionAccessService.class), mongo, mock(VersionManifestService.class),
                mock(ProjectDeletionService.class), mock(VersionCreationCommandHandler.class),
                mock(VersionUpdateCommandHandler.class));
    }

    @AfterEach
    void tearDown() {
        if (mongo != null) mongo.getDb().drop();
        if (client != null) client.close();
    }

    @Test
    void globalHashLookupReturnsOnlyAnApprovedVersionFromAPublicProject() {
        String target = "a".repeat(64);
        String other = "b".repeat(64);
        mongo.getDb().getCollection("projects").insertMany(List.of(
                project("pending", "PUBLISHED", version("pending", target, "PENDING"), version("approved-sibling", other, "APPROVED")),
                project("private", "PRIVATE", version("private", target, "APPROVED")),
                project("withdrawn", "PUBLISHED", version("withdrawn", target, "REJECTED")),
                project("deleted", "PUBLISHED", version("deleted", target, "APPROVED"))
                        .append("deletedAt", new java.util.Date())));

        assertTrue(versions.getVersionByHash(target).isEmpty());

        mongo.getDb().getCollection("projects").insertOne(
                project("public", "UNLISTED", version("public-pending", other, "PENDING"), version("public-approved", target, "APPROVED")));
        ProjectVersion visible = versions.getVersionByHash(target).orElseThrow();
        assertEquals("public-approved", visible.getId());

        mongo.getDb().getCollection("projects").updateOne(new Document("_id", "public"),
                new Document("$set", new Document("versions.1.reviewStatus", "REJECTED")));
        assertTrue(versions.getVersionByHash(target).isEmpty());
    }

    private static Document project(String id, String status, Document... versions) {
        return new Document("_id", id).append("status", status).append("versions", List.of(versions));
    }

    private static Document version(String id, String hash, String reviewStatus) {
        return new Document("_id", id).append("id", id).append("hash", hash)
                .append("reviewStatus", reviewStatus).append("versionNumber", "1.0.0");
    }
}
