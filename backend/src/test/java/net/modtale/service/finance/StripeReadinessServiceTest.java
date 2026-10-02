package net.modtale.service.finance;

import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StripeReadinessServiceTest {
    StripeGatewayService gateway;
    StripeReadinessService service;
    @BeforeEach void setup() {
        gateway = mock(StripeGatewayService.class);
        when(gateway.isTestMode()).thenReturn(true); when(gateway.isReconciliationEnabled()).thenReturn(true);
        when(gateway.getExpectedPlatformAccountId()).thenReturn("acct_fixture"); when(gateway.getPortalConfigurationId()).thenReturn("bpc_fixture");
        when(gateway.getCurrentAccount()).thenReturn(Map.of("id", "acct_fixture", "object", "account"));
        when(gateway.getBalance()).thenReturn(Map.of("object", "balance", "livemode", false));
        when(gateway.getWebhookEndpoint("we_fixture")).thenReturn(endpoint());
        when(gateway.getPortalConfiguration("bpc_fixture")).thenReturn(portal());
        service = new StripeReadinessService(gateway, "whsec_fixture_never_return", "we_fixture", "https://api.modtale.test", "https://modtale.test");
    }
    Map<String, Object> endpoint() { var value = new HashMap<String, Object>(Map.of("id", "we_fixture", "object", "webhook_endpoint", "status", "enabled", "livemode", false,
            "api_version", StripeGatewayService.API_VERSION, "url", "https://api.modtale.test/api/v1/finance/webhooks/stripe", "enabled_events", new ArrayList<>(StripeWebhookEvents.REQUIRED))); value.put("application", null); return value; }
    Map<String, Object> portal() { return new HashMap<>(Map.of("id", "bpc_fixture", "object", "billing_portal.configuration", "livemode", false, "active", true,
            "features", Map.of("subscription_cancel", Map.of("enabled", true, "mode", "at_period_end"), "payment_method_update", Map.of("enabled", true)))); }
    @Test void configurationNeverCallsProviderOrClaimsVerified() {
        var result = service.configuration(); assertFalse(result.providerConfigurationVerified()); assertNull(result.verifiedAt());
        verify(gateway, never()).getCurrentAccount(); verify(gateway, never()).getWebhookEndpoint(any());
        assertFalse(result.toString().contains("whsec_fixture_never_return"));
    }
    @Test void verifiedConfigurationStillListsDeliveryAndBusinessChecks() {
        var result = service.verifyProviderConfiguration(); assertTrue(result.providerConfigurationVerified()); assertNotNull(result.verifiedAt());
        assertFalse(result.livePaymentsEnabled()); assertEquals("TEST", result.mode());
        assertTrue(result.remainingChecks().stream().anyMatch(x -> x.contains("authentic signed platform webhook")));
        assertFalse(result.toString().contains("whsec_fixture_never_return"));
    }
    @Test void unknownCredentialsNeverReachProvider() {
        when(gateway.isTestMode()).thenReturn(false); when(gateway.isReconciliationEnabled()).thenReturn(false); when(gateway.isEnabled()).thenReturn(true);
        assertEquals("UNKNOWN", service.verifyProviderConfiguration().mode()); assertFalse(service.verifyProviderConfiguration().providerConfigurationVerified());
        verify(gateway, never()).getCurrentAccount();
    }
    @Test void anonymousRestrictionsRemainExplicitAndBlocked() {
        when(gateway.isAnonymousSandbox()).thenReturn(true); when(gateway.getCurrentAccount()).thenReturn(Map.of()); when(gateway.getBalance()).thenReturn(Map.of());
        var result = service.verifyProviderConfiguration(); assertFalse(result.providerConfigurationVerified());
        assertTrue(result.remainingChecks().stream().anyMatch(x -> x.contains("expire unless claimed")));
    }
    @Test void everyProviderWebhookBindingIsRequired() {
        for (var mismatch : Map.<String,Object>of("id", "we_other", "object", "other", "status", "disabled", "livemode", true,
                "api_version", "2025-03-31.basil", "url", "https://elsewhere.test/stripe", "enabled_events", List.of("invoice.paid")).entrySet()) {
            var data = endpoint(); data.put(mismatch.getKey(), mismatch.getValue()); when(gateway.getWebhookEndpoint("we_fixture")).thenReturn(data);
            assertFalse(service.verifyProviderConfiguration().providerConfigurationVerified(), mismatch.getKey());
        }
        var data = endpoint(); data.remove("livemode"); when(gateway.getWebhookEndpoint("we_fixture")).thenReturn(data);
        assertFalse(service.verifyProviderConfiguration().providerConfigurationVerified());
    }
    @Test void connectApplicationAndAbsentScopeMetadataDoNotPassEndpointFields() {
        var endpoint = endpoint(); endpoint.put("application", "ca_fixture"); when(gateway.getWebhookEndpoint("we_fixture")).thenReturn(endpoint);
        assertFalse(service.verifyProviderConfiguration().providerConfigurationVerified());
        endpoint.remove("application"); assertFalse(service.verifyProviderConfiguration().providerConfigurationVerified());
        assertTrue(service.configuration().remainingChecks().stream().anyMatch(x -> x.contains("delivery scope is not proven")));
    }

    @Test void accountModeAndPortalAllFailClosed() {
        when(gateway.getCurrentAccount()).thenReturn(Map.of("id", "acct_wrong", "object", "account")); assertFalse(service.verifyProviderConfiguration().providerConfigurationVerified());
        when(gateway.getCurrentAccount()).thenReturn(Map.of("id", "acct_fixture", "object", "account"));
        when(gateway.getBalance()).thenReturn(Map.of("object", "balance")); assertFalse(service.verifyProviderConfiguration().providerConfigurationVerified());
        when(gateway.getBalance()).thenReturn(Map.of("object", "balance", "livemode", false));
        var p = portal(); p.put("features", Map.of("subscription_cancel", Map.of("enabled", true, "mode", "immediately")));
        when(gateway.getPortalConfiguration("bpc_fixture")).thenReturn(p); assertFalse(service.verifyProviderConfiguration().providerConfigurationVerified());
    }
    @Test void baseUrlsRejectCredentialsQueriesFragmentsAndNonLoopbackHttp() {
        for (String input : List.of("https://u:p@example.test", "https://example.test?x=1", "https://example.test#x", "https://example.test/path", "http://example.test", "javascript:foo", "//example.test")) assertFalse(StripeReadinessService.validBaseUrl(input, true), input);
        assertTrue(StripeReadinessService.validBaseUrl("http://127.0.0.1:8080", true));
        assertFalse(StripeReadinessService.validBaseUrl("http://127.0.0.1:8080", false));
        assertTrue(StripeReadinessService.validBaseUrl("https://api.modtale.test/", false));
    }
    @Test void anonymousCredentialIsTestOnlyAndDoesNotEnableUnknownKeys() {
        var actual = new StripeGatewayService();
        ReflectionTestUtils.setField(actual, "stripeSecretKey", "rkcs_official_prefix_fixture");
        ReflectionTestUtils.setField(actual, "livePaymentsEnabled", true);
        assertTrue(actual.isTestMode()); assertFalse(actual.isLiveMode()); assertTrue(actual.isAnonymousSandbox());
        ReflectionTestUtils.setField(actual, "stripeSecretKey", "unknown_fixture");
        assertFalse(actual.isOperational()); assertFalse(actual.isReconciliationEnabled());
    }
}
