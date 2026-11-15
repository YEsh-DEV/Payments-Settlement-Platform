package io.github.yesh.paycore.ledger.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RefundRequest(
    @NotBlank(message = "Refund ID is required") String refundId,
    @NotBlank(message = "Payment ID is required") String paymentId,
    @Min(value = 1, message = "Refund amount must be at least 1 paise") long amountPaise,
    String reason) {}
