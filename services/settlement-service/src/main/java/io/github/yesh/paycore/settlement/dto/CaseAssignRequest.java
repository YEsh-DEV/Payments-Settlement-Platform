package io.github.yesh.paycore.settlement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CaseAssignRequest(@NotBlank String assignedTo, @NotNull Long version) {}
