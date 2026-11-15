package io.github.yesh.paycore.ledger.dto;

import java.time.Instant;
import java.util.UUID;

public record PayoutResponse(
    UUID id,
    String payoutId,
    String sellerId,
    long amountPaise,
    String status,
    String batchId,
    UUID journalId,
    Instant createdAt) {}
