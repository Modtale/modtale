package net.modtale.service.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.modtale.model.dto.request.finance.StageAdSettlementRequest;
import net.modtale.model.finance.*;
import net.modtale.model.user.AdminPermission;
import net.modtale.model.user.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;

@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_MONGO_URI", matches = ".+")
class AdSettlementStagingIntegrationTest {
    private MongoClient client; private MongoTemplate mongo; private AdSettlementStagingService service;
    private User reviewer; private StageAdSettlementRequest report;
    @BeforeEach void setup() {
        client = MongoClients.create(System.getenv("FINANCE_TEST_MONGO_URI"));
        var factory = new SimpleMongoClientDatabaseFactory(client, "ad_stage_test_" + UUID.randomUUID().toString().replace("-", ""));
        mongo = new MongoTemplate(factory); mongo.createCollection(AdSettlementStage.class); mongo.createCollection(AdSettlementDepositClaim.class);
        var activity = mock(AdSettlementActivityService.class);
        when(activity.snapshot(any(), any())).thenReturn(List.of(
                new AdSettlementStage.Activity("a", "owner-a", "Alpha", 10, 5, 0, 100, 15, 0, "Provisional"),
                new AdSettlementStage.Activity("b", "owner-b", "Beta", 20, 10, 0, 100, 30, 0, "Provisional")));
        service = new AdSettlementStagingService(mongo, factory, activity);
        reviewer = new User(); reviewer.setId("reviewer"); reviewer.setAdminPermissions(Set.of(AdminPermission.PLATFORM_FINANCE_MANAGE));
        report = new StageAdSettlementRequest("provider", "account", "report-1", "deposit-1", "usd", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 10001, "a".repeat(64));
    }
    @AfterEach void cleanup() { if (mongo != null) mongo.getDb().drop(); if (client != null) client.close(); }
    @Test void stagingDeduplicatesSourcesAndNeverFundsAWallet() {
        var stage = service.create(reviewer, report); assertEquals(stage.id(), service.create(reviewer, report).id());
        var snapshot = stage.snapshots().getFirst();
        assertEquals(7501, snapshot.provisionalCreatorPoolCents()); assertEquals(2500, snapshot.provisionalPlatformCents());
        assertEquals(7501, snapshot.projects().stream().mapToLong(AdSettlementStage.Activity::provisionalCreatorCents).sum());
        assertThrows(IllegalArgumentException.class, () -> service.create(reviewer, changedReport("report-other", 10001)));
        assertThrows(IllegalArgumentException.class, () -> service.create(reviewer, changedReport("report-1", 9999)));
        assertEquals(0, mongo.getCollection("creator_wallets").countDocuments());
        assertEquals(0, mongo.getCollection("finance_ledger_entries").countDocuments());
        assertEquals(1, mongo.getCollection("ad_settlement_stages").countDocuments());
    }
    @Test void reviewedSnapshotsSurviveExplicitAmendmentAndReviewReplays() {
        var stage = service.create(reviewer, report);
        var review = new AdSettlementStagingService.Review(1, UUID.randomUUID().toString(), "REVIEWED_PROVISIONAL", "Inputs reviewed; provider verification still required.");
        stage = service.review(reviewer, stage.id(), review); service.review(reviewer, stage.id(), review);
        assertEquals(2, stage.audit().size());
        var amendment = new AdSettlementStagingService.Amendment(1, UUID.randomUUID().toString(), changedReport("report-1", 12000), "Provider report was corrected.");
        stage = service.amend(reviewer, stage.id(), amendment); service.amend(reviewer, stage.id(), amendment);
        assertEquals(2, stage.revision()); assertEquals(2, stage.snapshots().size());
        assertEquals(10001, stage.snapshots().getFirst().reportedCollectedCents());
        assertEquals(12000, stage.snapshots().getLast().reportedCollectedCents());
        assertEquals("AWAITING_REVIEW", stage.status()); assertEquals(3, stage.audit().size());
        final String id = stage.id();
        assertThrows(IllegalArgumentException.class, () -> service.review(reviewer, id, new AdSettlementStagingService.Review(2, UUID.randomUUID().toString(), "FUNDED", "Do not allow this.")));
        assertEquals(0, mongo.getCollection("creator_wallets").countDocuments());
    }
    private StageAdSettlementRequest changedReport(String id, long cents) {
        return new StageAdSettlementRequest(report.provider(), report.providerAccount(), id, report.depositId(), report.currency(), report.from(), report.through(), cents, report.reportSha256());
    }
}
