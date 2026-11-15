package io.github.yesh.paycore.ledger.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CapturePaymentRequest(
    @NotBlank(message = "Payment ID is required") String paymentId,
    @NotBlank(message = "Seller ID is required") String sellerId,
    @Min(value = 1, message = "Gross amount must be at least 1 paise") long grossPaise) {}
