package io.github.yesh.paycore.ledger.dto;

import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID id,
    String paymentId,
    String sellerId,
    long grossPaise,
    long feePaise,
    long netPaise,
    String status,
    UUID journalId,
    Instant createdAt) {}
