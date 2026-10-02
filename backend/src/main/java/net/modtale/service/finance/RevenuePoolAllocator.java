package net.modtale.service.finance;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Allocates a collected revenue pool using a frozen set of valid activity weights, never ad clicks. */
public final class RevenuePoolAllocator {
    public record Allocation(long collectedCents, int creatorShareBps, long creatorPoolCents,
                             long platformCents, Map<String, Long> projectCents) {}
    private record Remainder(String projectId, BigInteger value) {}
    private RevenuePoolAllocator() {}

    public static Allocation allocate(long collectedCents, int creatorShareBps, Map<String, Long> eligibleActivity) {
        if (collectedCents < 0 || creatorShareBps < 0 || creatorShareBps > 10000) throw new IllegalArgumentException("Invalid funded pool.");
        if (eligibleActivity == null || eligibleActivity.isEmpty()) throw new IllegalArgumentException("A funded pool needs verified eligible activity before allocation.");
        BigInteger total = BigInteger.ZERO;
        for (var entry : eligibleActivity.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null || entry.getValue() <= 0) {
                throw new IllegalArgumentException("Every project must have a positive verified activity weight.");
            }
            total = total.add(BigInteger.valueOf(entry.getValue()));
        }
        long creatorPool = BigInteger.valueOf(collectedCents).multiply(BigInteger.valueOf(creatorShareBps))
                .add(BigInteger.valueOf(5000)).divide(BigInteger.valueOf(10000)).longValueExact();
        Map<String, Long> amounts = new LinkedHashMap<>();
        var remainders = new ArrayList<Remainder>();
        long allocated = 0;
        for (String projectId : eligibleActivity.keySet().stream().sorted().toList()) {
            BigInteger[] quotient = BigInteger.valueOf(creatorPool).multiply(BigInteger.valueOf(eligibleActivity.get(projectId))).divideAndRemainder(total);
            long cents = quotient[0].longValueExact();
            amounts.put(projectId, cents);
            allocated = Math.addExact(allocated, cents);
            remainders.add(new Remainder(projectId, quotient[1]));
        }
        // Largest remainder preserves every cent. Stable project ID order resolves equal remainders.
        remainders.sort(Comparator.comparing(Remainder::value).reversed().thenComparing(Remainder::projectId));
        for (int i = 0; i < creatorPool - allocated; i++) amounts.compute(remainders.get(i).projectId(), (key, amount) -> amount + 1);
        return new Allocation(collectedCents, creatorShareBps, creatorPool, collectedCents - creatorPool, Map.copyOf(amounts));
    }
}
