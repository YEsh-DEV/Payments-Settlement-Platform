package io.github.yesh.paycore.settlement.dto;

import io.github.yesh.paycore.settlement.domain.AdjustmentRecord;
import java.time.Instant;
import java.util.UUID;

public record AdjustmentResponse(
    UUID id,
    String adjustmentId,
    String caseId,
    String template,
    String debitAccount,
    String creditAccount,
    long amountPaise,
    String status,
    String proposedBy,
    String approvedBy,
    UUID ledgerJournalId,
    Instant createdAt,
    Instant updatedAt) {

  public static AdjustmentResponse fromEntity(AdjustmentRecord a) {
    return new AdjustmentResponse(
        a.getId(),
        a.getAdjustmentId(),
        a.getCaseId(),
        a.getTemplate(),
        a.getDebitAccount(),
        a.getCreditAccount(),
        a.getAmountPaise(),
        a.getStatus(),
        a.getProposedBy(),
        a.getApprovedBy(),
        a.getLedgerJournalId(),
        a.getCreatedAt(),
        a.getUpdatedAt());
  }
}
