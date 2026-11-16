package io.github.yesh.paycore.ledger.dto;

import io.github.yesh.paycore.ledger.domain.JournalType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record JournalRequest(
    String scope,
    @NotBlank String idempotencyKey,
    String referenceId,
    @NotNull JournalType type,
    String description,
    @NotEmpty List<JournalLeg> legs) {

  public JournalRequest(
      String idempotencyKey,
      String referenceId,
      JournalType type,
      String description,
      List<JournalLeg> legs) {
    this("global", idempotencyKey, referenceId, type, description, legs);
  }
}
