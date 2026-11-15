package io.github.yesh.paycore.ledger.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateAdjustmentRequest(
    @NotBlank(message = "Seller ID is required") String sellerId,
    @NotBlank @Pattern(regexp = "CREDIT|DEBIT", message = "Direction must be CREDIT or DEBIT")
        String direction,
    @Min(value = 1, message = "Amount must be at least 1 paise") long amountPaise,
    @NotBlank(message = "Audit reason is required") String reason,
    String referenceId) {}
