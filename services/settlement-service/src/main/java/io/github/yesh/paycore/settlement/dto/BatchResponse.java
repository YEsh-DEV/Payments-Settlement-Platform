package io.github.yesh.paycore.settlement.dto;

import io.github.yesh.paycore.settlement.domain.Batch;
import io.github.yesh.paycore.settlement.domain.BatchStatus;
import java.time.Instant;

public record BatchResponse(
    String batchId,
    String cutoffWindow,
    BatchStatus status,
    int totalCount,
    long totalGrossPaise,
    long totalFeePaise,
    long totalNetPaise,
    Instant createdAt,
    Instant closedAt) {

  public static BatchResponse fromEntity(Batch b) {
    return new BatchResponse(
        b.getBatchId(),
        b.getCutoffWindow(),
        b.getStatus(),
        b.getTotalCount(),
        b.getTotalGrossPaise(),
        b.getTotalFeePaise(),
        b.getTotalNetPaise(),
        b.getCreatedAt(),
        b.getClosedAt());
  }
}
