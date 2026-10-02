package net.modtale.model.finance;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Prevents one claimed deposit being allocated across unrelated staged reports. It is not proof of receipt. */
@Document(collection = "ad_settlement_deposit_claims")
public record AdSettlementDepositClaim(@Id String id, String stageId) {}
