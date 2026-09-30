package net.modtale.service.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import net.modtale.model.finance.CreatorWallet;
import net.modtale.model.finance.FinanceLedgerEntry;
import net.modtale.model.finance.PlatformFinanceSettings;
import net.modtale.model.user.User;
import net.modtale.repository.finance.FinanceLedgerEntryRepository;
import net.modtale.repository.finance.PlatformFinanceSettingsRepository;
import net.modtale.repository.project.ProjectRepository;
import net.modtale.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EarningsReadModelTest {
    private EarningsAccountService service;
    private FinanceLedgerEntryRepository ledger;
    private FinanceWalletService wallets;
    private StripeGatewayService gateway;
    private User creator;
    @BeforeEach void setup() {
        service = new EarningsAccountService();
        ledger = mock(FinanceLedgerEntryRepository.class); wallets = mock(FinanceWalletService.class); gateway = mock(StripeGatewayService.class);
        var settingsRepository = mock(PlatformFinanceSettingsRepository.class);
        var settings = new PlatformFinanceSettings(); settings.setMonetizationPolicyVersion(2);
        when(settingsRepository.findById("platform")).thenReturn(Optional.of(settings));
        var core = mock(RevenueOpsSupport.class); creator = new User(); creator.setId("creator");
        when(core.resolveFinanceOwner(any(), any(), eq(false))).thenReturn(creator);
        when(core.parseRangeDays(any())).thenReturn(30);
        ReflectionTestUtils.setField(service, "ledgerRepository", ledger);
        ReflectionTestUtils.setField(service, "settingsRepository", settingsRepository);
        ReflectionTestUtils.setField(service, "wallets", wallets);
        ReflectionTestUtils.setField(service, "stripeGatewayService", gateway);
        ReflectionTestUtils.setField(service, "core", core);
        ReflectionTestUtils.setField(service, "projectRepository", mock(ProjectRepository.class));
        ReflectionTestUtils.setField(service, "userRepository", mock(UserRepository.class));
        ReflectionTestUtils.setField(service, "creatorCountries", "US");
        var live = new CreatorWallet(); live.setAvailableCents(1500);
        var test = new CreatorWallet(); test.setAvailableCents(-45); test.setReservedCents(1000); test.setPayoutHold(true);
        when(wallets.getWallet("creator", "usd", false)).thenReturn(live);
        when(wallets.getWallet("creator", "usd", true)).thenReturn(test);
    }
    private FinanceLedgerEntry entry(boolean testMode, FinanceLedgerEntry.LedgerType type, long cents) {
        var entry = new FinanceLedgerEntry(); entry.setType(type); entry.setCurrency("usd"); entry.setCreatorCents(cents);
        entry.setCreatedAt(LocalDateTime.now()); entry.setStatus(FinanceLedgerEntry.EntryStatus.AVAILABLE);
        entry.getMetadata().put("testMode", String.valueOf(testMode)); entry.getMetadata().put("settlement", "settled");
        return entry;
    }
    @Test void testDashboardKeepsRefundsAndBalancesInTheSameMode() {
        when(gateway.isTestMode()).thenReturn(true);
        when(ledger.findByCreatorId("creator")).thenReturn(List.of(
                entry(false, FinanceLedgerEntry.LedgerType.DONATION, 9000),
                entry(true, FinanceLedgerEntry.LedgerType.DONATION, 450),
                entry(true, FinanceLedgerEntry.LedgerType.REFUND_ADJUSTMENT, -90)));
        var response = service.getCreatorOverview(creator, "creator", "30d");
        assertEquals(360L, response.get("periodDonationRevenueCents"));
        assertEquals(0L, response.get("testAvailableCents"));
        assertEquals(45L, response.get("adjustmentOwedCents"));
        assertEquals(1000L, response.get("reservedCents"));
        assertEquals(true, response.get("payoutHold"));
        assertEquals(1500L, response.get("availableCents"));
    }
    @Test void fundedLegacyWalletRemainsVisibleButRequiresScopeReconciliation() {
        when(gateway.isOperational()).thenReturn(true);
        when(ledger.findByCreatorId("creator")).thenReturn(List.of());
        var response = service.getCreatorOverview(creator, "creator", "30d");
        assertEquals(1500L, response.get("availableCents")); assertEquals(true, response.get("payoutHold"));
        assertEquals(false, response.get("fundingScopeVerified"));
    }
    @Test void matchingVerifiedFundingAccountCanProceedWithoutHidingItsBalance() {
        when(gateway.isReconciliationEnabled()).thenReturn(true); when(gateway.getPlatformAccountId()).thenReturn("acct_platform");
        wallets.getWallet("creator", "usd", false).setProviderAccountId("acct_platform");
        when(ledger.findByCreatorId("creator")).thenReturn(List.of());
        var response = service.getCreatorOverview(creator, "creator", "30d");
        assertEquals(1500L, response.get("availableCents")); assertEquals(false, response.get("payoutHold"));
        assertEquals(true, response.get("fundingScopeVerified"));
        when(gateway.getPlatformAccountId()).thenReturn("acct_other");
        assertEquals(true, service.getCreatorOverview(creator, "creator", "30d").get("payoutHold"));
    }
    @Test void partialTransferHistoryShowsOnlyTheUnconfirmedRemainderAsReserved() {
        var request = new net.modtale.model.finance.CreatorPayoutRequest(); request.setId("request"); request.setAmountCents(1000);
        var sent = new net.modtale.model.finance.CreatorPayoutRequest.Recipient(); sent.setAmountCents(400); sent.setTransferId("tr_sent");
        var pending = new net.modtale.model.finance.CreatorPayoutRequest.Recipient(); pending.setAmountCents(600); request.setRecipients(List.of(sent, pending));
        when(wallets.getRecentRequests("creator", false)).thenReturn(List.of(request));
        when(ledger.findByCreatorId("creator")).thenReturn(List.of());
        var response = service.getCreatorOverview(creator, "creator", "30d");
        var history = (List<java.util.Map<String, Object>>) response.get("payoutRequests");
        assertEquals(400L, history.getFirst().get("transferredCents")); assertEquals(600L, history.getFirst().get("remainingReservedCents"));
    }
    @Test void adminAvailableUsesRemainingWalletFundsInsteadOfHistoricEarnings() {
        when(wallets.getTotalAvailable("usd", false)).thenReturn(1500L);
        var response = service.getAdminOverview("30d");
        assertEquals(1500L, response.get("totalCreatorAvailableCents"));
        verify(wallets).getTotalAvailable("usd", false);
        verify(ledger, never()).findAll();
    }
}
