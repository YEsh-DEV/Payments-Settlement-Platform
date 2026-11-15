package io.github.yesh.paycore.ledger.service;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.common.model.Money;
import io.github.yesh.paycore.ledger.domain.Account;
import io.github.yesh.paycore.ledger.domain.Hold;
import io.github.yesh.paycore.ledger.domain.HoldStatus;
import io.github.yesh.paycore.ledger.domain.JournalType;
import io.github.yesh.paycore.ledger.dto.CaptureHoldRequest;
import io.github.yesh.paycore.ledger.dto.CreateHoldRequest;
import io.github.yesh.paycore.ledger.dto.HoldResponse;
import io.github.yesh.paycore.ledger.dto.JournalLeg;
import io.github.yesh.paycore.ledger.dto.JournalRequest;
import io.github.yesh.paycore.ledger.repository.AccountRepository;
import io.github.yesh.paycore.ledger.repository.HoldRepository;
import io.github.yesh.paycore.ledger.strategy.PostingStrategyRouter;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HoldService {

  private final HoldRepository holdRepository;
  private final AccountRepository accountRepository;
  private final PostingStrategyRouter strategyRouter;

  public HoldService(
      HoldRepository holdRepository,
      AccountRepository accountRepository,
      PostingStrategyRouter strategyRouter) {
    this.holdRepository = holdRepository;
    this.accountRepository = accountRepository;
    this.strategyRouter = strategyRouter;
  }

  @Transactional
  public HoldResponse createHold(CreateHoldRequest request) {
    Optional<Hold> existing = holdRepository.findByHoldId(request.holdId());
    if (existing.isPresent()) {
      return toResponse(existing.get(), request.accountCode());
    }

    Account account =
        accountRepository
            .findByCodeForUpdate(request.accountCode())
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND,
                        "account_not_found",
                        "Account " + request.accountCode() + " not found"));

    if (account.getAvailableBalancePaise() < request.amountPaise()) {
      throw new PayCoreException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "insufficient_available_balance",
          "Insufficient available balance in account "
              + account.getCode()
              + " (available: "
              + account.getAvailableBalancePaise()
              + ", requested: "
              + request.amountPaise()
              + ")");
    }

    account.setLockedBalancePaise(account.getLockedBalancePaise() + request.amountPaise());
    accountRepository.save(account);

    Instant expiresAt = Instant.now().plusSeconds(request.ttlSeconds());
    Hold hold =
        new Hold(
            request.holdId(),
            account.getId(),
            request.amountPaise(),
            request.reason(),
            HoldStatus.ACTIVE,
            expiresAt);

    Hold saved = holdRepository.save(hold);
    return toResponse(saved, account.getCode());
  }

  @Transactional
  public HoldResponse releaseHold(String holdId) {
    Hold hold =
        holdRepository
            .findByHoldId(holdId)
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND, "hold_not_found", "Hold " + holdId + " not found"));

    if (hold.getStatus() != HoldStatus.ACTIVE) {
      Account account = accountRepository.findById(hold.getAccountId()).orElse(null);
      return toResponse(hold, account != null ? account.getCode() : "");
    }

    Account account =
        accountRepository
            .findByIdForUpdate(hold.getAccountId())
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND, "account_not_found", "Held account not found"));

    long newLocked = Math.max(0, account.getLockedBalancePaise() - hold.getAmountPaise());
    account.setLockedBalancePaise(newLocked);
    accountRepository.save(account);

    hold.setStatus(HoldStatus.RELEASED);
    Hold updated = holdRepository.save(hold);

    return toResponse(updated, account.getCode());
  }

  @Transactional
  public HoldResponse captureHold(String holdId, CaptureHoldRequest request) {
    Hold hold =
        holdRepository
            .findByHoldId(holdId)
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND, "hold_not_found", "Hold " + holdId + " not found"));

    if (hold.getStatus() != HoldStatus.ACTIVE) {
      throw new PayCoreException(
          HttpStatus.BAD_REQUEST, "hold_not_active", "Hold " + holdId + " is not active");
    }

    Account heldAccount =
        accountRepository
            .findByIdForUpdate(hold.getAccountId())
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND, "account_not_found", "Held account not found"));

    Account destAccount =
        accountRepository
            .findByCode(request.destinationAccountCode())
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND,
                        "account_not_found",
                        "Destination account not found"));

    // Unlock held balance
    heldAccount.setLockedBalancePaise(
        Math.max(0, heldAccount.getLockedBalancePaise() - hold.getAmountPaise()));
    accountRepository.save(heldAccount);

    // Post Journal debiting held account and crediting destination account
    Money amount = Money.ofPaise(hold.getAmountPaise());
    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(heldAccount.getId(), amount),
            JournalLeg.credit(destAccount.getId(), amount));

    String jrnKey =
        request.idempotencyKey() != null ? request.idempotencyKey() : "cap_jrn_" + holdId;
    JournalRequest jrnReq =
        new JournalRequest(jrnKey, holdId, JournalType.CAPTURE, "Capture hold " + holdId, legs);

    strategyRouter.getActiveStrategy().post(jrnReq);

    hold.setStatus(HoldStatus.CAPTURED);
    Hold saved = holdRepository.save(hold);

    return toResponse(saved, heldAccount.getCode());
  }

  @Transactional(readOnly = true)
  public HoldResponse getHold(String holdId) {
    Hold hold =
        holdRepository
            .findByHoldId(holdId)
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND, "hold_not_found", "Hold " + holdId + " not found"));
    Account account = accountRepository.findById(hold.getAccountId()).orElse(null);
    return toResponse(hold, account != null ? account.getCode() : "");
  }

  @Transactional(readOnly = true)
  public List<HoldResponse> getActiveHoldsForAccount(String accountCode) {
    Account account =
        accountRepository
            .findByCode(accountCode)
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND,
                        "account_not_found",
                        "Account " + accountCode + " not found"));

    return holdRepository.findByAccountIdAndStatus(account.getId(), HoldStatus.ACTIVE).stream()
        .map(h -> toResponse(h, account.getCode()))
        .collect(Collectors.toList());
  }

  private HoldResponse toResponse(Hold hold, String accountCode) {
    return new HoldResponse(
        hold.getId(),
        hold.getHoldId(),
        hold.getAccountId(),
        accountCode,
        hold.getAmountPaise(),
        hold.getReason(),
        hold.getStatus().name(),
        hold.getExpiresAt(),
        hold.getCreatedAt());
  }
}
