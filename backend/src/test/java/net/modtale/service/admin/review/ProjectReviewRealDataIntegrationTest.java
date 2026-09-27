package net.modtale.service.admin.review;

import com.mongodb.client.MongoClients;
import java.util.List;
import java.util.ArrayList;
import java.util.Collections;
import net.modtale.config.properties.AppSecurityProperties;
import net.modtale.model.project.Project;
import net.modtale.model.project.ProjectVersion;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.analytics.ScoringService;
import net.modtale.service.project.access.ProjectVersionAccessService;
import net.modtale.service.project.lifecycle.LifecycleService;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.project.validation.ValidationService;
import net.modtale.service.security.issue.SecurityIssueAnalysisService;
import net.modtale.service.security.issue.SecurityIssueApprovalService;
import net.modtale.service.security.issue.SecurityIssueClassificationService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class ProjectReviewRealDataIntegrationTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "CODEX_PERF_MONGO_URI", matches = ".+")
    void approvalUpdatesOnlyTheChosenVersionInAnIsolatedDatabase() {
        String uri = System.getenv("CODEX_PERF_MONGO_URI");
        assertTrue(uri.startsWith("mongodb://127.0.0.1:"), "This test may write only to a local MongoDB instance.");
        try (var client = MongoClients.create(uri)) {
            var collection = client.getDatabase("codex_perf").getCollection("projects");
            Document source = null;
            String versionId = null;
            for (Document candidate : collection.find(new Document("versions.reviewStatus", "PENDING"))) {
                for (Document version : candidate.getList("versions", Document.class)) {
                    if ("PENDING".equals(version.getString("reviewStatus"))) {
                        if (source == null || candidate.getList("versions", Document.class).size()
                                > source.getList("versions", Document.class).size()) {
                            source = candidate;
                            versionId = version.getString("_id");
                        }
                        break;
                    }
                }
            }
            assertNotNull(source);
            assertNotNull(versionId);
            String projectId = source.getString("_id");
            List<Document> originalVersions = source.getList("versions", Document.class);

            MongoTemplate mongoTemplate = new MongoTemplate(client, "codex_perf");
            long queueStarted = System.nanoTime();
            var queue = new ProjectReviewQueueService(mongoTemplate).getVerificationQueue();
            long queueElapsedMs = (System.nanoTime() - queueStarted) / 1_000_000;
            assertTrue(queue.size() > 0);
            System.err.println("Isolated queue on real data: " + queueElapsedMs + " ms for " + queue.size() + " projects");
            List<Long> queueTimes = new ArrayList<>();
            for (int iteration = 0; iteration < 20; iteration++) {
                long queueRunStarted = System.nanoTime();
                new ProjectReviewQueueService(mongoTemplate).getVerificationQueue();
                queueTimes.add((System.nanoTime() - queueRunStarted) / 1_000_000);
            }
            Collections.sort(queueTimes);
            System.err.println("Isolated queue warm median/p95: " + queueTimes.get(10) + "/" + queueTimes.get(19) + " ms");
            var queryService = new ProjectReviewQueryService(mock(UserRepository.class),
                    mock(ProjectService.class), new ProjectReviewQueueService(mongoTemplate), mongoTemplate);
            queryService.getVerificationQueue();
            List<Long> cachedQueueTimes = new ArrayList<>();
            for (int iteration = 0; iteration < 1_000; iteration++) {
                long cachedReadStarted = System.nanoTime();
                queryService.getVerificationQueue();
                cachedQueueTimes.add(System.nanoTime() - cachedReadStarted);
            }
            Collections.sort(cachedQueueTimes);
            System.err.println("Isolated cached queue median/p95: "
                    + cachedQueueTimes.get(500) / 1_000 + "/" + cachedQueueTimes.get(950) / 1_000 + " microseconds");
            var classification = new SecurityIssueClassificationService(mock(AppSecurityProperties.class));
            var analysis = new SecurityIssueAnalysisService(classification,
                    new SecurityIssueApprovalService(classification));
            var transitions = new ProjectReviewTransitionService(
                    mock(ProjectRepository.class), mock(ProjectService.class), mock(LifecycleService.class),
                    mock(ScoringService.class), analysis,
                    new ProjectVersionAccessService(mock(ValidationService.class)), mongoTemplate);

            long started = System.nanoTime();
            var decision = transitions.approveVersion(projectId, versionId);
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            assertTrue(decision.changed());
            assertEquals(ProjectVersion.ReviewStatus.APPROVED, decision.version().getReviewStatus());

            Document updated = collection.find(new Document("_id", projectId)).first();
            assertNotNull(updated);
            List<Document> updatedVersions = updated.getList("versions", Document.class);
            assertEquals(originalVersions.size(), updatedVersions.size());
            for (int index = 0; index < originalVersions.size(); index++) {
                Document original = originalVersions.get(index);
                Document current = updatedVersions.get(index);
                if (versionId.equals(original.getString("_id"))) {
                    assertEquals("APPROVED", current.getString("reviewStatus"));
                } else if (!"APPROVED".equals(original.getString("reviewStatus"))
                        || original.get("scanResult") == null) {
                    assertEquals(original, current);
                }
            }
            System.err.println("Isolated approval on real queue data: " + elapsedMs + " ms");
            List<Long> approvalTimes = new ArrayList<>();
            for (int iteration = 0; iteration < 20; iteration++) {
                collection.replaceOne(new Document("_id", projectId), source);
                long approvalRunStarted = System.nanoTime();
                assertTrue(transitions.approveVersion(projectId, versionId).changed());
                approvalTimes.add((System.nanoTime() - approvalRunStarted) / 1_000_000);
            }
            Collections.sort(approvalTimes);
            System.err.println("Isolated approval warm median/p95: " + approvalTimes.get(10) + "/" + approvalTimes.get(19) + " ms");
        }
    }
}
