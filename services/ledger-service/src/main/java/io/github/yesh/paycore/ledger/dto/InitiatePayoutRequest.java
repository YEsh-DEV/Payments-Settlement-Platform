package io.github.yesh.paycore.ledger.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record InitiatePayoutRequest(
    @NotBlank(message = "Payout ID is required") String payoutId,
    @NotBlank(message = "Seller ID is required") String sellerId,
    @Min(value = 1, message = "Amount must be at least 1 paise") long amountPaise) {}
