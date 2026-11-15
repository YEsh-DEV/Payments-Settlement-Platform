package io.github.yesh.paycore.settlement.dto;

import io.github.yesh.paycore.settlement.domain.CaseStatus;
import jakarta.validation.constraints.NotNull;

public record CaseTransitionRequest(
    @NotNull CaseStatus newStatus, @NotNull Long version, String comment) {}
