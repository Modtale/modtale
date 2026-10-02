package net.modtale.service.finance;

import java.util.*;
import net.modtale.model.finance.ProviderCostEvidence;
import net.modtale.model.user.*;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProviderCostEvidenceServiceTest {
    StripeGatewayService gateway; MongoTemplate mongo; ProviderCostEvidenceService service; User reviewer;
    ProviderCostEvidenceService.ImportRequest request = new ProviderCostEvidenceService.ImportRequest("txn_fee", "acct_platform", true, "Verify actual service fee; attribution remains unreviewed.");
    @BeforeEach void setup() {
        gateway = mock(StripeGatewayService.class); mongo = mock(MongoTemplate.class); service = new ProviderCostEvidenceService(gateway, mongo);
        reviewer = new User(); reviewer.setId("reviewer"); reviewer.setAdminPermissions(Set.of(AdminPermission.PLATFORM_FINANCE_MANAGE));
        when(gateway.isTestMode()).thenReturn(true); when(gateway.isReconciliationEnabled()).thenReturn(true);
        when(gateway.getExpectedPlatformAccountId()).thenReturn("acct_platform"); when(gateway.verifyPlatformAccountId("acct_platform")).thenReturn(true);
        when(gateway.getBalance()).thenReturn(Map.of("object", "balance", "livemode", false));
        when(gateway.getBalanceTransaction("txn_fee")).thenReturn(StripeCostEvidenceTest.fee());
        when(mongo.insert(any(ProviderCostEvidence.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
    @Test void recordsCanonicalEvidenceWithNoAllocationAndAuthenticatedReviewer() {
        var evidence = service.retrieveAndImport(reviewer, request);
        assertEquals("UNALLOCATED", evidence.allocationStatus()); assertEquals("reviewer", evidence.recordedBy()); assertEquals(200, evidence.costMinorUnits());
        assertEquals("stripe:test:acct_platform:provider-cost:txn_fee", evidence.id());
        verify(gateway).verifyPlatformAccountId("acct_platform"); verify(mongo).insert(evidence); verifyNoMoreInteractions(mongo);
    }
    @Test void nonReviewerCannotReadOrImportOrCallProvider() {
        var user = new User(); user.setId("owner");
        assertThrows(SecurityException.class, () -> service.list(user)); assertThrows(SecurityException.class, () -> service.retrieveAndImport(user, request));
        verifyNoInteractions(gateway, mongo);
    }
    @Test void wrongAccountAndModeNeverRetrieveEvidence() {
        when(gateway.verifyPlatformAccountId("acct_platform")).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> service.retrieveAndImport(reviewer, request));
        when(gateway.verifyPlatformAccountId("acct_platform")).thenReturn(true); when(gateway.isTestMode()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> service.retrieveAndImport(reviewer, request));
        verify(gateway, never()).getBalanceTransaction(any()); verifyNoInteractions(mongo);
    }
    @Test void missingOrWrongBalanceModeNeverRetrievesEvidence() {
        for (var balance : List.of(Map.<String,Object>of("object", "balance"), Map.<String,Object>of("object", "balance", "livemode", true), Map.<String,Object>of("object", "wrong", "livemode", false))) {
            when(gateway.getBalance()).thenReturn(balance); assertThrows(IllegalStateException.class, () -> service.retrieveAndImport(reviewer, request));
        }
        verify(gateway, never()).getBalanceTransaction(any()); verifyNoInteractions(mongo);
    }
    @Test void unsupportedProviderDataNeverReachesStorage() {
        var data = StripeCostEvidenceTest.fee(); data.put("type", "charge"); when(gateway.getBalanceTransaction("txn_fee")).thenReturn(data);
        assertThrows(IllegalArgumentException.class, () -> service.retrieveAndImport(reviewer, request)); verifyNoInteractions(mongo);
    }
}
