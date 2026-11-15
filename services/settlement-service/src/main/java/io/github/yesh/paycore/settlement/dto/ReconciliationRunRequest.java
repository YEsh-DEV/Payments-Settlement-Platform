package io.github.yesh.paycore.settlement.dto;

import java.time.LocalDate;

public record ReconciliationRunRequest(
    String batchId,
    LocalDate fromDate,
    LocalDate toDate,
    Integer providerFeeBps,
    Long feeToleranceMinor) {}
