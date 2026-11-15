package io.github.yesh.paycore.settlement.dto;

import jakarta.validation.constraints.NotBlank;

public record CaseNoteRequest(@NotBlank String content) {}
