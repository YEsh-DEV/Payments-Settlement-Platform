package io.github.yesh.paycore.ledger.dto;

import java.time.Instant;
import java.util.UUID;

public record HoldResponse(
    UUID id,
    String holdId,
    UUID accountId,
    String accountCode,
    long amountPaise,
    String reason,
    String status,
    Instant expiresAt,
    Instant createdAt) {}
