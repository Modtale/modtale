package net.modtale.controller.finance;

import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import net.modtale.config.auth.ApiKeyAuthFilter;
import net.modtale.config.properties.AppFrontendProperties;
import net.modtale.config.security.*;
import net.modtale.exception.GlobalExceptionHandler;
import net.modtale.model.finance.*;
import net.modtale.model.project.Project;
import net.modtale.model.user.*;
import net.modtale.repository.finance.*;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.auth.*;
import net.modtale.service.finance.*;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.*;
import net.modtale.service.user.account.AccountService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real MVC mappings, production filter chain and method security; repositories/provider transport are mocked. */
class FinanceHttpSecurityTest {
    private static final String CHECKOUT = "/api/v1/finance/projects/project/donations/checkout-url";
    private static final String PAYOUT = "/api/v1/finance/creator/payouts/request";
    private static final String PORTAL = "/api/v1/finance/support/subscriptions/sub_fixture/billing-portal";
    private static final String POLICY = "/api/v1/finance/projects/project/settings";
    private static final String WEBHOOK = "/api/v1/finance/webhooks/stripe";
    private static final String RECONCILE = "/api/v1/admin/finance/payout-reconciliation";
    private static final String DISPUTES = "/api/v1/admin/finance/dispute-reconciliation";
    private static final String SECRET = "fixture-signing-secret";
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;
    private User owner, other, reviewer;
    private Project project;

    @Configuration @EnableWebMvc @EnableWebSecurity @EnableMethodSecurity
    static class Config {
        @Bean AccountService accounts() {
            var service = mock(AccountService.class);
            when(service.getCurrentUser()).thenAnswer(call -> principal(SecurityContextHolder.getContext().getAuthentication()));
            when(service.getCurrentUser(any(Authentication.class))).thenAnswer(call -> principal(call.getArgument(0)));
            return service;
        }
        private static User principal(Authentication auth) { return auth != null && auth.getPrincipal() instanceof User user ? user : null; }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean ProjectRepository projects() { return mock(ProjectRepository.class); }
        @Bean ProjectService projectService() { return mock(ProjectService.class); }
        @Bean PermissionProjectLookupService permissionProjects() { return mock(PermissionProjectLookupService.class); }
        @Bean MongoTemplate mongo() { return mock(MongoTemplate.class); }
        @Bean(name = "apiSecurity") AccessControlService access(AccountService accounts, UserRepository users, PermissionProjectLookupService projects, MongoTemplate mongo) {
            return new AccessControlService(accounts, users, projects, mongo);
        }
        @Bean RevenueOpsSupport core() { return new RevenueOpsSupport(); }
        @Bean EarningsAccountService earnings() { return new EarningsAccountService(); }
        @Bean PlatformFinanceSettingsRepository settings() { return mock(PlatformFinanceSettingsRepository.class); }
        @Bean FinanceLedgerEntryRepository ledger() { return mock(FinanceLedgerEntryRepository.class); }
        @Bean FinanceWalletService wallets() { return mock(FinanceWalletService.class); }
        @Bean CreatorPayoutService payouts() { return mock(CreatorPayoutService.class); }
        @Bean DonationCheckoutService donations() { return mock(DonationCheckoutService.class); }
        @Bean StripeGatewayService gateway() { return mock(StripeGatewayService.class); }
        @Bean DonationIntentRepository intents() { return mock(DonationIntentRepository.class); }
        @Bean CreatorSupportSubscriptionRepository subscriptions() { return mock(CreatorSupportSubscriptionRepository.class); }
        @Bean RecurringSupportService recurring(CreatorSupportSubscriptionRepository subscriptions, DonationIntentRepository intents,
                FinanceLedgerEntryRepository ledger, StripeGatewayService gateway, RevenueOpsSupport core, ProjectService projects) {
            return new RecurringSupportService(subscriptions, intents, ledger, gateway, core, projects);
        }
        @Bean PaymentAdjustmentService adjustments() { return mock(PaymentAdjustmentService.class); }
        @Bean PaymentWebhookReceiptRepository receipts() { return mock(PaymentWebhookReceiptRepository.class); }
        @Bean AdSettlementStagingService staging() { return mock(AdSettlementStagingService.class); }
        @Bean ApiKeyService keys() { return mock(ApiKeyService.class); }
        @Bean ClientRegistrationRepository clients() { return mock(ClientRegistrationRepository.class); }
        @Bean GlobalExceptionHandler errors() { return new GlobalExceptionHandler(); }
        @Bean PayoutReconciliationController reconciliationController(CreatorPayoutService payouts, AccountService accounts) { return new PayoutReconciliationController(payouts, accounts); }
        @Bean DisputeReconciliationController disputeController(PaymentAdjustmentService adjustments, AccountService accounts) { return new DisputeReconciliationController(adjustments, accounts); }
        @Bean DonationController donationController() { return new DonationController(); }
        @Bean CreatorRevenueController creatorController() { return new CreatorRevenueController(); }
        @Bean RecurringSupportController recurringController(RecurringSupportService service, AccountService accounts) { return new RecurringSupportController(service, accounts); }
        @Bean AdSettlementAdminController adController(AdSettlementStagingService service, AccountService accounts) { return new AdSettlementAdminController(service, accounts); }
        @Bean StripeWebhookController webhook(DonationCheckoutService donations, PaymentWebhookReceiptRepository receipts,
                StripeGatewayService gateway, RecurringSupportService recurring, PaymentAdjustmentService adjustments) {
            return new StripeWebhookController(donations, receipts, gateway, recurring, adjustments, SECRET);
        }
        @Bean SecurityFilterChain security(HttpSecurity http, ApiKeyService keys, AccountService accounts) throws Exception {
            var config = new SecurityConfig(new ApiKeyAuthFilter(keys, (req, res, handler, failure) -> {
                res.setStatus(401); return new org.springframework.web.servlet.ModelAndView();
            }), new RateLimitFilter(keys), mock(OAuth2LoginService.class), mock(OidcLoginService.class),
                    mock(OAuth2AuthorizedClientRepository.class), mock(LocalUserDetailsService.class), mock(PasswordEncoder.class),
                    accounts, mock(AuthenticationService.class), mock(LauncherAuthService.class), new AppFrontendProperties("http://localhost:3000"));
            return config.securityFilterChain(http, mock(OAuth2AuthorizationRequestResolver.class));
        }
    }

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        owner = user("owner"); other = user("other"); reviewer = user("reviewer");
        reviewer.setAdminPermissions(Set.of(AdminPermission.PLATFORM_FINANCE_MANAGE));
        var users = bean(UserRepository.class);
        when(users.findById(owner.getId())).thenReturn(Optional.of(owner));
        when(users.findById(other.getId())).thenReturn(Optional.of(other));
        when(users.findById(reviewer.getId())).thenReturn(Optional.of(reviewer));
        project = new Project(); project.setId("project"); project.setAuthorId(owner.getId());
        when(bean(ProjectService.class).getRawProjectById("project")).thenReturn(project);
        when(bean(PermissionProjectLookupService.class).findProject("project")).thenReturn(project);
        var settings = new PlatformFinanceSettings(); settings.setMonetizationPolicyVersion(2);
        when(bean(PlatformFinanceSettingsRepository.class).findById("platform")).thenReturn(Optional.of(settings));
        when(bean(StripeGatewayService.class).isTestMode()).thenReturn(true);
        when(bean(StripeGatewayService.class).isReconciliationEnabled()).thenReturn(true);
        when(bean(StripeGatewayService.class).getPlatformAccountId()).thenReturn("acct_fixture");
    }
    @AfterEach void cleanup() { context.close(); SecurityContextHolder.clearContext(); }
    private <T> T bean(Class<T> type) { return context.getBean(type); }
    private static User user(String id) { var user = new User(); user.setId(id); user.setUsername(id); return user; }
    private static Authentication session(User user) { return new UsernamePasswordAuthenticationToken(user, null, List.of(new SimpleGrantedAuthority("ROLE_USER"))); }
    private static MockHttpServletRequestBuilder browser(MockHttpServletRequestBuilder request) {
        return request.header("Origin", "http://localhost:3000").header("User-Agent", "Mozilla/5.0");
    }
    private static MockHttpServletRequestBuilder csrf(MockHttpServletRequestBuilder request) {
        return request.cookie(new Cookie("XSRF-TOKEN", "fixture-csrf")).header("X-XSRF-TOKEN", "fixture-csrf");
    }
    private void apiKey(User user) {
        var key = new ApiKey(); key.setId("fixture-key-id"); key.setContextPermissions(Map.of("PERSONAL", Set.of(ApiKey.ApiPermission.PROFILE_READ)));
        when(bean(ApiKeyService.class).resolveKey("fixture-key")).thenReturn(key);
        when(bean(ApiKeyService.class).getUserFromKey(key)).thenReturn(user);
    }
    private static String checkoutBody() { return "{\"amountCents\":500,\"recurring\":false,\"guestCheckout\":true,\"expectedPlatformCutBps\":1000}"; }

    @Test void publicConfigurationWorksButCheckoutCannotBeCreatedWithGet() throws Exception {
        when(bean(DonationCheckoutService.class).getDonationConfig("project")).thenReturn(Map.of("donationPlatformCutPercent", 10));
        mvc.perform(browser(get("/api/v1/finance/projects/project/donation-config"))).andExpect(status().isOk()).andExpect(jsonPath("$.donationPlatformCutPercent").value(10));
        mvc.perform(browser(get(CHECKOUT)).with(authentication(session(owner)))).andExpect(status().isMethodNotAllowed());
        verify(bean(DonationCheckoutService.class), never()).createDonationCheckout(anyString(), anyLong(), anyBoolean(), any(), anyBoolean(), anyInt());
    }
    @Test void guestCheckoutRequiresMatchingCookieAndHeaderAndDoesNotAdoptBodyIdentity() throws Exception {
        var donations = bean(DonationCheckoutService.class);
        when(donations.createDonationCheckout(eq("project"), eq(500L), eq(false), isNull(), eq(true), eq(1000))).thenReturn(Map.of("url", "https://checkout.stripe.test/fixture"));
        mvc.perform(browser(post(CHECKOUT)).contentType("application/json").content(checkoutBody())).andExpect(status().isForbidden());
        mvc.perform(browser(post(CHECKOUT)).cookie(new Cookie("XSRF-TOKEN", "one")).header("X-XSRF-TOKEN", "two")
                .contentType("application/json").content(checkoutBody())).andExpect(status().isForbidden());
        mvc.perform(csrf(browser(post(CHECKOUT))).contentType("application/json").content(checkoutBody())).andExpect(status().isOk());
        verify(donations, times(1)).createDonationCheckout("project", 500L, false, null, true, 1000);
    }
    @ParameterizedTest @ValueSource(strings = {"500.99", "0", "-1", "100001", "null"})
    void malformedAmountsAreRejectedByHttpValidationBeforeCheckout(String amount) throws Exception {
        mvc.perform(csrf(browser(post(CHECKOUT))).contentType("application/json").content("{\"amountCents\":" + amount + ",\"recurring\":false,\"guestCheckout\":true,\"expectedPlatformCutBps\":1000}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(bean(DonationCheckoutService.class));
    }
    @Test void checkoutUsesAuthenticatedSessionIdentity() throws Exception {
        when(bean(DonationCheckoutService.class).createDonationCheckout(eq("project"), eq(500L), eq(false), same(owner), eq(false), eq(1000))).thenReturn(Map.of("url", "https://checkout.stripe.test/fixture"));
        mvc.perform(csrf(browser(post(CHECKOUT))).with(authentication(session(owner))).contentType("application/json")
                .content("{\"amountCents\":500,\"recurring\":false,\"guestCheckout\":false,\"expectedPlatformCutBps\":1000,\"donorUserId\":\"other\"}")).andExpect(status().isOk());
        verify(bean(DonationCheckoutService.class)).createDonationCheckout("project", 500L, false, owner, false, 1000);
    }
    @ParameterizedTest @ValueSource(strings = {PAYOUT, PORTAL, "/api/v1/finance/admin/ad-settlements"})
    void authenticatedFinanceMutationsRequireCsrf(String path) throws Exception {
        mvc.perform(browser(post(path)).with(authentication(session(reviewer))).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bean(CreatorPayoutService.class), bean(AdSettlementStagingService.class));
    }
    @Test void apiKeyCannotUseBrowserOnlyFinanceRoutesEvenForFinanceAdmin() throws Exception {
        apiKey(reviewer);
        for (String path : List.of(PAYOUT, PORTAL, "/api/v1/finance/admin/ad-settlements")) {
            mvc.perform(browser(post(path)).header("X-MODTALE-KEY", "fixture-key").contentType("application/json").content("{}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(bean(CreatorPayoutService.class), bean(AdSettlementStagingService.class));
    }
    @Test void apiKeyCannotCreateDonorCheckoutWithAnUnscopedPersonalKey() throws Exception {
        apiKey(owner);
        mvc.perform(browser(post(CHECKOUT)).header("X-MODTALE-KEY", "fixture-key").contentType("application/json").content(checkoutBody()))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bean(DonationCheckoutService.class));
    }
    @Test void invalidApiKeyCannotFallBackToAuthenticatedSession() throws Exception {
        mvc.perform(csrf(browser(post(PAYOUT))).with(authentication(session(owner))).header("X-MODTALE-KEY", "invalid")
                .contentType("application/json").content("{}")) .andExpect(status().isUnauthorized());
        verifyNoInteractions(bean(CreatorPayoutService.class));
    }
    @Test void anonymousCannotReadPrivateSubscriptionsOrRequestPayout() throws Exception {
        mvc.perform(browser(get("/api/v1/finance/support/subscriptions"))).andExpect(status().isUnauthorized());
        mvc.perform(csrf(browser(post(PAYOUT))).contentType("application/json").content("{}")) .andExpect(status().isUnauthorized());
        verifyNoInteractions(bean(CreatorPayoutService.class), bean(CreatorSupportSubscriptionRepository.class));
    }
    @Test void otherUsersPayoutAccountCannotBeSelectedInRequestBody() throws Exception {
        mvc.perform(csrf(browser(post(PAYOUT))).with(authentication(session(owner))).contentType("application/json")
                .content("{\"ownerId\":\"other\",\"amountCents\":1000,\"requestKey\":\"fixture\"}")) .andExpect(status().isForbidden());
        verifyNoInteractions(bean(CreatorPayoutService.class));
    }
    @Test void anotherDonorsBillingPortalCannotBeOpened() throws Exception {
        var subscription = new CreatorSupportSubscription(); subscription.setId("sub_fixture"); subscription.setDonorUserId(other.getId());
        when(bean(CreatorSupportSubscriptionRepository.class).findById("sub_fixture")).thenReturn(Optional.of(subscription));
        mvc.perform(csrf(browser(post(PORTAL))).with(authentication(session(owner)))).andExpect(status().isForbidden());
        verify(bean(StripeGatewayService.class), never()).createBillingPortalSession(anyString(), anyString());
    }
    @Test void financialReviewerCanReviewReportsButCannotEditAnotherCreatorsPolicy() throws Exception {
        when(bean(AdSettlementStagingService.class).list(reviewer)).thenReturn(List.of());
        mvc.perform(browser(get("/api/v1/finance/admin/ad-settlements")).with(authentication(session(reviewer)))).andExpect(status().isOk());
        mvc.perform(browser(get("/api/v1/finance/admin/ad-settlements")).with(authentication(session(other)))).andExpect(status().isForbidden());
        mvc.perform(csrf(browser(put(POLICY))).with(authentication(session(reviewer))).contentType("application/json").content("{\"adsEnabled\":true}"))
                .andExpect(status().isForbidden());
        verify(bean(ProjectRepository.class), never()).save(any(Project.class));
    }
    @Test void projectEditorCannotChangeOwnerOnlyMoneyPolicyButOwnerCan() throws Exception {
        project.setTeamMembers(List.of(new Project.ProjectMember(other.getId(), "editor")));
        project.setProjectRoles(List.of(new Project.ProjectRole("editor", "Editor", "blue", Set.of(ApiKey.ApiPermission.PROJECT_EDIT_METADATA))));
        mvc.perform(csrf(browser(put(POLICY))).with(authentication(session(other))).contentType("application/json").content("{\"donationPlatformCutBps\":2500}"))
                .andExpect(status().isForbidden());
        verify(bean(ProjectRepository.class), never()).save(any(Project.class));
        mvc.perform(csrf(browser(put(POLICY))).with(authentication(session(owner))).contentType("application/json").content("{\"donationPlatformCutBps\":2500}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.donationPlatformCutBps").value(2500));
        verify(bean(ProjectRepository.class)).save(project);
    }
    @Test void missingOrFractionalDisplayedShareCannotCreateCheckout() throws Exception {
        for (String body : List.of(
                "{\"amountCents\":500,\"recurring\":false,\"guestCheckout\":true}",
                "{\"amountCents\":500,\"recurring\":false,\"guestCheckout\":true,\"expectedPlatformCutBps\":1000.5}")) {
            mvc.perform(csrf(browser(post(CHECKOUT))).contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(bean(DonationCheckoutService.class));
    }
    @Test void staleDisplayedShareReturnsAnExplicitConflict() throws Exception {
        when(bean(DonationCheckoutService.class).createDonationCheckout("project", 500L, false, null, true, 1000))
                .thenThrow(new DonationCheckoutService.SupportTermsChangedException());
        mvc.perform(csrf(browser(post(CHECKOUT))).contentType("application/json").content(checkoutBody()))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("SUPPORT_TERMS_CHANGED"));
    }
    @Test void malformedJsonAndWrongContentTypeAreClientErrorsNotServerFailures() throws Exception {
        mvc.perform(csrf(browser(post(CHECKOUT))).contentType("application/json").content("{broken"))
                .andExpect(status().isBadRequest());
        mvc.perform(csrf(browser(post(CHECKOUT))).contentType("text/plain").content(checkoutBody()))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(bean(DonationCheckoutService.class));
    }
    @Test void foreignBrowserOriginCannotPreflightTheRequiredCsrfHeader() throws Exception {
        // Spring may allow the public Content-Type header while omitting the forbidden
        // CSRF header. Browsers must reject that incomplete preflight grant.
        var response = mvc.perform(options(CHECKOUT).header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "Content-Type,X-XSRF-TOKEN"))
                .andExpect(status().isOk()).andReturn().getResponse();
        assertEquals("*", response.getHeader("Access-Control-Allow-Origin"));
        assertNull(response.getHeader("Access-Control-Allow-Credentials"));
        assertNotNull(response.getHeader("Access-Control-Allow-Headers"));
        assertFalse(response.getHeader("Access-Control-Allow-Headers").toLowerCase(Locale.ROOT).contains("x-xsrf-token"));
        mvc.perform(options(CHECKOUT).header("Origin", "https://untrusted.example")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "X-XSRF-TOKEN"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bean(DonationCheckoutService.class));
    }
    @Test void apiKeyCannotUseBrowserConfirmationEndpoint() throws Exception {
        apiKey(owner);
        mvc.perform(browser(post("/api/v1/finance/donations/confirm")).header("X-MODTALE-KEY", "fixture-key")
                .contentType("application/json").content("{\"intentId\":\"fixture-intent\"}")).andExpect(status().isForbidden());
        verifyNoInteractions(bean(DonationCheckoutService.class));
    }
    @Test void ownPayoutUsesSessionOwnerAndPassesTheExactAmountAndRequestKey() throws Exception {
        var result = new CreatorPayoutRequest(); result.setId("payout_fixture"); result.setAmountCents(1000);
        result.setStatus(CreatorPayoutRequest.Status.RESERVED); result.setTestMode(true);
        when(bean(CreatorPayoutService.class).request(same(owner), same(owner), eq("usd"), eq(1000L), anyLong(), eq("fixture-request"))).thenReturn(result);
        mvc.perform(csrf(browser(post(PAYOUT))).with(authentication(session(owner))).contentType("application/json")
                .content("{\"amountCents\":1000,\"requestKey\":\"fixture-request\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.amountCents").value(1000)).andExpect(jsonPath("$.testMode").value(true));
        verify(bean(CreatorPayoutService.class)).request(same(owner), same(owner), eq("usd"), eq(1000L), anyLong(), eq("fixture-request"));
    }
    @Test void orgMetadataEditorCannotWithdrawOrChangePolicyButOrgOwnerCanChangePolicy() throws Exception {
        var org = user("org"); org.setAccountType(User.AccountType.ORGANIZATION);
        var ownerRole = new User.OrganizationRole("owner-role", "Owner", "blue", Set.of()); ownerRole.setOwner(true);
        var editorRole = new User.OrganizationRole("editor-role", "Editor", "blue", Set.of(ApiKey.ApiPermission.ORG_EDIT_METADATA));
        org.setOrganizationRoles(List.of(ownerRole, editorRole));
        org.setOrganizationMembers(List.of(new User.OrganizationMember(owner.getId(), "owner-role"), new User.OrganizationMember(other.getId(), "editor-role")));
        when(bean(UserRepository.class).findById("org")).thenReturn(Optional.of(org));
        String path = "/api/v1/finance/creator/orgs/org/payout-policy";
        String body = "{\"payoutMode\":\"DIRECT_TO_ORG_STRIPE\",\"shares\":[]}";
        mvc.perform(csrf(browser(put(path))).with(authentication(session(other))).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(csrf(browser(post(PAYOUT))).with(authentication(session(other))).contentType("application/json")
                .content("{\"ownerId\":\"org\",\"amountCents\":1000,\"requestKey\":\"fixture\"}")) .andExpect(status().isForbidden());
        verify(bean(UserRepository.class), never()).save(any(User.class)); verifyNoInteractions(bean(CreatorPayoutService.class));
        mvc.perform(csrf(browser(put(path))).with(authentication(session(owner))).contentType("application/json").content(body)).andExpect(status().isOk());
        verify(bean(UserRepository.class)).save(org);
    }
    private static String reconciliationBody() { return "{\"requestId\":\"payout_fixture\",\"recipientIndex\":0,\"transferId\":\"tr_fixture\",\"reason\":\"Verified provider receipt\",\"reviewerId\":\"other\"}"; }
    @Test void reconciliationQueueRequiresFinanceManagerAndRejectsApiKeys() throws Exception {
        mvc.perform(browser(get(RECONCILE))).andExpect(status().isForbidden());
        mvc.perform(browser(get(RECONCILE)).with(authentication(session(owner)))).andExpect(status().isForbidden());
        apiKey(reviewer);
        mvc.perform(browser(get(RECONCILE)).header("X-MODTALE-KEY", "fixture-key")).andExpect(status().isForbidden());
        verifyNoInteractions(bean(CreatorPayoutService.class));
        when(bean(CreatorPayoutService.class).getReviewRequests()).thenReturn(List.of());
        mvc.perform(browser(get(RECONCILE)).with(authentication(session(reviewer)))).andExpect(status().isOk());
        verify(bean(CreatorPayoutService.class)).getReviewRequests();
    }
    @Test void reconciliationMutationRequiresMatchingCsrfAndFinanceSession() throws Exception {
        String path = RECONCILE + "/confirm-existing-transfer";
        mvc.perform(browser(post(path)).with(authentication(session(reviewer))).contentType("application/json").content(reconciliationBody())).andExpect(status().isForbidden());
        mvc.perform(csrf(browser(post(path))).with(authentication(session(owner))).contentType("application/json").content(reconciliationBody())).andExpect(status().isForbidden());
        apiKey(reviewer);
        mvc.perform(browser(post(path)).header("X-MODTALE-KEY", "fixture-key").contentType("application/json").content(reconciliationBody())).andExpect(status().isForbidden());
        verifyNoInteractions(bean(CreatorPayoutService.class));
    }
    @Test void reconciliationUsesAuthenticatedReviewerAndExactReceiptArguments() throws Exception {
        var result = new CreatorPayoutRequest(); result.setId("payout_fixture"); result.setAmountCents(1000);
        result.setStatus(CreatorPayoutRequest.Status.RESERVED); result.setTestMode(true);
        when(bean(CreatorPayoutService.class).reconcileKnownTransfer("payout_fixture", 0, "tr_fixture", reviewer, "Verified provider receipt")).thenReturn(result);
        mvc.perform(csrf(browser(post(RECONCILE + "/confirm-existing-transfer"))).with(authentication(session(reviewer)))
                .contentType("application/json").content(reconciliationBody())).andExpect(status().isOk()).andExpect(jsonPath("$.requestId").value("payout_fixture"));
        verify(bean(CreatorPayoutService.class)).reconcileKnownTransfer("payout_fixture", 0, "tr_fixture", reviewer, "Verified provider receipt");
    }
    @ParameterizedTest @ValueSource(strings = {
            "{\"requestId\":\"\",\"recipientIndex\":0,\"transferId\":\"tr_fixture\",\"reason\":\"verified\"}",
            "{\"requestId\":\"payout_fixture\",\"recipientIndex\":-1,\"transferId\":\"tr_fixture\",\"reason\":\"verified\"}",
            "{\"requestId\":\"payout_fixture\",\"recipientIndex\":0,\"transferId\":\"tr_bad/path\",\"reason\":\"verified\"}",
            "{\"requestId\":\"payout_fixture\",\"recipientIndex\":0,\"transferId\":\"tr_fixture\",\"reason\":\"  \"}"})
    void invalidReconciliationEvidenceCannotReachAccounting(String body) throws Exception {
        mvc.perform(csrf(browser(post(RECONCILE + "/confirm-existing-transfer"))).with(authentication(session(reviewer)))
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        verifyNoInteractions(bean(CreatorPayoutService.class));
    }
    private static String disputeBody(String fee, String digest, String reason) {
        return "{\"caseId\":\"case_fixture\",\"expectedEvidenceDigest\":\"" + digest + "\",\"creatorFeeCents\":" + fee + ",\"reason\":\"" + reason + "\",\"reviewerId\":\"other\"}";
    }
    @Test void disputeQueueRequiresFinanceManagerAndRejectsApiKeys() throws Exception {
        mvc.perform(browser(get(DISPUTES))).andExpect(status().isForbidden());
        mvc.perform(browser(get(DISPUTES)).with(authentication(session(owner)))).andExpect(status().isForbidden());
        apiKey(reviewer);
        mvc.perform(browser(get(DISPUTES)).header("X-MODTALE-KEY", "fixture-key")).andExpect(status().isForbidden());
        verifyNoInteractions(bean(PaymentAdjustmentService.class));
        when(bean(PaymentAdjustmentService.class).getDisputeCases()).thenReturn(List.of());
        mvc.perform(browser(get(DISPUTES)).with(authentication(session(reviewer)))).andExpect(status().isOk());
        verify(bean(PaymentAdjustmentService.class)).getDisputeCases();
    }
    @Test void disputeResolutionRequiresCsrfAndFinanceSession() throws Exception {
        String body = disputeBody("125", "a".repeat(64), "Verified actual fee");
        mvc.perform(browser(post(DISPUTES + "/resolve")).with(authentication(session(reviewer))).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(csrf(browser(post(DISPUTES + "/resolve"))).with(authentication(session(owner))).contentType("application/json").content(body)).andExpect(status().isForbidden());
        apiKey(reviewer);
        mvc.perform(browser(post(DISPUTES + "/resolve")).header("X-MODTALE-KEY", "fixture-key").contentType("application/json").content(body)).andExpect(status().isForbidden());
        verifyNoInteractions(bean(PaymentAdjustmentService.class));
    }
    @Test void disputeResolutionBindsEvidenceAmountAndAuthenticatedReviewer() throws Exception {
        String digest = "a".repeat(64);
        var result = new FinanceDisputeResolution("resolution_fixture", "case_fixture", "dp_fixture", digest, "acct_fixture", true, "won", 125, 125, reviewer.getId(), "Verified actual fee", Instant.now(), "usd", 1000, 0, 1000, List.of());
        when(bean(PaymentAdjustmentService.class).resolveCase("case_fixture", digest, 125L, reviewer, "Verified actual fee")).thenReturn(result);
        mvc.perform(csrf(browser(post(DISPUTES + "/resolve"))).with(authentication(session(reviewer)))
                .contentType("application/json").content(disputeBody("125", digest, "Verified actual fee")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviewerId").value("reviewer")).andExpect(jsonPath("$.creatorFeeCents").value(125));
        verify(bean(PaymentAdjustmentService.class)).resolveCase("case_fixture", digest, 125L, reviewer, "Verified actual fee");
    }
    @ParameterizedTest @ValueSource(strings = {"-1", "0.5", "null", "1000000000000"})
    void invalidDisputeFeeCannotReachAccounting(String fee) throws Exception {
        mvc.perform(csrf(browser(post(DISPUTES + "/resolve"))).with(authentication(session(reviewer)))
                .contentType("application/json").content(disputeBody(fee, "a".repeat(64), "Verified actual fee"))).andExpect(status().isBadRequest());
        verifyNoInteractions(bean(PaymentAdjustmentService.class));
    }
    @Test void invalidEvidenceDigestAndBlankReasonCannotReachAccounting() throws Exception {
        for (String body : List.of(disputeBody("0", "abc", "Verified"), disputeBody("0", "A".repeat(64), "Verified"), disputeBody("0", "a".repeat(64), "  "))) {
            mvc.perform(csrf(browser(post(DISPUTES + "/resolve"))).with(authentication(session(reviewer)))
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(bean(PaymentAdjustmentService.class));
    }
    private static String event() { return "{\"id\":\"evt_fixture\",\"type\":\"checkout.session.completed\",\"livemode\":false,\"api_version\":\"" + StripeGatewayService.API_VERSION + "\",\"data\":{\"object\":{\"id\":\"cs_fixture\"}}}"; }
    private static String signature(String body) throws Exception {
        long now = Instant.now().getEpochSecond(); var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t=" + now + ",v1=" + HexFormat.of().formatHex(mac.doFinal((now + "." + body).getBytes(StandardCharsets.UTF_8)));
    }
    @Test void signedWebhookIsCsrfExemptAndUsesExactRawHttpBody() throws Exception {
        String body = event();
        mvc.perform(post(WEBHOOK).header("User-Agent", "Stripe/1.0").header("Stripe-Signature", signature(body))
                .contentType("application/json").content(body.getBytes(StandardCharsets.UTF_8))).andExpect(status().isOk());
        verify(bean(DonationCheckoutService.class)).handlePaidCheckout(anyMap());
        verify(bean(PaymentWebhookReceiptRepository.class)).insert(any(PaymentWebhookReceipt.class));
    }
    @Test void unsignedOrTamperedWebhookNeverReachesAccountingEvenWithBrowserCsrf() throws Exception {
        String body = event();
        mvc.perform(post(WEBHOOK).header("User-Agent", "Stripe/1.0").contentType("application/json").content(body)).andExpect(status().isBadRequest());
        mvc.perform(csrf(browser(post(WEBHOOK))).header("Stripe-Signature", signature(body)).contentType("application/json").content(body + " "))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(bean(DonationCheckoutService.class), bean(PaymentWebhookReceiptRepository.class));
    }
}
