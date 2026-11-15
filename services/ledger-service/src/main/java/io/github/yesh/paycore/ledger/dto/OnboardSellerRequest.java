package io.github.yesh.paycore.ledger.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record OnboardSellerRequest(
    @NotBlank(message = "Seller ID is required") String sellerId,
    @NotBlank(message = "Seller name is required") String name,
    String feeTier,
    @Min(0) @Max(10000) Integer feeBps) {}
