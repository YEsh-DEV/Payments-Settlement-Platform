package io.github.yesh.paycore.ledger.dto;

import io.github.yesh.paycore.common.model.Money;
import io.github.yesh.paycore.ledger.domain.EntryDirection;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record JournalLeg(
    @NotNull UUID accountId, @NotNull EntryDirection direction, @NotNull Money amount) {

  public static JournalLeg debit(UUID accountId, Money amount) {
    return new JournalLeg(accountId, EntryDirection.DR, amount);
  }

  public static JournalLeg credit(UUID accountId, Money amount) {
    return new JournalLeg(accountId, EntryDirection.CR, amount);
  }
}
