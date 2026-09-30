package net.modtale.model.finance;

/** Minimal non-secret provider balance evidence used in a dispute's immutable review digest. */
public record FinanceDisputeBalance(String id, String source, String type, String currency, String status, Long amount, Long fee, Long net) {}
