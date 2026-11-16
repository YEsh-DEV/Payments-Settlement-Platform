package io.github.yesh.paycore.ledger.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

public record CreateProviderDepositRequest(
    @NotBlank(message = "Deposit ID is required") String depositId,
    @Min(value = 1, message = "Net amount must be at least 1 paise") long netAmount,
    @Min(value = 0, message = "Fee amount cannot be negative") long feeAmount,
    String reference,
    LocalDate valueDate) {

  public long grossAmount() {
    return netAmount + feeAmount;
  }
}
