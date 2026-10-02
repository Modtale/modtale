package net.modtale.service.finance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

@Service
public class StripeGatewayService {

    public static final String API_VERSION = "2026-08-26.dahlia";

    public record StripeResult(boolean success, String id, String url, String error, Map<String, Object> raw) {
    }

    private final WebClient webClient;
    private volatile String platformAccountId;

    @Value("${app.finance.stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.finance.stripe.mock-enabled:false}")
    private boolean mockEnabled;

    @Value("${app.finance.live-payments-enabled:false}")
    private boolean livePaymentsEnabled;

    @Value("${app.finance.stripe.platform-account-id:}")
    private String expectedPlatformAccountId;

    @Value("${app.finance.stripe.portal-configuration-id:}")
    private String portalConfigurationId;

    public boolean isAnonymousSandbox() { return isEnabled() && stripeSecretKey.startsWith("rkcs_"); }
    public boolean isLivePaymentsEnabled() { return livePaymentsEnabled; }
    public String getExpectedPlatformAccountId() { return expectedPlatformAccountId == null ? "" : expectedPlatformAccountId; }
    public String getPortalConfigurationId() { return portalConfigurationId == null ? "" : portalConfigurationId; }

    public boolean isMockEnabled() { return mockEnabled; }

    public boolean isTestMode() {
        return isEnabled() && (stripeSecretKey.startsWith("sk_test_") || stripeSecretKey.startsWith("rk_test_") || isAnonymousSandbox());
    }

    public boolean isLiveMode() {
        return isEnabled() && (stripeSecretKey.startsWith("sk_live_") || stripeSecretKey.startsWith("rk_live_"));
    }

    public boolean isReconciliationEnabled() { return isTestMode() || isLiveMode(); }
    public boolean isOperational() { return (isTestMode() && !isAnonymousSandbox()) || (isLiveMode() && livePaymentsEnabled); }
    public boolean isCheckoutAvailable() { return mockEnabled || isOperational(); }

    public String getAvailabilityMessage() {
        if (isAnonymousSandbox()) return "This limited test sandbox cannot verify the platform account. A full test credential is required before application payments, onboarding and withdrawals can be enabled.";
        return isOperational() ? (isTestMode() ? "Test mode: payments and transfers do not move real money." : "Creator payments are available. Settlement and account eligibility determine withdrawals.") : "Creator payments are being prepared. Live payments and withdrawals require provider approval and completed launch checks.";
    }

    // Live money is deliberately fail-closed. This is not a substitute for the launch checklist.
    private StripeResult unavailableForLiveMoney() {
        return new StripeResult(false, null, null, getAvailabilityMessage(), Map.of());
    }

    public StripeGatewayService() {
        this(WebClient.builder().baseUrl("https://api.stripe.com/v1"));
    }

    StripeGatewayService(WebClient.Builder builder) {
        this.webClient = builder.defaultHeader("Stripe-Version", API_VERSION).build();
    }

    public String getPlatformAccountId() {
        if (platformAccountId != null) return platformAccountId;
        Map<String, Object> account = getProviderObject("/account", Map.of());
        if (!(account.get("id") instanceof String id) || !id.startsWith("acct_")) throw new IllegalStateException("Could not verify the platform payment account.");
        if (!getExpectedPlatformAccountId().isBlank() && !id.equals(getExpectedPlatformAccountId()))
            throw new IllegalStateException("The payment credential does not match the configured platform account.");
        platformAccountId = id;
        return id;
    }

    public boolean isEnabled() {
        return stripeSecretKey != null && !stripeSecretKey.isBlank();
    }

    public StripeResult createOrSimulateConnectAccount(String email, String country, boolean forceMock, String idempotencyKey) {
        if (forceMock) {
            return new StripeResult(true, "sim_acct_" + UUID.randomUUID(), null, null, Map.of("simulated", true));
        }
        if (!isOperational()) return unavailableForLiveMoney();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("type", "express");
        form.add("capabilities[transfers][requested]", "true");
        if (email != null && !email.isBlank()) form.add("email", email);
        if (country != null && !country.isBlank()) form.add("country", country.toUpperCase());

        return postForm("/accounts", form, idempotencyKey);
    }

    public StripeResult createOrSimulateOnboardingLink(String accountId, String returnPath, boolean forceMock) {
        if (forceMock) {
            String url = normalizeFrontendUrl() + (returnPath.startsWith("/") ? returnPath : "/" + returnPath);
            return new StripeResult(true, "sim_link_" + UUID.randomUUID(), url, null, Map.of("simulated", true));
        }
        if (!isOperational()) return unavailableForLiveMoney();

        String returnUrl = normalizeFrontendUrl() + (returnPath.startsWith("/") ? returnPath : "/" + returnPath);
        String refreshUrl = normalizeFrontendUrl() + "/dashboard/finance?stripe=refresh";

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("account", accountId);
        form.add("refresh_url", refreshUrl);
        form.add("return_url", returnUrl);
        form.add("type", "account_onboarding");

        StripeResult result = postForm("/account_links", form);
        if (!result.success()) return result;
        return new StripeResult(true, result.id(), (String) result.raw().get("url"), null, result.raw());
    }

    public Map<String, Object> getAccountStatus(String accountId, boolean forceMock) {
        if (forceMock) {
            return Map.of(
                    "details_submitted", true,
                    "charges_enabled", true,
                    "payouts_enabled", true,
                    "country", "US",
                    "simulated", true
            );
        }
        if (!isEnabled()) {
            return Map.of("error", "Stripe secret key is not configured.");
        }

        try {
            Map<String, Object> result = webClient.get()
                    .uri("/accounts/{id}", accountId)
                    .headers(headers -> headers.setBasicAuth(stripeSecretKey, ""))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(20));
            return result == null ? Map.of() : result;
        } catch (Exception e) {
            return Map.of("error", "The payment provider could not be reached. Please try again later.");
        }
    }

    public StripeResult createOrSimulateDonationCheckout(
            String intentId,
            String projectTitle,
            long amountCents,
            boolean recurring,
            String successUrl,
            String cancelUrl,
            String currency,
            boolean forceMock
    ) {
        if (forceMock) {
            return new StripeResult(true, "sim_cs_" + UUID.randomUUID(), null, null, Map.of("simulated", true));
        }
        // Anonymous sandboxes support isolated Checkout contract tests, but cannot enable the application integration.
        if (!isOperational() && !isAnonymousSandbox()) return unavailableForLiveMoney();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", recurring ? "subscription" : "payment");
        form.add("success_url", successUrl);
        form.add("cancel_url", cancelUrl);
        form.add("line_items[0][price_data][currency]", currency);
        form.add("line_items[0][price_data][product_data][name]", "Support " + projectTitle + " on Modtale");
        form.add("line_items[0][price_data][unit_amount]", String.valueOf(amountCents));
        if (recurring) {
            form.add("line_items[0][price_data][recurring][interval]", "month");
        }
        form.add("line_items[0][quantity]", "1");
        form.add("metadata[intentId]", intentId);
        form.add("metadata[project]", projectTitle);
        form.add("metadata[source]", "modtale_creator_support");
        String metadataPrefix = recurring ? "subscription_data" : "payment_intent_data";
        form.add(metadataPrefix + "[metadata][intentId]", intentId);
        form.add(metadataPrefix + "[metadata][source]", "modtale_creator_support");

        StripeResult result = postForm("/checkout/sessions", form, "donation-checkout-" + intentId);
        if (!result.success()) return result;
        return new StripeResult(true, result.id(), (String) result.raw().get("url"), null, result.raw());
    }

    public Map<String, Object> getCheckoutSession(String sessionId, boolean forceMock) {
        if (forceMock) {
            return Map.of("id", sessionId, "status", "open", "payment_status", "unpaid", "simulated", true);
        }
        if (!isEnabled()) {
            return Map.of("error", "Stripe secret key is not configured.");
        }

        try {
            Map<String, Object> result = webClient.get()
                    .uri("/checkout/sessions/{id}", sessionId)
                    .headers(headers -> headers.setBasicAuth(stripeSecretKey, ""))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(20));
            return result == null ? Map.of() : result;
        } catch (Exception e) {
            return Map.of("error", "The payment provider could not be reached. Please try again later.");
        }
    }

    public StripeResult createOrSimulateTransfer(String destinationAccountId, long amountCents, String currency, String description, Map<String, String> metadata, boolean forceMock) {
        return createTransfer(destinationAccountId, amountCents, currency, description, metadata, forceMock, null);
    }

    public StripeResult createTransfer(String destinationAccountId, long amountCents, String currency, String description,
            Map<String, String> metadata, boolean forceMock, String idempotencyKey) {
        if (forceMock) {
            return new StripeResult(true, "sim_tr_" + UUID.randomUUID(), null, null, Map.of(
                    "simulated", true,
                    "createdAt", LocalDateTime.now().toString(),
                    "destination", destinationAccountId,
                    "amount", amountCents
            ));
        }
        if (!isOperational()) return unavailableForLiveMoney();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("amount", String.valueOf(amountCents));
        form.add("currency", currency);
        form.add("destination", destinationAccountId);
        if (description != null && !description.isBlank()) {
            form.add("description", description);
        }
        if (metadata != null) {
            metadata.forEach((k, v) -> {
                if (k != null && v != null) {
                    form.add("metadata[" + k + "]", v);
                }
            });
        }

        if (metadata != null && metadata.get("transferGroup") != null) form.add("transfer_group", metadata.get("transferGroup"));
        if (idempotencyKey == null || idempotencyKey.isBlank()) return new StripeResult(false, null, null, "A durable payout key is required.", Map.of());
        return postForm("/transfers", form, idempotencyKey);
    }

    public boolean verifyPlatformAccountId(String expected) {
        if (expected == null || !expected.matches("acct_[A-Za-z0-9]+")) return false;
        return expected.equals(getProviderObject("/account", Map.of()).get("id"));
    }

    public Map<String, Object> getBalanceTransaction(String id) {
        if (id == null || !id.matches("txn_[A-Za-z0-9]+")) return Map.of();
        return getProviderObject("/balance_transactions/" + id, Map.of());
    }

    public Map<String, Object> getCurrentAccount() { return getProviderObject("/account", Map.of()); }
    public Map<String, Object> getBalance() { return getProviderObject("/balance", Map.of()); }
    public Map<String, Object> getWebhookEndpoint(String id) {
        if (id == null || !id.matches("we_[A-Za-z0-9]+")) return Map.of();
        return getProviderObject("/webhook_endpoints/" + id, Map.of());
    }
    public Map<String, Object> getPortalConfiguration(String id) {
        if (id == null || !id.matches("bpc_[A-Za-z0-9]+")) return Map.of();
        return getProviderObject("/billing_portal/configurations/" + id, Map.of());
    }

    public Map<String, Object> getTransfer(String transferId) {
        if (transferId == null || !transferId.matches("tr_[A-Za-z0-9]+")) return Map.of();
        return getProviderObject("/transfers/" + transferId, Map.of());
    }

    public Map<String, Object> getChargeDisputes(String chargeId, String after) {
        if (chargeId == null || !chargeId.matches("ch_[A-Za-z0-9]+") || (after != null && !after.matches("d[pu]_[A-Za-z0-9]+"))) return Map.of();
        Map<String, String> query = new HashMap<>(); query.put("charge", chargeId); query.put("limit", "100");
        if (after != null) query.put("starting_after", after);
        return getProviderObject("/disputes", query);
    }

    public Map<String, Object> getDispute(String disputeId) {
        if (disputeId == null || !disputeId.matches("d[pu]_[A-Za-z0-9]+")) return Map.of();
        return getProviderObject("/disputes/" + disputeId, Map.of());
    }

    public Map<String, Object> getCharge(String chargeId) {
        if (chargeId == null || !chargeId.startsWith("ch_")) return Map.of();
        return getProviderObject("/charges/" + chargeId, Map.of());
    }

    public Map<String, Object> getChargeRefunds(String chargeId, String after) {
        if (chargeId == null || !chargeId.startsWith("ch_")) return Map.of();
        Map<String, String> query = new HashMap<>();
        query.put("charge", chargeId); query.put("limit", "100"); query.put("expand[]", "data.balance_transaction");
        if (after != null) query.put("starting_after", after);
        return getProviderObject("/refunds", query);
    }

    public Map<String, Object> getInvoicePayments(String invoiceId) {
        if (invoiceId == null || !invoiceId.startsWith("in_")) return Map.of();
        return getProviderObject("/invoice_payments", Map.of("invoice", invoiceId, "status", "paid", "limit", "100"));
    }

    public Map<String, Object> getSubscription(String subscriptionId) {
        if (subscriptionId == null || !subscriptionId.startsWith("sub_")) return Map.of();
        return getProviderObject("/subscriptions/" + subscriptionId, Map.of());
    }

    private Map<String, Object> getProviderObject(String path, Map<String, String> query) {
        if (!isReconciliationEnabled()) return Map.of();
        try {
            Map<String, Object> result = webClient.get().uri(builder -> {
                builder.path(path); query.forEach(builder::queryParam); return builder.build();
            }).headers(headers -> headers.setBasicAuth(stripeSecretKey, ""))
                    .retrieve().bodyToMono(Map.class).block(Duration.ofSeconds(20));
            return result == null ? Map.of() : result;
        } catch (Exception unavailable) { return Map.of(); }
    }

    public StripeResult createBillingPortalSession(String customerId, String returnUrl) {
        if (!isReconciliationEnabled()) return unavailableForLiveMoney();
        if (customerId == null || !customerId.startsWith("cus_")) return new StripeResult(false, null, null, "Invalid billing account.", Map.of());
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("customer", customerId); form.add("return_url", returnUrl);
        if (!getPortalConfigurationId().isBlank()) {
            if (!getPortalConfigurationId().matches("bpc_[A-Za-z0-9]+")) return new StripeResult(false, null, null, "Invalid billing portal configuration.", Map.of());
            form.add("configuration", getPortalConfigurationId());
        }
        return postForm("/billing_portal/sessions", form);
    }

    public Map<String, Object> getPaymentWithBalanceTransaction(String paymentId) {
        if (!isReconciliationEnabled() || paymentId == null || !paymentId.startsWith("pi_")) return Map.of();
        try {
            Map<String, Object> result = webClient.get()
                    .uri(builder -> builder.path("/payment_intents/{id}").queryParam("expand[]", "latest_charge.balance_transaction").build(paymentId))
                    .headers(headers -> headers.setBasicAuth(stripeSecretKey, ""))
                    .retrieve().bodyToMono(Map.class).block(Duration.ofSeconds(20));
            return result == null ? Map.of() : result;
        } catch (Exception unavailable) {
            return Map.of();
        }
    }

    private StripeResult postForm(String path, MultiValueMap<String, String> form) {
        return postForm(path, form, null);
    }

    private StripeResult postForm(String path, MultiValueMap<String, String> form, String idempotencyKey) {
        try {
            Map<String, Object> result = webClient.post()
                    .uri(path)
                    .headers(headers -> {
                        headers.setBasicAuth(stripeSecretKey, "");
                        if (idempotencyKey != null) headers.set("Idempotency-Key", idempotencyKey);
                    })
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(20));

            if (result == null) {
                return new StripeResult(false, null, null, "No response from Stripe", Map.of());
            }

            String id = valueAsString(result.get("id"));
            String url = valueAsString(result.get("url"));
            return new StripeResult(true, id, url, null, result);
        } catch (Exception e) {
            return new StripeResult(false, null, null, "The payment provider could not be reached. Please try again later.", Map.of());
        }
    }

    private String valueAsString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String normalizeFrontendUrl() {
        if (frontendUrl == null || frontendUrl.isBlank()) return "http://localhost:5173";
        return frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1) : frontendUrl;
    }
}
