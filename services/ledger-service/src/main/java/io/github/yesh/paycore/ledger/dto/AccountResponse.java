package io.github.yesh.paycore.ledger.dto;

import io.github.yesh.paycore.ledger.domain.Account;
import io.github.yesh.paycore.ledger.domain.AccountType;
import java.util.UUID;

public record AccountResponse(
    UUID id,
    String code,
    AccountType type,
    String currency,
    long balancePaise,
    long lockedBalancePaise,
    long availableBalancePaise,
    boolean isHot) {

  public static AccountResponse from(Account a) {
    return new AccountResponse(
        a.getId(),
        a.getCode(),
        a.getType(),
        a.getCurrency(),
        a.getBalancePaise(),
        a.getLockedBalancePaise(),
        a.getAvailableBalancePaise(),
        a.isHot());
  }
}
