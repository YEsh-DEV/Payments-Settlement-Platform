package io.github.yesh.paycore.settlement.dto;

import io.github.yesh.paycore.settlement.domain.ReconciliationRun;
import io.github.yesh.paycore.settlement.domain.ReconciliationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ReconciliationRunResponse(
    UUID id,
    String runId,
    String batchId,
    UUID fileId,
    LocalDate fromDate,
    LocalDate toDate,
    ReconciliationStatus status,
    int matchedCount,
    int discrepancyCount,
    int unmatchedCount,
    long totalVariancePaise,
    Double precisionPct,
    Double recallPct,
    Instant startedAt,
    Instant completedAt) {

  public static ReconciliationRunResponse fromEntity(ReconciliationRun r) {
    return new ReconciliationRunResponse(
        r.getId(),
        r.getRunId(),
        r.getBatchId(),
        r.getFileId(),
        r.getFromDate(),
        r.getToDate(),
        r.getStatus(),
        r.getMatchedCount(),
        r.getDiscrepancyCount(),
        r.getUnmatchedCount(),
        r.getTotalVariancePaise(),
        r.getPrecisionPct(),
        r.getRecallPct(),
        r.getStartedAt(),
        r.getCompletedAt());
  }
}
