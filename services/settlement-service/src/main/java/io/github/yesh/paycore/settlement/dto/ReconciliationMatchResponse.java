package io.github.yesh.paycore.settlement.dto;

import io.github.yesh.paycore.settlement.domain.ReconciliationMatch;
import io.github.yesh.paycore.settlement.domain.ReconciliationStage;
import java.time.Instant;

public record ReconciliationMatchResponse(
    Long id,
    String runId,
    String recordId,
    Long providerLineId,
    String bankDepositId,
    ReconciliationStage stage,
    String rule,
    double confidence,
    String evidenceJson,
    long matchedAmountPaise,
    long variancePaise,
    Instant createdAt) {

  public static ReconciliationMatchResponse fromEntity(ReconciliationMatch m) {
    return new ReconciliationMatchResponse(
        m.getId(),
        m.getRunId(),
        m.getRecordId(),
        m.getProviderLineId(),
        m.getBankDepositId(),
        m.getStage(),
        m.getRule(),
        m.getConfidence(),
        m.getEvidenceJson(),
        m.getMatchedAmountPaise(),
        m.getVariancePaise(),
        m.getCreatedAt());
  }
}
