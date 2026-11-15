package io.github.yesh.paycore.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.Instant;

public record SettlementCandidateRequest(
    String eventId,
    String source,
    @NotBlank String paymentId,
    @NotBlank String sellerId,
    String providerTxnId,
    String orderId,
    String kind,
    @PositiveOrZero long grossPaise,
    @PositiveOrZero long feePaise,
    @PositiveOrZero long netPaise,
    Instant occurredAt) {}
