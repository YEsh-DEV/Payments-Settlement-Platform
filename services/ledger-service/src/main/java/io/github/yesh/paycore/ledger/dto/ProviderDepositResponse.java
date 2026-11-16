package io.github.yesh.paycore.ledger.dto;

import java.time.Instant;
import java.util.UUID;

public record ProviderDepositResponse(
    String depositId,
    UUID journalId,
    long netAmount,
    long feeAmount,
    long grossAmount,
    String reference,
    Instant postedAt) {}
