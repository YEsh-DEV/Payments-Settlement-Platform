package io.github.yesh.paycore.ledger.dto;

import java.util.UUID;

public record SellerResponse(
    UUID id,
    String sellerId,
    String name,
    String feeTier,
    int feeBps,
    UUID accountId,
    String accountCode) {}
