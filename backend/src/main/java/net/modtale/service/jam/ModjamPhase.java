package net.modtale.service.jam;

import java.time.Instant;
import java.util.Set;
import net.modtale.model.jam.Modjam;

public final class ModjamPhase {
    private ModjamPhase() {}

    public static String current(Modjam jam, Instant now) {
        if ("DRAFT".equals(jam.getStatus()) || "COMPLETED".equals(jam.getStatus())) return jam.getStatus();
        if (jam.getStartDate() != null && now.isBefore(jam.getStartDate())) return "UPCOMING";
        if (jam.getEndDate() != null && now.isBefore(jam.getEndDate())) return "ACTIVE";
        if (jam.getVotingEndDate() != null && now.isBefore(jam.getVotingEndDate())) return "VOTING";
        if (jam.getEndDate() != null || jam.getVotingEndDate() != null) return "AWAITING_WINNERS";
        return jam.getStatus() == null ? "" : jam.getStatus();
    }

    public static boolean hidesEntries(Modjam jam, Instant now) {
        return jam.isHideSubmissions() && Set.of("DRAFT", "UPCOMING", "ACTIVE").contains(current(jam, now));
    }
}
