package net.modtale.controller.finance;

import jakarta.servlet.Filter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.modtale.config.properties.AppSecurityProperties;
import net.modtale.controller.auth.AuthController;
import net.modtale.controller.auth.CsrfController;
import net.modtale.model.finance.*;
import net.modtale.model.project.Project;
import net.modtale.model.user.User;
import net.modtale.repository.admin.BannedEmailRepository;
import net.modtale.repository.finance.*;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import net.modtale.service.analytics.TrackingService;
import net.modtale.service.auth.*;
import net.modtale.service.communication.EmailService;
import net.modtale.service.finance.*;
import net.modtale.service.project.query.ProjectService;
import net.modtale.service.security.access.PermissionProjectLookupService;
import net.modtale.service.user.account.AccountService;
import org.apache.catalina.startup.Tomcat;
import org.apache.tomcat.util.descriptor.web.FilterDef;
import org.apache.tomcat.util.descriptor.web.FilterMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** No component scanning or public fixture endpoint: every fixture exists only in this test JVM. */
class FinanceFrontendIntegrationTest {
    @TempDir Path temporary;

    @Configuration
    @Import(FinanceHttpSecurityTest.Config.class)
    static class Config {
        @Bean @Primary DonationCheckoutService realDonations() { return new DonationCheckoutService(); }
        @Bean CsrfController csrfController() { return new CsrfController(); }
        @Bean AuthenticationService fixtureAuthentication(UserRepository users) {
            return new AuthenticationService(users, mock(BannedEmailRepository.class), mock(TrackingService.class),
                    new BCryptPasswordEncoder(), mock(EmailService.class), mock(ReservedAccountGuardService.class),
                    mock(AppSecurityProperties.class), mock(OAuthUserLoginService.class), mock(OAuthAccountLinkingService.class));
        }
        @Bean AuthController authenticationController(AuthenticationService auth, AccountService accounts) {
            return new AuthController(auth, mock(AuthenticationMutationService.class), accounts, mock(TwoFactorService.class),
                    mock(LauncherAuthService.class), new HttpSessionSecurityContextRepository(), mock(MfaEnrollmentService.class));
        }
    }

    @Test void realFrontendUsesSessionCookiesCsrfAndAuthoritativeSupportTerms() throws Exception {
        Path frontend = Path.of("../frontend").toAbsolutePath().normalize();
        assertTrue(Files.isRegularFile(frontend.resolve("node_modules/vitest/vitest.mjs")), "Install frontend dependencies before this integration task");
        var tomcat = new Tomcat();
        tomcat.setBaseDir(temporary.resolve("tomcat").toString());
        tomcat.setHostname("127.0.0.1"); tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        var servlet = tomcat.addContext("", temporary.toString());
        servlet.setParentClassLoader(getClass().getClassLoader());
        var application = new AnnotationConfigWebApplicationContext();
        application.setServletContext(servlet.getServletContext());
        application.register(Config.class); application.refresh();
        Map<String, DonationIntent> intents = configureFixtures(application);
        var dispatcher = Tomcat.addServlet(servlet, "dispatcher", new DispatcherServlet(application));
        dispatcher.setLoadOnStartup(1); servlet.addServletMappingDecoded("/", "dispatcher");
        var filter = new FilterDef(); filter.setFilterName("security");
        filter.setFilter(application.getBean("springSecurityFilterChain", Filter.class)); servlet.addFilterDef(filter);
        var mapping = new FilterMap(); mapping.setFilterName("security"); mapping.addURLPattern("/*"); servlet.addFilterMap(mapping);
        Process child = null;
        try {
            tomcat.start();
            Path output = temporary.resolve("frontend-output.txt");
            Path report = Path.of("build/test-results/finance-frontend-ui.xml").toAbsolutePath();
            Files.createDirectories(report.getParent());
            var process = new ProcessBuilder("node", "node_modules/vitest/vitest.mjs", "run", "--config", "vitest.finance-integration.config.ts",
                    "--reporter=default", "--reporter=junit", "--outputFile.junit=" + report)
                    .directory(frontend.toFile()).redirectErrorStream(true).redirectOutput(output.toFile());
            // This subprocess only needs local Node and the disposable server. Do not inherit provider/cloud credentials.
            String path = process.environment().get("PATH");
            process.environment().clear();
            process.environment().put("PATH", path == null ? "/usr/bin:/bin" : path);
            process.environment().put("HOME", temporary.toString());
            process.environment().put("CI", "true");
            process.environment().put("MODTALE_FINANCE_TEST_ORIGIN", "http://127.0.0.1:" + tomcat.getConnector().getLocalPort());
            child = process.start();
            assertTrue(child.waitFor(120, TimeUnit.SECONDS), "Frontend integration timed out");
            String result = Files.readString(output); System.out.println(result);
            assertEquals(0, child.exitValue(), "Frontend integration failed:\n" + result);
            assertEquals(3, intents.size(), "Rejected, repeated, and stale requests must not create extra intents");
            assertEquals(1, intents.values().stream().filter(DonationIntent::isRecurring).count());
            for (var intent : intents.values()) {
                assertEquals(1234, intent.getPlatformCutBps());
                assertEquals(500, intent.getAmountCents()); assertEquals(62, intent.getPlatformCents()); assertEquals(438, intent.getCreatorCents());
                assertEquals("acct_fixture", intent.getStripePlatformAccountId()); assertEquals(Boolean.TRUE, intent.getStripeTestMode());
                assertEquals(DonationIntent.DonationStatus.PENDING, intent.getStatus());
                assertEquals(intent.isRecurring() ? "owner" : null, intent.getDonorUserId());
            }
            verifyNoInteractions(application.getBean(FinanceLedgerEntryRepository.class), application.getBean(FinanceWalletService.class));
            verify(application.getBean(StripeGatewayService.class), times(1)).createBillingPortalSession(eq("cus_owner"), anyString());
        } finally {
            if (child != null && child.isAlive()) child.destroyForcibly().waitFor();
            tomcat.stop(); tomcat.destroy(); application.close();
        }
    }

    private Map<String, DonationIntent> configureFixtures(AnnotationConfigWebApplicationContext app) {
        var users = app.getBean(UserRepository.class);
        for (String id : List.of("owner", "other")) {
            var user = new User(); user.setId(id); user.setUsername(id); user.setRoles(List.of("USER"));
            // Fictional, test-only password; no fixture user is persisted or accepted by a running application.
            user.setPassword(new BCryptPasswordEncoder().encode("isolated-fixture-password"));
            when(users.findByUsernameIgnoreCase(id)).thenReturn(Optional.of(user));
            when(users.findById(id)).thenReturn(Optional.of(user));
        }
        var project = new Project(); project.setId("project"); project.setAuthorId("owner"); project.setTitle("Isolated support fixture");
        project.setDonationsEnabled(true); project.setDonationPlatformCutBps(1234); project.setSuggestedDonationCents(500);
        var projects = app.getBean(ProjectService.class);
        when(projects.getProjectById("project")).thenReturn(project);
        when(projects.getRawProjectById("project")).thenReturn(project);
        when(projects.getProjectLink(project)).thenReturn("/mod/isolated-fixture");
        when(app.getBean(PermissionProjectLookupService.class).findProject("project")).thenReturn(project);
        when(app.getBean(ProjectRepository.class).save(any(Project.class))).thenAnswer(call -> call.getArgument(0));
        var settings = new PlatformFinanceSettings(); settings.setCurrency("usd"); settings.setMonetizationPolicyVersion(2);
        when(app.getBean(PlatformFinanceSettingsRepository.class).findById("platform")).thenReturn(Optional.of(settings));
        Map<String, DonationIntent> intents = new ConcurrentHashMap<>();
        var repository = app.getBean(DonationIntentRepository.class);
        when(repository.save(any(DonationIntent.class))).thenAnswer(call -> { DonationIntent intent = call.getArgument(0); intents.put(intent.getId(), intent); return intent; });
        when(repository.findById(anyString())).thenAnswer(call -> Optional.ofNullable(intents.get(call.getArgument(0))));
        var gateway = app.getBean(StripeGatewayService.class);
        when(gateway.isTestMode()).thenReturn(true); when(gateway.isOperational()).thenReturn(true); when(gateway.isCheckoutAvailable()).thenReturn(true);
        when(gateway.getPlatformAccountId()).thenReturn("acct_fixture");
        when(gateway.getAvailabilityMessage()).thenReturn("Isolated provider fixture");
        when(gateway.createOrSimulateDonationCheckout(anyString(), anyString(), anyLong(), anyBoolean(), anyString(), anyString(), anyString(), eq(false)))
                .thenAnswer(call -> new StripeGatewayService.StripeResult(true, "cs_" + call.getArgument(0), "https://checkout.stripe.com/c/pay/fixture", null, Map.of()));
        when(gateway.getCheckoutSession(anyString(), eq(false))).thenReturn(Map.of("payment_status", "unpaid", "livemode", false));
        when(gateway.createBillingPortalSession(eq("cus_owner"), anyString()))
                .thenReturn(new StripeGatewayService.StripeResult(true, "bps_fixture", "https://billing.stripe.com/p/session/fixture", null, Map.of()));
        var subscriptions = app.getBean(CreatorSupportSubscriptionRepository.class);
        var subscription = new CreatorSupportSubscription(); subscription.setId("sub_owner"); subscription.setDonorUserId("owner");
        subscription.setProjectId("project"); subscription.setCustomerId("cus_owner"); subscription.setProviderAccountId("acct_fixture");
        subscription.setTestMode(true); subscription.setAmountCents(500); subscription.setCurrency("usd"); subscription.setStatus("active");
        when(subscriptions.findById("sub_owner")).thenReturn(Optional.of(subscription));
        when(subscriptions.findByDonorUserId("owner")).thenReturn(List.of(subscription));
        when(subscriptions.findByDonorUserId("other")).thenReturn(List.of());
        return intents;
    }
}
