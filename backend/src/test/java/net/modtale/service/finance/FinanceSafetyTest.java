package net.modtale.service.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.modtale.model.finance.AdCampaign;
import net.modtale.model.finance.DonationIntent;
import net.modtale.model.finance.FinanceLedgerEntry;
import net.modtale.model.finance.PlatformFinanceSettings;
import net.modtale.model.project.Project;
import net.modtale.repository.finance.AdCampaignRepository;
import net.modtale.repository.finance.DonationIntentRepository;
import net.modtale.repository.finance.FinanceLedgerEntryRepository;
import net.modtale.service.project.query.ProjectService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

class FinanceSafetyTest {
    private DonationIntent intent() {
        var intent = new DonationIntent();
        intent.setId("intent-1"); intent.setStripePlatformAccountId("acct_platform"); intent.setStripeSessionId("cs_test_1");
        intent.setAmountCents(500); intent.setCreatorCents(450); intent.setPlatformCents(50);
        intent.setCreatorId("creator"); intent.setProjectId("project");
        return intent;
    }

    private Map<String, Object> paidSession() {
        return new HashMap<>(Map.of("id", "cs_test_1", "mode", "payment", "payment_status", "paid",
                "status", "complete", "currency", "usd", "amount_total", 500, "livemode", false,
                "metadata", Map.of("intentId", "intent-1")));
    }

    @ParameterizedTest @ValueSource(longs = {-1, 0, 99, 100001, Long.MAX_VALUE})
    void invalidSupportAmountsAreRejectedRatherThanChanged(long cents) {
        assertThrows(IllegalArgumentException.class, () -> FinanceAmounts.validateSupportAmount(cents));
    }

    @Test void integerSplitConservesEveryCent() {
        for (long amount = 100; amount <= 100000; amount++) {
            long platform = FinanceAmounts.share(amount, 1000);
            assertEquals(amount, platform + (amount - platform));
        }
        assertEquals(4, FinanceAmounts.share(5, 7500));
    }

    @Test void unpaidCompletedSessionNeverCredits() {
        var session = paidSession(); session.put("payment_status", "unpaid");
        assertFalse(DonationCheckoutService.isVerifiedPayment(intent(), session));
    }

    @Test void paymentMustMatchAllRecordedCheckoutFields() {
        assertTrue(DonationCheckoutService.isVerifiedPayment(intent(), paidSession()));
        for (var entry : Map.<String, Object>of("id", "cs_other", "mode", "subscription", "currency", "eur",
                "amount_total", 499, "metadata", Map.of("intentId", "other")).entrySet()) {
            var session = paidSession(); session.put(entry.getKey(), entry.getValue());
            assertFalse(DonationCheckoutService.isVerifiedPayment(intent(), session), entry.getKey());
        }
        var session = paidSession(); session.put("simulated", true);
        assertFalse(DonationCheckoutService.isVerifiedPayment(intent(), session));
    }

    @Test void duplicateWebhookDoesNotOverwriteOrDuplicateCredit() {
        var service = new DonationCheckoutService();
        var intents = mock(DonationIntentRepository.class);
        var ledger = mock(FinanceLedgerEntryRepository.class);
        var intent = intent();
        when(intents.findByStripeSessionId("cs_test_1")).thenReturn(Optional.of(intent));
        when(ledger.insert(any(FinanceLedgerEntry.class))).thenThrow(new DuplicateKeyException("already inserted"));
        ReflectionTestUtils.setField(service, "donationIntentRepository", intents);
        ReflectionTestUtils.setField(service, "ledgerRepository", ledger);
        service.handlePaidCheckout(paidSession());
        assertEquals(DonationIntent.DonationStatus.COMPLETED, intent.getStatus());
        var captured = ArgumentCaptor.forClass(FinanceLedgerEntry.class);
        verify(ledger).insert(captured.capture());
        assertEquals("stripe:test:acct_platform:checkout:cs_test_1", captured.getValue().getId());
        assertEquals(FinanceLedgerEntry.EntryStatus.PENDING, captured.getValue().getStatus());
        assertNull(captured.getValue().getExpiresAt());
        assertFalse(FinanceLedgerRules.isReal(captured.getValue()));
        verify(ledger, never()).save(any());
    }

    @Test void legacyForfeitureEntryPointDoesNothing() {
        var service = new RevenueReportingService();
        var ledger = mock(FinanceLedgerEntryRepository.class);
        var intents = mock(DonationIntentRepository.class);
        ReflectionTestUtils.setField(service, "ledgerRepository", ledger);
        ReflectionTestUtils.setField(service, "donationIntentRepository", intents);
        service.expireCreatorFunds();
        verifyNoInteractions(ledger, intents);
    }

    @Test void publicRevenueExcludesTransfersTestMoneyAndUnsettledEntries() {
        var earned = new FinanceLedgerEntry();
        earned.setCreatedAt(LocalDateTime.now()); earned.setGrossCents(1000); earned.setCreatorCents(850); earned.setPlatformCents(100);
        earned.setType(FinanceLedgerEntry.LedgerType.DONATION); earned.setStatus(FinanceLedgerEntry.EntryStatus.AVAILABLE);
        earned.getMetadata().put("settlement", "settled");
        var payout = new FinanceLedgerEntry(); payout.setType(FinanceLedgerEntry.LedgerType.PAYOUT); payout.setGrossCents(850); payout.setCreatedAt(LocalDateTime.now());
        var simulated = new FinanceLedgerEntry(); simulated.setType(FinanceLedgerEntry.LedgerType.DONATION); simulated.setGrossCents(3000); simulated.getMetadata().put("simulated", "true"); simulated.setCreatedAt(LocalDateTime.now());
        var service = new RevenueReportingService(); var ledger = mock(FinanceLedgerEntryRepository.class);
        ReflectionTestUtils.setField(service, "ledgerRepository", ledger);
        when(ledger.findByCreatedAtBetween(any(), any())).thenReturn(List.of(earned, payout, simulated));
        var row = service.getPublicDailyRevenue(1).getFirst();
        assertEquals(1000L, row.get("grossCents"));
        assertEquals(850L, row.get("creatorCents"));
        assertEquals(100L, row.get("platformCents"));
    }

    @Test void clicksNeverMintEarnings() {
        var service = new AdCampaignService(); var campaigns = mock(AdCampaignRepository.class);
        var ledger = mock(FinanceLedgerEntryRepository.class); var projects = mock(ProjectService.class);
        var accounts = mock(EarningsAccountService.class); var core = new RevenueOpsSupport();
        var campaign = new AdCampaign(); campaign.setId("campaign"); campaign.setTargetUrl("https://sponsor.example/offer"); campaign.setBaseRevenuePerClickCents(50000);
        var project = new Project(); project.setId("project"); project.setAuthorId("creator"); project.setAdsEnabled(true);
        when(campaigns.findById("campaign")).thenReturn(Optional.of(campaign));
        when(projects.getProjectById("project")).thenReturn(project);
        when(accounts.getSettings()).thenReturn(new PlatformFinanceSettings());
        ReflectionTestUtils.setField(service, "adCampaignRepository", campaigns); ReflectionTestUtils.setField(service, "ledgerRepository", ledger);
        ReflectionTestUtils.setField(service, "projectService", projects); ReflectionTestUtils.setField(service, "financeAccountService", accounts); ReflectionTestUtils.setField(service, "core", core);
        assertEquals("https://sponsor.example/offer", service.registerAdClickAndResolveUrl("campaign", "project", "127.0.0.1"));
        service.registerAdClickAndResolveUrl("campaign", "project", "127.0.0.1");
        var captured = ArgumentCaptor.forClass(FinanceLedgerEntry.class);
        verify(ledger, times(1)).save(captured.capture());
        assertEquals(0, captured.getValue().getCreatorCents()); assertEquals(0, captured.getValue().getGrossCents());
        campaign.setActive(false);
        assertThrows(IllegalArgumentException.class, () -> service.registerAdClickAndResolveUrl("campaign", "project", "other"));
    }

    @ParameterizedTest @ValueSource(strings = {"javascript:alert(1)", "//evil.example", "http://sponsor.example", "https://user:password@sponsor.example", "data:image/svg+xml,test", ""})
    void unsafeSponsoredUrlsAreRejected(String url) {
        assertThrows(IllegalArgumentException.class, () -> RevenueOpsSupport.requireSafeExternalUrl(url));
    }
}
