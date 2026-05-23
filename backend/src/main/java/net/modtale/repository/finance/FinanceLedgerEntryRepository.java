package net.modtale.repository.finance;

import net.modtale.model.finance.FinanceLedgerEntry;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface FinanceLedgerEntryRepository extends MongoRepository<FinanceLedgerEntry, String> {
    List<FinanceLedgerEntry> findByCreatorId(String creatorId);
    List<FinanceLedgerEntry> findByCreatorIdAndStatus(String creatorId, FinanceLedgerEntry.EntryStatus status);
    List<FinanceLedgerEntry> findByCreatorIdAndStatusOrderByCreatedAtAsc(String creatorId, FinanceLedgerEntry.EntryStatus status);
    List<FinanceLedgerEntry> findByStatusAndExpiresAtBefore(FinanceLedgerEntry.EntryStatus status, LocalDateTime cutoff);
    List<FinanceLedgerEntry> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
