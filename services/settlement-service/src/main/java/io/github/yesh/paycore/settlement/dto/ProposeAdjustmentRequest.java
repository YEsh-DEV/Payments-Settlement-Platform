package io.github.yesh.paycore.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record ProposeAdjustmentRequest(
    @NotBlank String caseId,
    @NotBlank String template, // FEE_VARIANCE or WRITE_OFF
    @Positive long amountPaise,
    String comment) {}
