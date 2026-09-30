package net.modtale.service.finance;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Read-only diagnostics. Provider configuration checks cannot establish business approval or delivery. */
@Service
public class StripeReadinessService {
    public record Check(String code, boolean passed, String action) {}
    public record Report(String mode, String apiVersion, boolean livePaymentsEnabled,
            boolean providerConfigurationVerified, Instant verifiedAt, List<Check> checks, List<String> remainingChecks) {}
    private final StripeGatewayService gateway;
    private final String webhookSecret;
    private final String webhookEndpointId;
    private final String backendUrl;
    private final String frontendUrl;
    public StripeReadinessService(StripeGatewayService gateway,
            @Value("${app.finance.stripe.webhook-secret:}") String webhookSecret,
            @Value("${app.finance.stripe.webhook-endpoint-id:}") String webhookEndpointId,
            @Value("${app.backend.url:http://localhost:8080}") String backendUrl,
            @Value("${app.frontend.url:http://localhost:5173}") String frontendUrl) {
        this.gateway = gateway; this.webhookSecret = webhookSecret; this.webhookEndpointId = webhookEndpointId;
        this.backendUrl = backendUrl; this.frontendUrl = frontendUrl;
    }
    public Report configuration() { return report(false); }
    public Report verifyProviderConfiguration() { return report(true); }
    private Report report(boolean verify) {
        List<Check> checks = new ArrayList<>();
        String mode = gateway.isTestMode() ? "TEST" : gateway.isLiveMode() ? "LIVE" : gateway.isEnabled() ? "UNKNOWN" : "UNCONFIGURED";
        add(checks, "credential_mode", gateway.isReconciliationEnabled(), "Set STRIPE_SECRET_KEY to a recognized test or live credential using the deployment secret store.");
        add(checks, "simulation_disabled", !gateway.isMockEnabled(), "Disable STRIPE_MOCK_ENABLED when testing or operating the real provider integration.");
        add(checks, "platform_account", gateway.getExpectedPlatformAccountId().matches("acct_[A-Za-z0-9]+"), "Set STRIPE_PLATFORM_ACCOUNT_ID to the exact platform account for this credential and environment.");
        add(checks, "webhook_signing_secret", webhookSecret != null && webhookSecret.startsWith("whsec_") && webhookSecret.length() > 6,
                "Set STRIPE_WEBHOOK_SECRET from this endpoint and environment. Its presence does not verify event delivery.");
        add(checks, "webhook_endpoint", webhookEndpointId != null && webhookEndpointId.matches("we_[A-Za-z0-9]+"), "Set STRIPE_WEBHOOK_ENDPOINT_ID to the platform event endpoint, not a connected-account event endpoint.");
        add(checks, "portal_configuration", gateway.getPortalConfigurationId().matches("bpc_[A-Za-z0-9]+"), "Set STRIPE_PORTAL_CONFIGURATION_ID to an active configuration with cancellation at period end and payment-method updates enabled.");
        add(checks, "backend_url", validBaseUrl(backendUrl, gateway.isTestMode()), "Set BACKEND_URL to the deployed HTTPS API origin. Loopback HTTP is only supported for local test forwarding.");
        add(checks, "frontend_url", validBaseUrl(frontendUrl, gateway.isTestMode()), "Set FRONTEND_URL to the deployed HTTPS site origin. Loopback HTTP is only supported for local tests.");
        if (verify && gateway.isReconciliationEnabled()) {
            Map<String, Object> account = gateway.getCurrentAccount();
            add(checks, "provider_account", "account".equals(account.get("object")) && gateway.getExpectedPlatformAccountId().equals(account.get("id")) && !gateway.getExpectedPlatformAccountId().isBlank(),
                    "Verify account-read permission and the expected platform account. Anonymous sandboxes can require claiming before this API is available.");
            Map<String, Object> balance = gateway.getBalance();
            add(checks, "provider_mode", "balance".equals(balance.get("object")) && sameMode(balance), "Verify balance-read permission and ensure the credential belongs to the intended test/live environment.");
            Map<String, Object> endpoint = gateway.getWebhookEndpoint(webhookEndpointId);
            String expectedUrl = backendUrl == null ? "" : backendUrl.replaceAll("/+$", "") + "/api/v1/finance/webhooks/stripe";
            add(checks, "provider_webhook_fields", webhookEndpointId != null && webhookEndpointId.equals(endpoint.get("id"))
                    && "webhook_endpoint".equals(endpoint.get("object")) && endpoint.containsKey("application") && endpoint.get("application") == null && sameMode(endpoint)
                    && "enabled".equals(endpoint.get("status")) && StripeGatewayService.API_VERSION.equals(endpoint.get("api_version"))
                    && expectedUrl.equals(endpoint.get("url")) && containsRequiredEvents(endpoint.get("enabled_events")),
                    "Verify an enabled endpoint with no associated Connect application, the exact API URL, pinned API version, matching mode and required events. Delivery scope still requires an authentic platform-event test. Required events: " + String.join(", ", new TreeSet<>(StripeWebhookEvents.REQUIRED)));
            Map<String, Object> portal = gateway.getPortalConfiguration(gateway.getPortalConfigurationId());
            Map<?, ?> features = map(portal.get("features"));
            Map<?, ?> cancellation = map(features.get("subscription_cancel"));
            add(checks, "provider_portal", gateway.getPortalConfigurationId().equals(portal.get("id"))
                    && "billing_portal.configuration".equals(portal.get("object")) && sameMode(portal) && Boolean.TRUE.equals(portal.get("active"))
                    && Boolean.TRUE.equals(cancellation.get("enabled")) && "at_period_end".equals(cancellation.get("mode"))
                    && Boolean.TRUE.equals(map(features.get("payment_method_update")).get("enabled")),
                    "Enable the configured portal in this environment with cancellation at period end and payment-method updates. Verify it with the customer's actual subscription.");
        }
        List<String> remaining = new ArrayList<>(List.of(
                "Platform-event delivery scope is not proven by endpoint fields. Deliver and replay an authentic signed platform webhook to this deployment; confirm the matching signing secret and idempotent persisted fulfillment.",
                "Complete sandbox Checkout, renewal, cancellation, actual fee settlement, refunds, disputes, Connect onboarding and payout reconciliation checks.",
                "Verify creator-country eligibility, tax collection and withholding, content-creator approval, applicable agreements and privacy disclosures before live activation.",
                "Reconcile actual Billing, Connect, routing and payout costs before allocating those costs; charge-level fees do not include every later fee.",
                "Resolve the creator zero-net/100% platform-share fee policy before permitting that live edge case."));
        if (gateway.isAnonymousSandbox()) remaining.add("Anonymous sandbox credentials have limited permissions and expire unless claimed. A full test credential is required for account, balance and Connect verification.");
        return new Report(mode, StripeGatewayService.API_VERSION, gateway.isLivePaymentsEnabled(),
                verify && checks.stream().allMatch(Check::passed), verify ? Instant.now() : null, List.copyOf(checks), List.copyOf(remaining));
    }
    private boolean sameMode(Map<String, Object> value) { return value.get("livemode") instanceof Boolean live && live != gateway.isTestMode(); }
    private static void add(List<Check> checks, String code, boolean passed, String action) { checks.add(new Check(code, passed, action)); }
    private static Map<?, ?> map(Object value) { return value instanceof Map<?, ?> m ? m : Map.of(); }
    private static boolean containsRequiredEvents(Object value) {
        return value instanceof List<?> events && (events.contains("*") || events.containsAll(StripeWebhookEvents.REQUIRED));
    }
    static boolean validBaseUrl(String value, boolean test) {
        try {
            URI uri = URI.create(value);
            if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null || uri.getHost() == null
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))) return false;
            return "https".equals(uri.getScheme()) || (test && "http".equals(uri.getScheme())
                    && Set.of("localhost", "127.0.0.1", "[::1]").contains(uri.getHost()));
        } catch (Exception invalid) { return false; }
    }
}
