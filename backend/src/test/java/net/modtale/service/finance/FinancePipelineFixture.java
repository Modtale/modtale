package net.modtale.service.finance;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.modtale.controller.finance.StripeWebhookController;
import net.modtale.model.finance.*;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.finance.*;
import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.repository.support.MongoRepositoryFactory;
import org.springframework.test.util.ReflectionTestUtils;

/** Disposable persistence and actual finance services; only external provider/project lookup are fixtures. */
abstract class FinancePipelineFixture {
    MongoClient client; MongoTemplate mongo; FinanceWalletService wallets; StripeGatewayService gateway;
    DonationCheckoutService donations; RecurringSupportService recurring; PaymentSettlementService settlement;
    PaymentAdjustmentService adjustments; StripeWebhookController webhook; DonationIntentRepository intents;
    FinanceLedgerEntryRepository ledger; CreatorSupportSubscriptionRepository subscriptions;
    Project project; User donor;
    static final String SECRET = "whsec_synthetic_pipeline_only";
    @BeforeEach void setUpPipeline() {
        client = MongoClients.create(System.getenv("FINANCE_TEST_MONGO_URI"));
        var factory = new SimpleMongoClientDatabaseFactory(client, "finance_pipeline_" + UUID.randomUUID().toString().replace("-", ""));
        mongo = new MongoTemplate(factory);
        for (Class<?> type : List.of(CreatorWallet.class, CreatorPayoutRequest.class, FinanceLedgerEntry.class,
                DonationIntent.class, CreatorSupportSubscription.class, PaymentWebhookReceipt.class, FinanceDisputeCase.class)) mongo.createCollection(type);
        var repositories = new MongoRepositoryFactory(mongo);
        intents = repositories.getRepository(DonationIntentRepository.class); ledger = repositories.getRepository(FinanceLedgerEntryRepository.class);
        subscriptions = repositories.getRepository(CreatorSupportSubscriptionRepository.class);
        gateway = mock(StripeGatewayService.class);
        when(gateway.isOperational()).thenReturn(true); when(gateway.isCheckoutAvailable()).thenReturn(true);
        when(gateway.isReconciliationEnabled()).thenReturn(true); when(gateway.isTestMode()).thenReturn(true);
        when(gateway.getPlatformAccountId()).thenReturn("acct_platform");
        when(gateway.createOrSimulateDonationCheckout(anyString(), anyString(), anyLong(), anyBoolean(), anyString(), anyString(), anyString(), eq(false)))
                .thenAnswer(call -> new StripeGatewayService.StripeResult(true, "cs_" + call.getArgument(0), "https://checkout.stripe.com/c/pay/synthetic", null, Map.of()));
        var projects = mock(ProjectService.class); project = new Project(); project.setId("project"); project.setAuthorId("creator");
        project.setTitle("Synthetic project"); project.setDonationsEnabled(true); project.setDonationPlatformCutBps(1234);
        when(projects.getProjectById("project")).thenReturn(project); when(projects.getProjectLink(project)).thenReturn("/mod/synthetic");
        var accounts = mock(EarningsAccountService.class); when(accounts.getSettings()).thenReturn(new PlatformFinanceSettings());
        var core = mock(RevenueOpsSupport.class); when(core.normalizeFrontendUrl()).thenReturn("https://example.test");
        wallets = new FinanceWalletService(mongo, factory); adjustments = new PaymentAdjustmentService(mongo, gateway, wallets);
        recurring = new RecurringSupportService(subscriptions, intents, ledger, gateway, core, projects);
        donations = new DonationCheckoutService();
        ReflectionTestUtils.setField(donations, "financeAccountService", accounts); ReflectionTestUtils.setField(donations, "donationIntentRepository", intents);
        ReflectionTestUtils.setField(donations, "ledgerRepository", ledger); ReflectionTestUtils.setField(donations, "projectService", projects);
        ReflectionTestUtils.setField(donations, "stripeGatewayService", gateway); ReflectionTestUtils.setField(donations, "core", core);
        ReflectionTestUtils.setField(donations, "recurringSupport", recurring);
        settlement = new PaymentSettlementService(ledger, gateway, wallets, mongo, adjustments);
        webhook = new StripeWebhookController(donations, repositories.getRepository(PaymentWebhookReceiptRepository.class), gateway, recurring, adjustments, SECRET);
        donor = new User(); donor.setId("donor");
    }
    @AfterEach void closePipeline() { if (mongo != null) mongo.getDb().drop(); if (client != null) client.close(); }
    DonationIntent checkout(boolean monthly) {
        var result = donations.createDonationCheckout("project", 10000, monthly, donor, false, 1234);
        return intents.findById((String) result.get("intentId")).orElseThrow();
    }
    Map<String, Object> session(DonationIntent intent) {
        var value = new HashMap<String, Object>(); value.put("id", intent.getStripeSessionId()); value.put("livemode", false); value.put("status", "complete");
        value.put("payment_status", "paid"); value.put("mode", intent.isRecurring() ? "subscription" : "payment"); value.put("currency", "usd");
        value.put("amount_total", 10000); value.put("metadata", Map.of("intentId", intent.getId())); value.put("payment_intent", "pi_first");
        value.put("subscription", "sub_synthetic"); value.put("customer", "cus_synthetic"); return value;
    }
    Map<String, Object> payment(String id, String balanceStatus) {
        return Map.of("id", id, "livemode", false, "status", "succeeded", "currency", "usd", "amount_received", 10000,
                "latest_charge", Map.of("id", "ch_" + id, "paid", true, "captured", true, "disputed", false, "amount_refunded", 0,
                        "balance_transaction", Map.of("id", "txn_" + id, "status", balanceStatus, "currency", "usd", "amount", 10000, "fee", 321, "net", 9679)));
    }
    Map<String, Object> invoice(DonationIntent intent, String id) {
        return Map.of("id", id, "livemode", false, "status", "paid", "currency", "usd", "customer", "cus_synthetic", "amount_paid", 10000,
                "parent", Map.of("type", "subscription_details", "subscription_details", Map.of("subscription", "sub_synthetic", "metadata", Map.of("intentId", intent.getId()))));
    }
    void cashInvoice(String invoiceId, String paymentId) {
        when(gateway.getInvoicePayments(invoiceId)).thenReturn(Map.of("has_more", false, "data", List.of(Map.of("status", "paid", "amount_paid", 10000,
                "payment", Map.of("type", "payment_intent", "payment_intent", paymentId)))));
        when(gateway.getPaymentWithBalanceTransaction(paymentId)).thenReturn(payment(paymentId, "available")); subscriptionStatus("active", false);
    }
    void subscriptionStatus(String status, boolean cancel) {
        when(gateway.getSubscription("sub_synthetic")).thenReturn(Map.of("id", "sub_synthetic", "customer", "cus_synthetic", "livemode", false, "status", status, "cancel_at_period_end", cancel));
    }
    int event(String id, String type, Map<String, Object> object) throws Exception { return event(id, type, object, Map.of()); }
    int event(String id, String type, Map<String, Object> object, Map<String, Object> overrides) throws Exception {
        var payload = new HashMap<String, Object>(Map.of("id", id, "type", type, "livemode", false, "api_version", StripeGatewayService.API_VERSION, "data", Map.of("object", object)));
        payload.putAll(overrides); byte[] body = new ObjectMapper().writeValueAsBytes(payload);
        String timestamp = String.valueOf(Instant.now().getEpochSecond()); Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256")); mac.update((timestamp + ".").getBytes(StandardCharsets.UTF_8));
        return webhook.receive(body, "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac.doFinal(body))).getStatusCode().value();
    }
    long available() { return wallets.getWallet("creator", "usd", true).getAvailableCents(); }
    FinanceLedgerEntry credit(String paymentId) { return ledger.findById(FinanceSourceKey.stripe(true, "acct_platform", "settlement:txn_" + paymentId)).orElseThrow(); }
    void settleOneTime() throws Exception {
        var intent = checkout(false); event("evt_checkout", "checkout.session.completed", session(intent));
        when(gateway.getPaymentWithBalanceTransaction("pi_first")).thenReturn(payment("pi_first", "available")); settlement.reconcilePendingPayments();
    }
}
