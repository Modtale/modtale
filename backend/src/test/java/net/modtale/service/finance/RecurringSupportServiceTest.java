package net.modtale.service.finance;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.modtale.model.finance.CreatorSupportSubscription;
import net.modtale.model.finance.FinanceLedgerEntry;
import net.modtale.model.user.User;
import net.modtale.repository.finance.CreatorSupportSubscriptionRepository;
import net.modtale.repository.finance.DonationIntentRepository;
import net.modtale.repository.finance.FinanceLedgerEntryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class RecurringSupportServiceTest {
    private CreatorSupportSubscriptionRepository subscriptions;
    private FinanceLedgerEntryRepository ledger;
    private StripeGatewayService gateway;
    private RecurringSupportService service;
    private Map<String, Object> invoice;
    @BeforeEach void setup() {
        subscriptions = mock(CreatorSupportSubscriptionRepository.class); ledger = mock(FinanceLedgerEntryRepository.class); gateway = mock(StripeGatewayService.class);
        service = new RecurringSupportService(subscriptions, mock(DonationIntentRepository.class), ledger, gateway, new RevenueOpsSupport(), mock(net.modtale.service.project.query.ProjectService.class));
        var subscription = new CreatorSupportSubscription(); subscription.setId("sub_test"); subscription.setProviderAccountId("acct_platform"); subscription.setDonorUserId("donor"); subscription.setCreatorId("creator"); subscription.setProjectId("project"); subscription.setCustomerId("cus_test"); subscription.setAmountCents(500); subscription.setPlatformCutBps(1000); subscription.setCurrency("usd"); subscription.setTestMode(true); subscription.setStatus("active");
        when(subscriptions.findById("sub_test")).thenReturn(Optional.of(subscription));
        when(gateway.getSubscription("sub_test")).thenReturn(Map.of("id", "sub_test", "customer", "cus_test", "status", "active"));
        invoice = Map.of("id", "in_test", "status", "paid", "parent", Map.of("type", "subscription_details", "subscription_details", Map.of("subscription", "sub_test")),
                "currency", "usd", "customer", "cus_test", "livemode", false, "amount_paid", 500);
    }
    private Map<String, Object> payment() { return Map.of("status", "paid", "amount_paid", 500, "payment", Map.of("type", "payment_intent", "payment_intent", "pi_test")); }
    @Test void repeatedPaidInvoiceCreatesOnlySourceIdentifiedPendingObservation() {
        when(gateway.getInvoicePayments("in_test")).thenReturn(Map.of("has_more", false, "data", List.of(payment())));
        when(ledger.insert(any(FinanceLedgerEntry.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertDoesNotThrow(() -> service.handlePaidInvoice(invoice));
        var captured = ArgumentCaptor.forClass(FinanceLedgerEntry.class); verify(ledger).insert(captured.capture());
        assertEquals("stripe:test:acct_platform:invoice:in_test", captured.getValue().getId()); assertEquals(450, captured.getValue().getCreatorCents());
        assertEquals("pi_test", captured.getValue().getMetadata().get("paymentIntentId"));
        assertEquals(FinanceLedgerEntry.EntryStatus.PENDING, captured.getValue().getStatus());
    }
    @Test void creditFundedOrMultiplePaymentsNeverBecomeFabricatedCashRevenue() {
        when(gateway.getInvoicePayments("in_test")).thenReturn(Map.of("has_more", false, "data", List.of()));
        assertThrows(IllegalArgumentException.class, () -> service.handlePaidInvoice(invoice));
        when(gateway.getInvoicePayments("in_test")).thenReturn(Map.of("has_more", false, "data", List.of(payment(), payment())));
        assertThrows(IllegalArgumentException.class, () -> service.handlePaidInvoice(invoice)); verifyNoInteractions(ledger);
    }
    @Test void billingPortalCannotBeOpenedByAnotherDonor() {
        var user = new User(); user.setId("other");
        assertThrows(SecurityException.class, () -> service.openBillingPortal(user, "sub_test"));
        verifyNoInteractions(gateway);
    }
    @Test void subscriptionUpdatesRetrieveCurrentProviderStateInsteadOfRegressingFromOldEvents() {
        when(gateway.getSubscription("sub_test")).thenReturn(Map.of("id", "sub_test", "customer", "cus_test", "status", "canceled", "cancel_at_period_end", false));
        service.refreshSubscription("sub_test");
        var captured = ArgumentCaptor.forClass(CreatorSupportSubscription.class); verify(subscriptions).save(captured.capture());
        assertEquals("canceled", captured.getValue().getStatus());
    }
}
