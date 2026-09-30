package net.modtale.service.finance;

import static org.mockito.Mockito.*;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import net.modtale.model.finance.CreatorPayoutRequest;
import net.modtale.model.finance.CreatorWallet;
import net.modtale.repository.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CreatorPayoutServiceTest {
    private FinanceWalletService wallets;
    private StripeGatewayService gateway;
    private CreatorPayoutService service;
    private CreatorPayoutRequest request;
    @BeforeEach void setUp() {
        wallets = mock(FinanceWalletService.class); gateway = mock(StripeGatewayService.class);
        service = new CreatorPayoutService(wallets, gateway, mock(UserRepository.class), new RevenueOpsSupport());
        request = new CreatorPayoutRequest(); request.setId("test:creator:usd:key"); request.setCreatorId("creator"); request.setCurrency("usd"); request.setTestMode(true);
        request.setStatus(CreatorPayoutRequest.Status.PROCESSING); request.setFirstAttemptAt(Instant.now());
        var recipient = new CreatorPayoutRequest.Recipient(); recipient.setAccountId("acct_recipient"); recipient.setAmountCents(1000); request.setRecipients(List.of(recipient));
        when(gateway.isTestMode()).thenReturn(true); when(gateway.isOperational()).thenReturn(true); when(wallets.authorizeRecipientTransfer(anyString(), anyInt())).thenReturn(true); when(wallets.getRequest(request.getId())).thenReturn(request);
        when(wallets.getWallet("creator", "usd", true)).thenReturn(new CreatorWallet());
    }
    @Test void uncertainProviderResponseKeepsFundsReservedAndSameRetryKey() {
        when(gateway.createTransfer(anyString(), anyLong(), anyString(), anyString(), anyMap(), eq(false), anyString()))
                .thenReturn(new StripeGatewayService.StripeResult(false, null, null, "timeout", Map.of()));
        service.dispatch(request.getId()); service.dispatch(request.getId());
        verify(gateway, times(2)).createTransfer(eq("acct_recipient"), eq(1000L), eq("usd"), anyString(), anyMap(), eq(false), eq("modtale-payout:test:creator:usd:key:0"));
        verify(wallets, never()).completeTransfers(anyString());
    }
    @Test void doesNotResendAnAlreadyConfirmedRecipient() {
        request.getRecipients().getFirst().setTransferId("tr_confirmed");
        service.dispatch(request.getId());
        verify(gateway, never()).createTransfer(anyString(), anyLong(), anyString(), anyString(), anyMap(), anyBoolean(), anyString());
        verify(wallets).completeTransfers(request.getId());
    }
    @Test void staleIdempotencyWindowRequiresReviewWithoutRetry() {
        request.setFirstAttemptAt(Instant.now().minusSeconds(24 * 3600));
        service.dispatch(request.getId());
        verify(wallets).requireReview(eq(request.getId()), anyString());
        verify(gateway, never()).createTransfer(anyString(), anyLong(), anyString(), anyString(), anyMap(), anyBoolean(), anyString());
    }
    @Test void mismatchedSuccessfulTransferIsNotMarkedComplete() {
        when(gateway.createTransfer(anyString(), anyLong(), anyString(), anyString(), anyMap(), eq(false), anyString()))
                .thenReturn(new StripeGatewayService.StripeResult(true, "tr_wrong", null, null, Map.of("destination", "acct_other", "amount", 1000, "currency", "usd")));
        service.dispatch(request.getId());
        verify(wallets).requireReview(eq(request.getId()), anyString()); verify(wallets, never()).completeTransfers(anyString());
    }
    @Test void accountHoldStopsDispatch() {
        var wallet = new CreatorWallet(); wallet.setPayoutHold(true); when(wallets.getWallet("creator", "usd", true)).thenReturn(wallet);
        service.dispatch(request.getId());
        verify(wallets).requireReview(eq(request.getId()), anyString());
        verify(gateway, never()).createTransfer(anyString(), anyLong(), anyString(), anyString(), anyMap(), anyBoolean(), anyString());
    }
    @Test void openRiskIdsAreIncludedInPayoutHold() {
        var wallet = new CreatorWallet(); wallet.setOpenRiskIds(List.of("dp_late"));
        when(wallets.getWallet("creator", "usd", true)).thenReturn(wallet);
        service.dispatch(request.getId());
        verify(wallets).requireReview(eq(request.getId()), anyString());
        verify(gateway, never()).createTransfer(anyString(), anyLong(), anyString(), anyString(), anyMap(), anyBoolean(), anyString());
    }
    @Test void failedTransactionalAuthorizationStopsTheOutboundCall() {
        when(wallets.authorizeRecipientTransfer(anyString(), anyInt())).thenReturn(false);
        service.dispatch(request.getId());
        verify(wallets).requireReview(eq(request.getId()), anyString());
        verify(gateway, never()).createTransfer(anyString(), anyLong(), anyString(), anyString(), anyMap(), anyBoolean(), anyString());
    }

}
