package io.github.yesh.paycore.ledger.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateAdjustmentRequest(
    String adjustmentId,
    String sellerId,
    String direction,
    String debitAccount,
    String creditAccount,
    @NotNull(message = "Amount is required")
        @Min(value = 1, message = "Amount must be at least 1 paise")
        Long amountPaise,
    String reason,
    String referenceId,
    String caseId) {

  // Constructor for legacy tests
  public CreateAdjustmentRequest(
      String sellerId, String direction, long amountPaise, String reason, String referenceId) {
    this(null, sellerId, direction, null, null, amountPaise, reason, referenceId, null);
  }

  // Constructor for Playbook Section 8.3 system accounts
  public CreateAdjustmentRequest(
      String adjustmentId,
      String debitAccount,
      String creditAccount,
      long amountPaise,
      String caseId,
      String reason) {
    this(
        adjustmentId,
        null,
        null,
        debitAccount,
        creditAccount,
        amountPaise,
        reason,
        caseId,
        caseId);
  }
}
