package net.modtale.service.finance;

import com.mongodb.client.*;
import java.util.*;
import java.util.concurrent.*;
import net.modtale.model.finance.ProviderCostEvidence;
import net.modtale.model.user.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named = "FINANCE_TEST_MONGO_URI", matches = ".+")
class ProviderCostEvidenceIntegrationTest {
    MongoClient client; MongoTemplate mongo; StripeGatewayService gateway; ProviderCostEvidenceService service; User reviewer;
    ProviderCostEvidenceService.ImportRequest request = new ProviderCostEvidenceService.ImportRequest("txn_fee", "acct_platform", true, "Actual fee evidence reviewed; no creator attribution.");
    @BeforeEach void setup() {
        client = MongoClients.create(System.getenv("FINANCE_TEST_MONGO_URI"));
        mongo = new MongoTemplate(new SimpleMongoClientDatabaseFactory(client, "cost_test_" + UUID.randomUUID().toString().replace("-", "")));
        mongo.createCollection(ProviderCostEvidence.class); gateway = mock(StripeGatewayService.class); service = new ProviderCostEvidenceService(gateway, mongo);
        reviewer = new User(); reviewer.setId("reviewer"); reviewer.setAdminPermissions(Set.of(AdminPermission.PLATFORM_FINANCE_MANAGE));
        when(gateway.isTestMode()).thenReturn(true); when(gateway.isReconciliationEnabled()).thenReturn(true); when(gateway.getExpectedPlatformAccountId()).thenReturn("acct_platform");
        when(gateway.verifyPlatformAccountId("acct_platform")).thenReturn(true); when(gateway.getBalance()).thenReturn(Map.of("object", "balance", "livemode", false));
        when(gateway.getBalanceTransaction("txn_fee")).thenReturn(StripeCostEvidenceTest.fee());
    }
    @AfterEach void cleanup() { if (mongo != null) mongo.getDb().drop(); if (client != null) client.close(); }
    @Test void replayPreservesFirstEvidenceAndConflictCannotOverwrite() {
        var first = service.retrieveAndImport(reviewer, request);
        var repeated = service.retrieveAndImport(reviewer, new ProviderCostEvidenceService.ImportRequest("txn_fee", "acct_platform", true, "A later observer sees the same evidence."));
        assertEquals(first, repeated); assertEquals(request.reason(), repeated.reason());
        var altered = StripeCostEvidenceTest.fee(); altered.put("amount", -201); altered.put("net", -201); when(gateway.getBalanceTransaction("txn_fee")).thenReturn(altered);
        assertThrows(IllegalArgumentException.class, () -> service.retrieveAndImport(reviewer, request));
        assertEquals(first, mongo.findById(first.id(), ProviderCostEvidence.class)); assertEquals(1, service.list(reviewer).size());
        assertEquals(0, mongo.getCollection("creator_wallets").countDocuments()); assertEquals(0, mongo.getCollection("finance_ledger_entries").countDocuments());
    }
    @Test void simultaneousImportsProduceOneImmutableUnallocatedCost() throws Exception {
        var gate = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = new ArrayList<Future<ProviderCostEvidence>>();
            for (int i = 0; i < 8; i++) futures.add(executor.submit(() -> { gate.await(); return service.retrieveAndImport(reviewer, request); }));
            gate.countDown(); var first = futures.getFirst().get(10, TimeUnit.SECONDS);
            for (var future : futures) assertEquals(first, future.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1, service.list(reviewer).size()); assertEquals("UNALLOCATED", service.list(reviewer).getFirst().allocationStatus());
        assertEquals(0, mongo.getCollection("creator_wallets").countDocuments()); assertEquals(0, mongo.getCollection("finance_ledger_entries").countDocuments());
    }
    @Test void testAndLiveEvidenceCannotAlias() {
        var test = service.retrieveAndImport(reviewer, request);
        when(gateway.isTestMode()).thenReturn(false); when(gateway.getBalance()).thenReturn(Map.of("object", "balance", "livemode", true));
        var live = service.retrieveAndImport(reviewer, new ProviderCostEvidenceService.ImportRequest("txn_fee", "acct_platform", false, "Different live scope fixture."));
        assertNotEquals(test.id(), live.id()); assertEquals(2, service.list(reviewer).size());
    }
}
