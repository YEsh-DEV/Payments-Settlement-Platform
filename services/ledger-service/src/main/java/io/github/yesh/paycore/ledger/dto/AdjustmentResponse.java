package io.github.yesh.paycore.ledger.dto;

import java.time.Instant;
import java.util.UUID;

public record AdjustmentResponse(
    UUID journalId,
    String sellerId,
    String direction,
    long amountPaise,
    String reason,
    String referenceId,
    Instant postedAt) {}
