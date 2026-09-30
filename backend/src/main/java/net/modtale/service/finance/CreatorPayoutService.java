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
        String id = FinanceWalletService.walletId(owner.getId(), currency, gateway.isTestMode()) + ":" + requestKey;
        CreatorPayoutRequest existing = wallets.getRequest(id);
        if (existing != null) {
            if (!requester.getId().equals(existing.getRequestedBy()) || (requestedAmount != null && requestedAmount != existing.getAmountCents())) {
                throw new IllegalArgumentException("This request key belongs to a different payout.");
            }
            return existing;
        }
        long amount = requestedAmount == null ? wallets.getWallet(owner.getId(), currency, gateway.isTestMode()).getAvailableCents() : requestedAmount;
        if (amount < minimum) throw new IllegalArgumentException("The payout amount must meet the minimum.");
        List<CreatorPayoutRequest.Recipient> recipients = resolveRecipients(owner, amount);
        return wallets.reserve(owner.getId(), requester.getId(), currency, gateway.isTestMode(), requestKey, amount, minimum, recipients);
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
            if (request.isTestMode() == gateway.isTestMode()) dispatch(request.getId());
        }
    }

    public void dispatch(String requestId) {
        CreatorPayoutRequest request = wallets.getRequest(requestId);
        if (request == null || request.isTestMode() != gateway.isTestMode() || !gateway.isOperational()) return;
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
        if (request.getFirstAttemptAt() == null || request.getFirstAttemptAt().isBefore(Instant.now().minus(Duration.ofHours(23)))) {
            wallets.requireReview(requestId, "Transfer confirmation needs reconciliation before any further attempt."); return;
        }
        for (int i = 0; i < request.getRecipients().size(); i++) {
            var recipient = request.getRecipients().get(i);
            if (recipient.getTransferId() != null) continue;
            if (!wallets.authorizeRecipientTransfer(requestId, i)) {
                wallets.requireReview(requestId, "Transfer authorization is paused for balance or risk reconciliation."); return;
            }
            var result = gateway.createTransfer(recipient.getAccountId(), recipient.getAmountCents(), request.getCurrency(),
                    "Modtale creator earnings", Map.of("payoutRequestId", request.getId(), "recipientIndex", String.valueOf(i)), false,
                    "modtale-payout:" + request.getId() + ":" + i);
            if (!result.success()) return; // Leave funds reserved; the next attempt uses the exact same key.
            if (result.id() == null || !result.id().startsWith("tr_")
                    || !recipient.getAccountId().equals(result.raw().get("destination"))
                    || !(result.raw().get("amount") instanceof Number amount) || amount.longValue() != recipient.getAmountCents()
                    || !request.getCurrency().equals(result.raw().get("currency"))) {
                wallets.requireReview(requestId, "Provider transfer response did not match its reserved recipient and amount."); return;
            }
            wallets.recordTransfer(requestId, i, result.id());
        }
        wallets.completeTransfers(requestId);
    }

    public static Map<String, Object> toResponse(CreatorPayoutRequest request) {
        return Map.of("ok", true, "requestId", request.getId(), "amountCents", request.getAmountCents(),
                "status", request.getStatus(), "recipientCount", request.getRecipients().size(),
                "testMode", request.isTestMode(), "paymentStage", "transfer_to_connected_account");
    }
}
