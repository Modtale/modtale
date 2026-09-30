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

    public record StripeResult(boolean success, String id, String url, String error, Map<String, Object> raw) {
    }

    private final WebClient webClient;

    @Value("${app.finance.stripe.secret-key:}")
    private String stripeSecretKey;

    @Value("${app.frontend.url:http://localhost:5173}")
    private String frontendUrl;

    @Value("${app.finance.stripe.mock-enabled:false}")
    private boolean mockEnabled;

    public boolean isMockEnabled() { return mockEnabled; }

    public boolean isTestMode() {
        return isEnabled() && (stripeSecretKey.startsWith("sk_test_") || stripeSecretKey.startsWith("rk_test_"));
    }

    public boolean isCheckoutAvailable() { return mockEnabled || isTestMode(); }

    public String getAvailabilityMessage() {
        return "Creator payments are in preview. Live payments and withdrawals are unavailable until provider approval and settlement reconciliation are complete.";
    }

    // Live money is deliberately fail-closed. This is not a substitute for the launch checklist.
    private StripeResult unavailableForLiveMoney() {
        return new StripeResult(false, null, null, getAvailabilityMessage(), Map.of());
    }

    public StripeGatewayService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.stripe.com/v1")
                .build();
    }

    public boolean isEnabled() {
        return stripeSecretKey != null && !stripeSecretKey.isBlank();
    }

    public StripeResult createOrSimulateConnectAccount(String email, String country, boolean forceMock) {
        if (forceMock) {
            return new StripeResult(true, "sim_acct_" + UUID.randomUUID(), null, null, Map.of("simulated", true));
        }
        if (!isTestMode()) return unavailableForLiveMoney();

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("type", "express");
        form.add("capabilities[transfers][requested]", "true");
        if (email != null && !email.isBlank()) form.add("email", email);
        if (country != null && !country.isBlank()) form.add("country", country.toUpperCase());

        return postForm("/accounts", form);
    }

    public StripeResult createOrSimulateOnboardingLink(String accountId, String returnPath, boolean forceMock) {
        if (forceMock) {
            String url = normalizeFrontendUrl() + (returnPath.startsWith("/") ? returnPath : "/" + returnPath);
            return new StripeResult(true, "sim_link_" + UUID.randomUUID(), url, null, Map.of("simulated", true));
        }
        if (!isTestMode()) return unavailableForLiveMoney();

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
        if (recurring) {
            return new StripeResult(false, null, null, "Monthly support is not available yet.", Map.of());
        }
        if (forceMock) {
            return new StripeResult(true, "sim_cs_" + UUID.randomUUID(), null, null, Map.of("simulated", true));
        }
        if (!isTestMode()) return unavailableForLiveMoney();

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
        form.add("metadata[source]", "modtale_donation");

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
        if (forceMock) {
            return new StripeResult(true, "sim_tr_" + UUID.randomUUID(), null, null, Map.of(
                    "simulated", true,
                    "createdAt", LocalDateTime.now().toString(),
                    "destination", destinationAccountId,
                    "amount", amountCents
            ));
        }
        if (!isTestMode()) return unavailableForLiveMoney();

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

        return postForm("/transfers", form);
    }

    public Map<String, Object> getPaymentWithBalanceTransaction(String paymentId) {
        if (!isTestMode() || paymentId == null || !paymentId.startsWith("pi_")) return Map.of();
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
