package io.github.yesh.paycore.ledger.dto;

import jakarta.validation.constraints.Min;

public record PaymentRefundRequest(
    String refundId,
    @Min(value = 1, message = "Refund amount must be at least 1 paise") long amountPaise,
    String reason) {}
