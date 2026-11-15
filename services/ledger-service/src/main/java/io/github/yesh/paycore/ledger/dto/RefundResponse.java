package io.github.yesh.paycore.ledger.dto;

import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
    UUID id,
    String refundId,
    String paymentId,
    long amountPaise,
    String status,
    UUID journalId,
    Instant createdAt) {}
