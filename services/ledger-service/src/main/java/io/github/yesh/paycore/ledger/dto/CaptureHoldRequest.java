package io.github.yesh.paycore.ledger.dto;

import jakarta.validation.constraints.NotBlank;

public record CaptureHoldRequest(
    @NotBlank(message = "Destination account code is required") String destinationAccountCode,
    String idempotencyKey) {}
