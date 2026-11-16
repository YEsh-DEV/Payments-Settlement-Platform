package io.github.yesh.paycore.ledger.service;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.common.model.Money;
import io.github.yesh.paycore.ledger.domain.*;
import io.github.yesh.paycore.ledger.dto.InitiatePayoutRequest;
import io.github.yesh.paycore.ledger.dto.JournalLeg;
import io.github.yesh.paycore.ledger.dto.JournalRequest;
import io.github.yesh.paycore.ledger.dto.PayoutResponse;
import io.github.yesh.paycore.ledger.dto.PostingResult;
import io.github.yesh.paycore.ledger.idempotency.IdempotencyService;
import io.github.yesh.paycore.ledger.repository.AccountRepository;
import io.github.yesh.paycore.ledger.repository.PayoutRepository;
import io.github.yesh.paycore.ledger.repository.SellerRepository;
import io.github.yesh.paycore.ledger.strategy.PostingStrategyRouter;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PayoutService {

  private final PayoutRepository payoutRepository;
  private final SellerRepository sellerRepository;
  private final AccountRepository accountRepository;
  private final PostingStrategyRouter strategyRouter;
  private final IdempotencyService idempotencyService;

  public PayoutService(
      PayoutRepository payoutRepository,
      SellerRepository sellerRepository,
      AccountRepository accountRepository,
      PostingStrategyRouter strategyRouter,
      IdempotencyService idempotencyService) {
    this.payoutRepository = payoutRepository;
    this.sellerRepository = sellerRepository;
    this.accountRepository = accountRepository;
    this.strategyRouter = strategyRouter;
    this.idempotencyService = idempotencyService;
  }

  public PayoutResponse initiatePayout(InitiatePayoutRequest request, String idempotencyKey) {
    String scope = idempotencyService.resolveCurrentScope();
    return idempotencyService.execute(
        scope,
        idempotencyKey,
        request,
        () -> doInitiate(request, idempotencyKey, scope),
        PayoutResponse.class);
  }

  @Transactional
  public PayoutResponse doInitiate(InitiatePayoutRequest request, String idempotencyKey) {
    return doInitiate(request, idempotencyKey, "global");
  }

  @Transactional
  public PayoutResponse doInitiate(
      InitiatePayoutRequest request, String idempotencyKey, String scope) {
    Optional<Payout> existing = payoutRepository.findByPayoutId(request.payoutId());
    if (existing.isPresent()) {
      return toResponse(existing.get());
    }

    Seller seller =
        sellerRepository
            .findBySellerId(request.sellerId())
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND,
                        "seller_not_found",
                        "Seller " + request.sellerId() + " not found"));

    Account merchantAccount =
        accountRepository
            .findByCode("MERCHANT_" + seller.getSellerId())
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND, "account_missing", "Merchant account missing"));

    Account settlementPayable =
        accountRepository
            .findByCode(SystemAccountInitializer.PAYOUT_IN_TRANSIT)
            .or(() -> accountRepository.findByCode(SystemAccountInitializer.SETTLEMENT_PAYABLE))
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "account_missing",
                        "Payout in transit account missing"));

    if (merchantAccount.getAvailableBalancePaise() < request.amountPaise()) {
      throw new PayCoreException(
          HttpStatus.UNPROCESSABLE_ENTITY,
          "insufficient_funds",
          "Insufficient available funds for payout (available: "
              + merchantAccount.getAvailableBalancePaise()
              + ", requested: "
              + request.amountPaise()
              + ")");
    }

    Money amount = Money.ofPaise(request.amountPaise());
    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(merchantAccount.getId(), amount),
            JournalLeg.credit(settlementPayable.getId(), amount));

    String jrnKey = idempotencyKey != null ? idempotencyKey : "payout_init_" + request.payoutId();
    JournalRequest journalRequest =
        new JournalRequest(
            scope,
            jrnKey,
            request.payoutId(),
            JournalType.PAYOUT,
            "Payout initiation for " + seller.getSellerId(),
            legs);

    PostingResult postingResult = strategyRouter.getActiveStrategy().post(journalRequest);

    Payout payout =
        new Payout(
            request.payoutId(),
            seller.getSellerId(),
            amount.paise(),
            PayoutStatus.INITIATED,
            null,
            postingResult.journal().getId());

    Payout saved = payoutRepository.save(payout);
    PayoutResponse response = toResponse(saved);

    if (idempotencyKey != null && !idempotencyKey.isBlank()) {
      idempotencyService.completeKey(
          scope, idempotencyKey, idempotencyService.serialize(response), 201);
    }

    return response;
  }

  @Transactional
  public PayoutResponse confirmPayout(String payoutId, String batchId) {
    Payout payout =
        payoutRepository
            .findByPayoutId(payoutId)
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND,
                        "payout_not_found",
                        "Payout " + payoutId + " not found"));

    if (payout.getStatus() == PayoutStatus.PAID) {
      return toResponse(payout);
    }

    if (payout.getStatus() == PayoutStatus.FAILED) {
      throw new PayCoreException(
          HttpStatus.CONFLICT,
          "invalid_state_transition",
          "Cannot confirm payout " + payoutId + " in FAILED status");
    }

    Account settlementPayable =
        accountRepository
            .findByCode(SystemAccountInitializer.PAYOUT_IN_TRANSIT)
            .or(() -> accountRepository.findByCode(SystemAccountInitializer.SETTLEMENT_PAYABLE))
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "account_missing",
                        "Payout in transit account missing"));

    Account bankCash =
        accountRepository
            .findByCode(SystemAccountInitializer.BANK_CASH)
            .or(() -> accountRepository.findByCode(SystemAccountInitializer.USER_FUNDS_IN_TRANSIT))
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "account_missing",
                        "Bank cash account missing"));

    Money amount = Money.ofPaise(payout.getAmountPaise());
    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(settlementPayable.getId(), amount),
            JournalLeg.credit(bankCash.getId(), amount));

    JournalRequest journalRequest =
        new JournalRequest(
            "payout_confirm_" + payoutId,
            payoutId,
            JournalType.PAYOUT,
            "Payout bank confirmation for " + payoutId,
            legs);

    strategyRouter.getActiveStrategy().post(journalRequest);

    payout.setStatus(PayoutStatus.PAID);
    payout.setBatchId(batchId);
    Payout saved = payoutRepository.save(payout);

    return toResponse(saved);
  }

  @Transactional
  public PayoutResponse failPayout(String payoutId, String reason) {
    Payout payout =
        payoutRepository
            .findByPayoutId(payoutId)
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND,
                        "payout_not_found",
                        "Payout " + payoutId + " not found"));

    if (payout.getStatus() == PayoutStatus.FAILED) {
      return toResponse(payout);
    }

    if (payout.getStatus() == PayoutStatus.PAID) {
      throw new PayCoreException(
          HttpStatus.CONFLICT,
          "invalid_state_transition",
          "Cannot fail payout " + payoutId + " in PAID status");
    }

    Account settlementPayable =
        accountRepository
            .findByCode(SystemAccountInitializer.PAYOUT_IN_TRANSIT)
            .or(() -> accountRepository.findByCode(SystemAccountInitializer.SETTLEMENT_PAYABLE))
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "account_missing",
                        "Payout in transit account missing"));

    Account merchantAccount =
        accountRepository
            .findByCode("MERCHANT_" + payout.getSellerId())
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND, "account_missing", "Merchant account missing"));

    // Reverse funds back to merchant
    Money amount = Money.ofPaise(payout.getAmountPaise());
    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(settlementPayable.getId(), amount),
            JournalLeg.credit(merchantAccount.getId(), amount));

    JournalRequest journalRequest =
        new JournalRequest(
            "payout_reversal_" + payoutId,
            payoutId,
            JournalType.PAYOUT,
            "Payout failure reversal: " + (reason != null ? reason : "Bank rejection"),
            legs);

    strategyRouter.getActiveStrategy().post(journalRequest);

    payout.setStatus(PayoutStatus.FAILED);
    Payout saved = payoutRepository.save(payout);

    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public PayoutResponse getPayout(String payoutId) {
    Payout payout =
        payoutRepository
            .findByPayoutId(payoutId)
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND,
                        "payout_not_found",
                        "Payout " + payoutId + " not found"));
    return toResponse(payout);
  }

  private PayoutResponse toResponse(Payout p) {
    return new PayoutResponse(
        p.getId(),
        p.getPayoutId(),
        p.getSellerId(),
        p.getAmountPaise(),
        p.getStatus().name(),
        p.getBatchId(),
        p.getJournalId(),
        p.getCreatedAt());
  }
}
