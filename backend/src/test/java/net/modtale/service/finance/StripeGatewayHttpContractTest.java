package net.modtale.service.finance;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises real HTTP encoding against a loopback stub, never Stripe or real credentials. */
class StripeGatewayHttpContractTest {
    private static final String FIXTURE_KEY = "sk_test_local_http_fixture_not_a_real_key";
    private record Reply(int status, String body, boolean disconnect) {}
    private record Request(String method, String path, Map<String, String> query, Map<String, String> form,
            String authorization, String version, String idempotency, String contentType) {}
    private HttpServer server;
    private ExecutorService executor;
    private StripeGatewayService gateway;
    private final BlockingQueue<Request> requests = new LinkedBlockingQueue<>();
    private final AtomicReference<Reply> reply = new AtomicReference<>();

    @BeforeEach void setup() throws Exception {
        reply.set(new Reply(200, "{\"id\":\"cs_fixture\",\"url\":\"https://checkout.stripe.test/fixture\"}", false));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newVirtualThreadPerTaskExecutor(); server.setExecutor(executor);
        server.createContext("/", exchange -> {
            try {
                var headers = exchange.getRequestHeaders();
                requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().getPath(),
                        decode(exchange.getRequestURI().getRawQuery()),
                        decode(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)),
                        headers.getFirst("Authorization"), headers.getFirst("Stripe-Version"),
                        headers.getFirst("Idempotency-Key"), headers.getFirst("Content-Type")));
                Reply response = reply.get();
                if (!response.disconnect()) {
                    byte[] bytes = response.body().getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(response.status(), bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            } finally { exchange.close(); }
        });
        server.start();
        // The test changes only the destination; the gateway applies its own production headers.
        gateway = new StripeGatewayService(WebClient.builder()
                .baseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/v1"));
        ReflectionTestUtils.setField(gateway, "stripeSecretKey", FIXTURE_KEY);
    }
    @AfterEach void cleanup() { if (server != null) server.stop(0); if (executor != null) executor.close(); }
    private Request take() throws Exception { var request = requests.poll(2, TimeUnit.SECONDS); assertNotNull(request, "Expected a loopback HTTP request"); return request; }
    private static Map<String, String> decode(String input) {
        Map<String, String> result = new LinkedHashMap<>();
        if (input == null || input.isEmpty()) return result;
        for (String part : input.split("&")) {
            String[] pair = part.split("=", 2);
            result.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8), URLDecoder.decode(pair.length == 2 ? pair[1] : "", StandardCharsets.UTF_8));
        }
        return result;
    }
    private static void assertProviderHeaders(Request request) {
        assertEquals("2026-08-26.dahlia", request.version());
        assertEquals("Basic " + Base64.getEncoder().encodeToString((FIXTURE_KEY + ":").getBytes(StandardCharsets.UTF_8)), request.authorization());
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void checkoutEncodesExactAmountsMetadataAndSubscriptionFrequency(boolean recurring) throws Exception {
        var result = gateway.createOrSimulateDonationCheckout("intent_fixture", "Café + Tools & Maps", 1234, recurring,
                "https://modtale.test/success?x=1&y=2", "https://modtale.test/cancel", "usd", false);
        assertTrue(result.success()); assertEquals("cs_fixture", result.id());
        assertEquals("https://checkout.stripe.test/fixture", result.url());
        Request request = take(); assertProviderHeaders(request);
        assertEquals("POST", request.method()); assertEquals("/v1/checkout/sessions", request.path());
        assertTrue(request.contentType().startsWith("application/x-www-form-urlencoded"));
        assertEquals("donation-checkout-intent_fixture", request.idempotency());
        assertEquals("1234", request.form().get("line_items[0][price_data][unit_amount]"));
        assertEquals("usd", request.form().get("line_items[0][price_data][currency]"));
        assertEquals("Support Café + Tools & Maps on Modtale", request.form().get("line_items[0][price_data][product_data][name]"));
        assertEquals("https://modtale.test/success?x=1&y=2", request.form().get("success_url"));
        assertEquals("intent_fixture", request.form().get("metadata[intentId]"));
        assertEquals(recurring ? "subscription" : "payment", request.form().get("mode"));
        assertEquals("intent_fixture", request.form().get((recurring ? "subscription_data" : "payment_intent_data") + "[metadata][intentId]"));
        assertEquals(recurring ? "month" : null, request.form().get("line_items[0][price_data][recurring][interval]"));
    }
    @Test void transferSerializationPreservesDurableIdentityAndIntegerCents() throws Exception {
        reply.set(new Reply(200, "{\"id\":\"tr_fixture\",\"amount\":1234,\"currency\":\"usd\",\"destination\":\"acct_creator\",\"livemode\":false}", false));
        var result = gateway.createTransfer("acct_creator", 1234, "usd", "Fixture payout", Map.of("payoutId", "payout_fixture", "recipientId", "creator_fixture"), false, "durable-payout-key");
        assertTrue(result.success()); assertEquals("tr_fixture", result.id());
        Request request = take(); assertProviderHeaders(request);
        assertEquals("POST", request.method()); assertEquals("/v1/transfers", request.path());
        assertEquals("durable-payout-key", request.idempotency());
        assertEquals("1234", request.form().get("amount")); assertEquals("usd", request.form().get("currency"));
        assertEquals("acct_creator", request.form().get("destination"));
        assertEquals("payout_fixture", request.form().get("metadata[payoutId]"));
        assertEquals("creator_fixture", request.form().get("metadata[recipientId]"));
    }
    @Test void realTransferWithoutDurableKeyIsRejectedBeforeHttp() {
        assertFalse(gateway.createTransfer("acct_creator", 1000, "usd", "Fixture", Map.of(), false, null).success());
        assertTrue(requests.isEmpty());
    }
    @Test void simulationNeverSendsARequestAndRemainsExplicitlyUnpaid() {
        assertTrue(gateway.createOrSimulateDonationCheckout("fixture", "Fixture", 500, false, "https://modtale.test", "https://modtale.test", "usd", true).success());
        var session = gateway.getCheckoutSession("sim_cs_fixture", true);
        assertEquals(true, session.get("simulated")); assertEquals("unpaid", session.get("payment_status"));
        assertEquals("open", session.get("status")); assertTrue(requests.isEmpty());
    }
    @Test void actualFeeLookupRequestsExpandedBalanceTransaction() throws Exception {
        reply.set(new Reply(200, "{\"id\":\"pi_fixture\",\"status\":\"succeeded\"}", false));
        assertEquals("pi_fixture", gateway.getPaymentWithBalanceTransaction("pi_fixture").get("id"));
        Request request = take(); assertProviderHeaders(request);
        assertEquals("GET", request.method()); assertEquals("/v1/payment_intents/pi_fixture", request.path());
        assertEquals("latest_charge.balance_transaction", request.query().get("expand[]"));
    }
    @Test void refundAndInvoiceQueriesUseCurrentDocumentedProviderCollections() throws Exception {
        reply.set(new Reply(200, "{\"data\":[],\"has_more\":false}", false));
        gateway.getChargeRefunds("ch_fixture", "re_previous");
        Request refunds = take(); assertProviderHeaders(refunds);
        assertEquals("/v1/refunds", refunds.path()); assertEquals("ch_fixture", refunds.query().get("charge"));
        assertEquals("100", refunds.query().get("limit")); assertEquals("re_previous", refunds.query().get("starting_after"));
        assertEquals("data.balance_transaction", refunds.query().get("expand[]"));
        gateway.getInvoicePayments("in_fixture"); Request invoices = take(); assertProviderHeaders(invoices);
        assertEquals("/v1/invoice_payments", invoices.path()); assertEquals("in_fixture", invoices.query().get("invoice"));
        assertEquals("paid", invoices.query().get("status")); assertEquals("100", invoices.query().get("limit"));
    }
    @Test void billingPortalSendsOnlyTheServerSelectedCustomerAndReturnUrl() throws Exception {
        gateway.createBillingPortalSession("cus_fixture", "https://modtale.test/dashboard/finance");
        Request request = take(); assertProviderHeaders(request);
        assertEquals("/v1/billing_portal/sessions", request.path());
        assertEquals(Map.of("customer", "cus_fixture", "return_url", "https://modtale.test/dashboard/finance"), request.form());
    }
    @ParameterizedTest @ValueSource(ints = {400, 401, 429, 500})
    void providerErrorsAreNotReportedAsSuccessfulTransfersOrLeakedToCallers(int status) throws Exception {
        reply.set(new Reply(status, "{\"error\":{\"message\":\"provider-private-detail\"}}", false));
        var result = gateway.createTransfer("acct_creator", 1000, "usd", "Fixture", Map.of(), false, "stable-key");
        assertFalse(result.success()); assertNull(result.id()); assertFalse(result.error().contains("provider-private-detail"));
        assertEquals("stable-key", take().idempotency());
    }
    @Test void lostResponseCannotBecomeSuccessAndAnyNetworkRetryKeepsTheSameKey() throws Exception {
        reply.set(new Reply(200, "", true));
        var result = gateway.createTransfer("acct_creator", 1000, "usd", "Fixture", Map.of(), false, "stable-uncertain-key");
        assertFalse(result.success()); assertNull(result.id());
        assertEquals("stable-uncertain-key", take().idempotency());
        for (Request retry : requests) assertEquals("stable-uncertain-key", retry.idempotency());
    }
    @Test void liveKillSwitchStopsNewMoneyButAllowsExistingPaymentReconciliation() throws Exception {
        ReflectionTestUtils.setField(gateway, "stripeSecretKey", "sk_live_local_http_fixture_not_a_real_key");
        ReflectionTestUtils.setField(gateway, "livePaymentsEnabled", false);
        assertFalse(gateway.createTransfer("acct_creator", 1000, "usd", "Fixture", Map.of(), false, "stable-key").success());
        assertFalse(gateway.createOrSimulateDonationCheckout("intent", "Fixture", 500, false, "https://modtale.test", "https://modtale.test", "usd", false).success());
        assertTrue(requests.isEmpty());
        reply.set(new Reply(200, "{\"id\":\"ch_fixture\",\"livemode\":true}", false));
        assertEquals("ch_fixture", gateway.getCharge("ch_fixture").get("id"));
        assertEquals("/v1/charges/ch_fixture", take().path());
    }
    @Test void payoutAccountVerificationBypassesCachedScopeAndRejectsChangedAccount() throws Exception {
        reply.set(new Reply(200, "{\"id\":\"acct_original\"}", false));
        assertEquals("acct_original", gateway.getPlatformAccountId());
        take();
        reply.set(new Reply(200, "{\"id\":\"acct_changed\"}", false));
        assertFalse(gateway.verifyPlatformAccountId("acct_original"));
        Request changed = take(); assertProviderHeaders(changed);
        assertEquals("GET", changed.method()); assertEquals("/v1/account", changed.path());
        assertTrue(gateway.verifyPlatformAccountId("acct_changed"));
        assertEquals("/v1/account", take().path());
    }
    @ParameterizedTest @ValueSource(ints = {401, 429, 500})
    void payoutAccountVerificationFailsClosedOnProviderErrors(int status) throws Exception {
        reply.set(new Reply(status, "{\"error\":{\"message\":\"private fixture detail\"}}", false));
        assertFalse(gateway.verifyPlatformAccountId("acct_expected"));
        assertEquals("/v1/account", take().path());
    }
    @Test void knownTransferReconciliationUsesReadOnlyProviderLookup() throws Exception {
        reply.set(new Reply(200, "{\"id\":\"tr_fixture\",\"amount\":1234,\"reversed\":false}", false));
        var transfer = gateway.getTransfer("tr_fixture");
        assertEquals("tr_fixture", transfer.get("id")); assertEquals(1234, transfer.get("amount"));
        Request request = take(); assertProviderHeaders(request);
        assertEquals("GET", request.method()); assertEquals("/v1/transfers/tr_fixture", request.path());
        assertTrue(request.form().isEmpty()); assertNull(request.idempotency());
    }
    @Test void invalidReconciliationIdentifiersNeverReachTheProvider() {
        for (String invalid : Arrays.asList(null, "", "acct_bad/path", "acct_", "acct_bad?query"))
            assertFalse(gateway.verifyPlatformAccountId(invalid));
        for (String invalid : Arrays.asList(null, "", "tr_bad/path", "tr_", "tr_bad?query"))
            assertTrue(gateway.getTransfer(invalid).isEmpty());
        assertTrue(requests.isEmpty());
    }
    @Test void transferGroupSurvivesFormEncodingAlongsideItsAuditMetadata() throws Exception {
        gateway.createTransfer("acct_creator", 1000, "usd", "Fixture", Map.of("transferGroup", "payout_fixture_group"), false, "stable-group-key");
        Request request = take();
        assertEquals("payout_fixture_group", request.form().get("transfer_group"));
        assertEquals("payout_fixture_group", request.form().get("metadata[transferGroup]"));
        assertEquals("stable-group-key", request.idempotency());
    }
    @Test void chargeDisputeEnumerationUsesFullCursorPaginationWithoutDateFiltering() throws Exception {
        reply.set(new Reply(200, "{\"data\":[],\"has_more\":false}", false));
        gateway.getChargeDisputes("ch_fixture", null);
        Request first = take(); assertProviderHeaders(first);
        assertEquals("GET", first.method()); assertEquals("/v1/disputes", first.path());
        assertEquals(Map.of("charge", "ch_fixture", "limit", "100"), first.query());
        gateway.getChargeDisputes("ch_fixture", "dp_previous");
        assertEquals(Map.of("charge", "ch_fixture", "limit", "100", "starting_after", "dp_previous"), take().query());
    }
    @Test void invalidDisputePaginationIdentifiersNeverReachProvider() {
        assertTrue(gateway.getChargeDisputes("ch_fixture/path", null).isEmpty());
        assertTrue(gateway.getChargeDisputes("ch_fixture", "dp_bad?query").isEmpty());
        assertTrue(gateway.getChargeDisputes(null, null).isEmpty());
        assertTrue(requests.isEmpty());
    }
    @Test void readinessProviderChecksOnlyReadExactConfigurationEndpoints() throws Exception {
        reply.set(new Reply(200, "{}", false));
        gateway.getCurrentAccount(); gateway.getBalance(); gateway.getWebhookEndpoint("we_fixture"); gateway.getPortalConfiguration("bpc_fixture");
        for (String path : List.of("/v1/account", "/v1/balance", "/v1/webhook_endpoints/we_fixture", "/v1/billing_portal/configurations/bpc_fixture")) {
            Request request = take(); assertProviderHeaders(request);
            assertEquals("GET", request.method()); assertEquals(path, request.path());
            assertTrue(request.query().isEmpty()); assertTrue(request.form().isEmpty());
        }
    }
    @Test void invalidReadinessIdentifiersNeverReachProvider() {
        for (String invalid : Arrays.asList(null, "", "we_", "we_bad/path", "we_bad?query")) assertTrue(gateway.getWebhookEndpoint(invalid).isEmpty());
        for (String invalid : Arrays.asList(null, "", "bpc_", "bpc_bad/path", "bpc_bad?query")) assertTrue(gateway.getPortalConfiguration(invalid).isEmpty());
        assertTrue(requests.isEmpty());
    }
    @Test void configuredPortalSessionUsesPinnedConfigurationAndRejectsInvalidConfig() throws Exception {
        ReflectionTestUtils.setField(gateway, "portalConfigurationId", "bpc_fixture");
        gateway.createBillingPortalSession("cus_fixture", "https://modtale.test/finance");
        assertEquals(Map.of("customer", "cus_fixture", "return_url", "https://modtale.test/finance", "configuration", "bpc_fixture"), take().form());
        ReflectionTestUtils.setField(gateway, "portalConfigurationId", "bpc_bad/path");
        assertFalse(gateway.createBillingPortalSession("cus_fixture", "https://modtale.test/finance").success());
        assertTrue(requests.isEmpty());
    }
    @Test void claimableSandboxCredentialsRemainTestOnlyEvenWhenLiveSwitchIsOff() throws Exception {
        ReflectionTestUtils.setField(gateway, "stripeSecretKey", "rkcs_local_http_fixture_not_a_real_key");
        ReflectionTestUtils.setField(gateway, "livePaymentsEnabled", false);
        assertTrue(gateway.isAnonymousSandbox()); assertTrue(gateway.isTestMode()); assertFalse(gateway.isLiveMode());
        assertFalse(gateway.isOperational()); assertFalse(gateway.isCheckoutAvailable());
        assertTrue(gateway.createOrSimulateDonationCheckout("intent_fixture", "Fixture", 500, false, "https://modtale.test", "https://modtale.test", "usd", false).success());
        assertEquals("/v1/checkout/sessions", take().path());
    }
    @Test void accountScopeComesFromCurrentPlatformAccountEndpoint() throws Exception {
        reply.set(new Reply(200, "{\"id\":\"acct_platform_fixture\"}", false));
        assertEquals("acct_platform_fixture", gateway.getPlatformAccountId());
        assertEquals("acct_platform_fixture", gateway.getPlatformAccountId());
        Request request = take(); assertProviderHeaders(request); assertEquals("/v1/account", request.path());
        assertTrue(requests.isEmpty(), "Stable configured credential may cache its verified account scope");
    }
}
