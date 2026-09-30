package net.modtale.service.finance;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.modtale.model.finance.CreatorPayoutRequest;
import net.modtale.model.user.User;
import net.modtale.repository.user.UserRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Reserves first, then dispatches each snapshotted recipient with a stable provider idempotency key. */
@Service
public class CreatorPayoutService {
    private final FinanceWalletService wallets;
    private final StripeGatewayService gateway;
    private final UserRepository users;
    private final RevenueOpsSupport core;

    public CreatorPayoutService(FinanceWalletService wallets, StripeGatewayService gateway,
            UserRepository users, RevenueOpsSupport core) {
        this.wallets = wallets; this.gateway = gateway; this.users = users; this.core = core;
    }

    public CreatorPayoutRequest request(User requester, User owner, String currency, Long requestedAmount,
            long minimum, String requestKey) {
        if (!gateway.isOperational()) throw new IllegalStateException(gateway.getAvailabilityMessage());
        try { java.util.UUID.fromString(requestKey); } catch (RuntimeException invalid) { throw new IllegalArgumentException("A valid payout request key is required."); }
        String accountId = gateway.getPlatformAccountId();
        if (!gateway.verifyPlatformAccountId(accountId)) throw new IllegalStateException("The payout provider account could not be verified.");
        String id = FinanceWalletService.walletId(owner.getId(), currency, gateway.isTestMode()) + ":" + requestKey;
        CreatorPayoutRequest existing = wallets.getRequest(id);
        if (existing != null) {
            if (!accountId.equals(existing.getProviderAccountId()) || !requester.getId().equals(existing.getRequestedBy()) || (requestedAmount != null && requestedAmount != existing.getAmountCents())) {
                throw new IllegalArgumentException("This request key belongs to a different payout.");
            }
            return existing;
        }
        long amount = requestedAmount == null ? wallets.getWallet(owner.getId(), currency, gateway.isTestMode()).getAvailableCents() : requestedAmount;
        if (amount < minimum) throw new IllegalArgumentException("The payout amount must meet the minimum.");
        List<CreatorPayoutRequest.Recipient> recipients = resolveRecipients(owner, amount);
        return wallets.reserve(owner.getId(), requester.getId(), currency, gateway.isTestMode(), requestKey, amount, minimum, recipients, accountId);
    }

    private List<CreatorPayoutRequest.Recipient> resolveRecipients(User owner, long amount) {
        Map<String, Long> weights = new LinkedHashMap<>();
        if (owner.getAccountType() == User.AccountType.ORGANIZATION && owner.getOrgPayoutMode() == User.OrgPayoutMode.DISTRIBUTE_TO_MEMBERS) {
            core.validateOrgPayoutShares(owner, owner.getOrgPayoutShares());
            if (owner.getOrgPayoutShares().size() > 50) throw new IllegalArgumentException("A payout may have at most 50 recipients.");
            for (var share : owner.getOrgPayoutShares()) weights.put(share.getUserId(), (long) share.getPercent());
        } else weights.put(owner.getId(), 100L);
        var allocation = RevenuePoolAllocator.allocate(amount, 10000, weights);
        List<CreatorPayoutRequest.Recipient> recipients = new ArrayList<>();
        for (var entry : allocation.projectCents().entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
            if (entry.getValue() == 0) continue;
            User user = users.findById(entry.getKey()).orElseThrow(() -> new IllegalArgumentException("Payout recipient no longer exists."));
            String accountId = user.getStripeConnectAccountId();
            if (accountId == null || !accountId.startsWith("acct_")) throw new IllegalStateException("All recipients must complete payout onboarding first.");
            Map<String, Object> status = gateway.getAccountStatus(accountId, false);
            if (!accountId.equals(status.get("id")) || !Boolean.TRUE.equals(status.get("payouts_enabled"))
                    || !(status.get("capabilities") instanceof Map<?, ?> capabilities) || !"active".equals(capabilities.get("transfers"))) {
                throw new IllegalStateException("A recipient is not currently eligible for transfers and payouts. Refresh their onboarding status.");
            }
            var recipient = new CreatorPayoutRequest.Recipient(); recipient.setUserId(user.getId()); recipient.setAccountId(accountId); recipient.setAmountCents(entry.getValue());
            recipients.add(recipient);
        }
        return recipients;
    }

    @Scheduled(fixedDelayString = "${app.finance.payout-dispatch-interval-ms:60000}")
    public void dispatchReservedPayouts() {
        if (!gateway.isOperational()) return;
        for (CreatorPayoutRequest request : wallets.getUnfinishedRequests(gateway.isTestMode())) {
            if (request.isTestMode() != gateway.isTestMode()) continue;
            try { dispatch(request.getId()); }
            catch (RuntimeException requiresReview) {
                try { wallets.requireReview(request.getId(), "Dispatch interrupted; verify the saved provider request before any further transfer."); }
                catch (RuntimeException storageUnavailable) { /* No funds are released; retry after storage recovers. */ }
            }
        }
    }

    public void dispatch(String requestId) {
        CreatorPayoutRequest request = wallets.getRequest(requestId);
        if (request == null || request.isTestMode() != gateway.isTestMode() || !gateway.isOperational()) return;
        wallets.noteDispatchAttempt(requestId);
        if (!gateway.verifyPlatformAccountId(request.getProviderAccountId())) {
            wallets.requireReview(requestId, "Payout provider account scope needs reconciliation."); return;
        }
        if (request.getStatus() == CreatorPayoutRequest.Status.RESERVED) {
            wallets.markAttempted(requestId);
            request = wallets.getRequest(requestId);
        }
        if (request.getStatus() != CreatorPayoutRequest.Status.PROCESSING) return;
        var wallet = wallets.getWallet(request.getCreatorId(), request.getCurrency(), request.isTestMode());
        if (wallet.isPayoutHold() || wallet.getAvailableCents() < 0) {
            wallets.requireReview(requestId, "Creator balance is on hold or needs reconciliation."); return;
        }
        // Stripe may prune keys after 24h. Never risk an automatic duplicate after that window.
        if (request.getFirstAttemptAt() != null && request.getFirstAttemptAt().isBefore(Instant.now().minus(Duration.ofHours(23)))) {
            wallets.requireReview(requestId, "Transfer confirmation needs reconciliation before any further attempt."); return;
        }
        for (int i = 0; i < request.getRecipients().size(); i++) {
            var recipient = request.getRecipients().get(i);
            if (recipient.getTransferId() != null) continue;
            if (!wallets.authorizeRecipientTransfer(requestId, i)) {
                wallets.requireReview(requestId, "Transfer authorization is paused for balance or risk reconciliation."); return;
            }
            request = wallets.getRequest(requestId);
            recipient = request.getRecipients().get(i);
            var result = gateway.createTransfer(recipient.getAccountId(), recipient.getAmountCents(), request.getCurrency(),
                    "Modtale creator earnings", transferMetadata(request, i), false,
                    "modtale-payout:" + request.getId() + ":" + i);
            if (!result.success()) return; // Leave funds reserved; the next attempt uses the exact same key.
            if (!matchesTransfer(request, i, result.id(), result.raw(), Instant.now())) {
                wallets.requireReview(requestId, "Provider transfer response did not match the saved authorization."); return;
            }
            try { wallets.recordTransfer(requestId, i, result.id(), "provider-response", "Verified response to the original idempotent transfer request."); }
            catch (IllegalStateException conflictingEvidence) { wallets.requireReview(requestId, "Transfer evidence conflicts with an existing payout claim."); return; }
        }
        wallets.completeTransfers(requestId);
    }

    static Map<String, String> transferMetadata(CreatorPayoutRequest request, int index) {
        return Map.of("payoutRequestId", request.getId(), "recipientIndex", String.valueOf(index),
                "correlationId", request.getRecipients().get(index).getCorrelationId(), "transferGroup", request.getTransferGroup());
    }

    /** Read-only provider retrieval followed by audited recognition of an already-sent transfer. */
    public CreatorPayoutRequest reconcileKnownTransfer(String requestId, int recipientIndex, String transferId, User reviewer, String reason) {
        CreatorPayoutRequest request = wallets.getRequest(requestId);
        if (request == null || recipientIndex < 0 || recipientIndex >= request.getRecipients().size()) throw new IllegalArgumentException("Payout recipient not found.");
        if (reviewer == null || reviewer.getId() == null || reason == null || reason.isBlank() || reason.length() > 1000) throw new IllegalArgumentException("A reviewer and reconciliation reason are required.");
        if (!gateway.isReconciliationEnabled() || request.isTestMode() != gateway.isTestMode()
                || !gateway.verifyPlatformAccountId(request.getProviderAccountId())) throw new IllegalArgumentException("Payout provider account or mode does not match.");
        Map<String, Object> transfer = gateway.getTransfer(transferId);
        if (!matchesTransfer(request, recipientIndex, transferId, transfer, Instant.now())) throw new IllegalArgumentException("Transfer evidence does not match this saved payout authorization. Funds remain reserved.");
        wallets.recordTransfer(requestId, recipientIndex, transferId, reviewer.getId(), reason.trim());
        return wallets.getRequest(requestId);
    }

    public List<CreatorPayoutRequest> getReviewRequests() {
        if (!gateway.isReconciliationEnabled()) return List.of();
        return wallets.getReviewRequests(gateway.isTestMode(), gateway.getPlatformAccountId());
    }

    static boolean matchesTransfer(CreatorPayoutRequest request, int index, String transferId, Map<String, Object> transfer, Instant now) {
        if (request == null || index < 0 || index >= request.getRecipients().size() || transfer == null) return false;
        var recipient = request.getRecipients().get(index);
        if (transferId == null || !transferId.matches("tr_[A-Za-z0-9]+") || !transferId.equals(transfer.get("id")) || !"transfer".equals(transfer.get("object"))
                || !(transfer.get("livemode") instanceof Boolean) || request.isTestMode() != Boolean.FALSE.equals(transfer.get("livemode"))
                || !recipient.getAccountId().equals(transfer.get("destination")) || recipient.getAmountCents() != PaymentAdjustmentService.number(transfer.get("amount"))
                || !request.getCurrency().equals(transfer.get("currency")) || request.getTransferGroup() == null || !request.getTransferGroup().equals(transfer.get("transfer_group"))
                || !Boolean.FALSE.equals(transfer.get("reversed")) || PaymentAdjustmentService.number(transfer.get("amount_reversed")) != 0
                || recipient.getAuthorizedAt() == null || request.getFirstAttemptAt() == null || recipient.getCorrelationId() == null
                || !(transfer.get("metadata") instanceof Map<?, ?> metadata)) return false;
        for (var expected : transferMetadata(request, index).entrySet()) if (!expected.getValue().equals(metadata.get(expected.getKey()))) return false;
        long created = PaymentAdjustmentService.number(transfer.get("created"));
        return created >= recipient.getAuthorizedAt().minusSeconds(300).getEpochSecond() && created <= now.plusSeconds(300).getEpochSecond();
    }

    public static Map<String, Object> toResponse(CreatorPayoutRequest request) {
        return Map.of("ok", true, "requestId", request.getId(), "amountCents", request.getAmountCents(),
                "status", request.getStatus(), "recipientCount", request.getRecipients().size(),
                "testMode", request.isTestMode(), "paymentStage", "transfer_to_connected_account");
    }
}
