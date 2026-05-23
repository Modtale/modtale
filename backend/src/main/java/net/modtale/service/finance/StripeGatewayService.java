package net.modtale.service.finance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDateTime;
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

    public StripeGatewayService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://api.stripe.com/v1")
                .build();
    }

    public boolean isEnabled() {
        return stripeSecretKey != null && !stripeSecretKey.isBlank();
    }

    public StripeResult createOrSimulateConnectAccount(String email, String country) {
        if (!isEnabled()) {
            return new StripeResult(true, "sim_acct_" + System.currentTimeMillis(), null, null, Map.of("simulated", true));
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("type", "express");
        if (email != null && !email.isBlank()) form.add("email", email);
        if (country != null && !country.isBlank()) form.add("country", country.toUpperCase());

        return postForm("/accounts", form);
    }

    public StripeResult createOrSimulateOnboardingLink(String accountId, String returnPath) {
        if (!isEnabled()) {
            String url = normalizeFrontendUrl() + (returnPath.startsWith("/") ? returnPath : "/" + returnPath);
            return new StripeResult(true, "sim_link_" + System.currentTimeMillis(), url, null, Map.of("simulated", true));
        }

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

    public Map<String, Object> getAccountStatus(String accountId) {
        if (!isEnabled()) {
            return Map.of(
                    "details_submitted", true,
                    "charges_enabled", true,
                    "payouts_enabled", true,
                    "country", "US",
                    "simulated", true
            );
        }

        try {
            Map<String, Object> result = webClient.get()
                    .uri("/accounts/{id}", accountId)
                    .headers(headers -> headers.setBasicAuth(stripeSecretKey, ""))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return result == null ? Map.of() : result;
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    public StripeResult createOrSimulateDonationCheckout(
            String intentId,
            String projectTitle,
            long amountCents,
            boolean recurring,
            String successUrl,
            String cancelUrl,
            String currency
    ) {
        if (!isEnabled()) {
            return new StripeResult(true, "sim_cs_" + System.currentTimeMillis(), normalizeFrontendUrl() + "/dashboard/finance?donation=intent-" + intentId, null, Map.of("simulated", true));
        }

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

        StripeResult result = postForm("/checkout/sessions", form);
        if (!result.success()) return result;
        return new StripeResult(true, result.id(), (String) result.raw().get("url"), null, result.raw());
    }

    public Map<String, Object> getCheckoutSession(String sessionId) {
        if (!isEnabled()) {
            return Map.of("id", sessionId, "status", "complete", "payment_status", "paid", "simulated", true);
        }

        try {
            Map<String, Object> result = webClient.get()
                    .uri("/checkout/sessions/{id}", sessionId)
                    .headers(headers -> headers.setBasicAuth(stripeSecretKey, ""))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();
            return result == null ? Map.of() : result;
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }

    public StripeResult createOrSimulateTransfer(String destinationAccountId, long amountCents, String currency, String description, Map<String, String> metadata) {
        if (!isEnabled()) {
            return new StripeResult(true, "sim_tr_" + System.currentTimeMillis(), null, null, Map.of(
                    "simulated", true,
                    "createdAt", LocalDateTime.now().toString(),
                    "destination", destinationAccountId,
                    "amount", amountCents
            ));
        }

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

    private StripeResult postForm(String path, MultiValueMap<String, String> form) {
        try {
            Map<String, Object> result = webClient.post()
                    .uri(path)
                    .headers(headers -> headers.setBasicAuth(stripeSecretKey, ""))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (result == null) {
                return new StripeResult(false, null, null, "No response from Stripe", Map.of());
            }

            String id = valueAsString(result.get("id"));
            String url = valueAsString(result.get("url"));
            return new StripeResult(true, id, url, null, result);
        } catch (Exception e) {
            return new StripeResult(false, null, null, e.getMessage(), Map.of("error", e.getMessage()));
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
