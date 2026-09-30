package net.modtale.service.finance;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RevenuePoolAllocatorTest {
    @Test void distributesOnlyTheFundedCreatorShareAndPreservesRounding() {
        var result = RevenuePoolAllocator.allocate(101, 7500, Map.of("a", 1L, "b", 1L, "c", 1L));
        assertEquals(76, result.creatorPoolCents());
        assertEquals(25, result.platformCents());
        assertEquals(Map.of("a", 26L, "b", 25L, "c", 25L), result.projectCents());
        assertEquals(101, result.platformCents() + result.projectCents().values().stream().mapToLong(Long::longValue).sum());
    }
    @Test void allocationSnapshotDoesNotDependOnMapOrderOrFutureShareChanges() {
        var first = RevenuePoolAllocator.allocate(10000, 7500, Map.of("b", 2L, "a", 1L));
        var second = RevenuePoolAllocator.allocate(10000, 7500, Map.of("a", 1L, "b", 2L));
        assertEquals(first, second);
        assertEquals(7500, first.creatorShareBps());
        assertThrows(UnsupportedOperationException.class, () -> first.projectCents().put("extra", 100L));
    }
    @Test void handlesLargeLegitimateActivityWithoutOverflow() {
        var result = RevenuePoolAllocator.allocate(Long.MAX_VALUE, 7500, Map.of("a", Long.MAX_VALUE, "b", Long.MAX_VALUE));
        assertEquals(Long.MAX_VALUE, result.platformCents() + result.projectCents().values().stream().mapToLong(Long::longValue).sum());
    }
    @Test void rejectsUnverifiedEmptyOrInvalidActivity() {
        assertThrows(IllegalArgumentException.class, () -> RevenuePoolAllocator.allocate(100, 7500, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> RevenuePoolAllocator.allocate(100, 7500, Map.of("a", -1L)));
        assertThrows(IllegalArgumentException.class, () -> RevenuePoolAllocator.allocate(-100, 7500, Map.of("a", 1L)));
    }
}
