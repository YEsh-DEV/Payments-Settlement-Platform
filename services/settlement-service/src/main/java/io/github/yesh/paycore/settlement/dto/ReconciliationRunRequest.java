package io.github.yesh.paycore.settlement.dto;

import java.time.LocalDate;

public record ReconciliationRunRequest(
    String batchId,
    LocalDate fromDate,
    LocalDate toDate,
    Integer providerFeeBps,
    Long providerFixedFee,
    Long feeToleranceMinor,
    Long reserveHoldbackToleranceMinor) {

  public ReconciliationRunRequest(
      String batchId,
      LocalDate fromDate,
      LocalDate toDate,
      Integer providerFeeBps,
      Long feeToleranceMinor) {
    this(batchId, fromDate, toDate, providerFeeBps, 0L, feeToleranceMinor, 0L);
  }

  public ReconciliationRunRequest(String batchId) {
    this(batchId, null, null, 200, 0L, 1L, 0L);
  }
}
