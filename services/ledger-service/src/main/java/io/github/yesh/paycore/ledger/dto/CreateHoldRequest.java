package io.github.yesh.paycore.ledger.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateHoldRequest(
    @NotBlank(message = "Hold ID is required") String holdId,
    @NotBlank(message = "Account code is required") String accountCode,
    @Min(value = 1, message = "Amount must be at least 1 paise") long amountPaise,
    String reason,
    @Min(value = 1, message = "TTL must be at least 1 second") long ttlSeconds) {}
