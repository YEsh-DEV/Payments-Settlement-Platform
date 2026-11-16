package io.github.yesh.paycore.ledger.service;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.common.model.Money;
import io.github.yesh.paycore.ledger.domain.Account;
import io.github.yesh.paycore.ledger.domain.JournalType;
import io.github.yesh.paycore.ledger.dto.CreateProviderDepositRequest;
import io.github.yesh.paycore.ledger.dto.JournalLeg;
import io.github.yesh.paycore.ledger.dto.JournalRequest;
import io.github.yesh.paycore.ledger.dto.PostingResult;
import io.github.yesh.paycore.ledger.dto.ProviderDepositResponse;
import io.github.yesh.paycore.ledger.idempotency.IdempotencyService;
import io.github.yesh.paycore.ledger.repository.AccountRepository;
import io.github.yesh.paycore.ledger.strategy.PostingStrategyRouter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProviderDepositService {

  private final AccountRepository accountRepository;
  private final PostingStrategyRouter strategyRouter;
  private final IdempotencyService idempotencyService;

  public ProviderDepositService(
      AccountRepository accountRepository,
      PostingStrategyRouter strategyRouter,
      IdempotencyService idempotencyService) {
    this.accountRepository = accountRepository;
    this.strategyRouter = strategyRouter;
    this.idempotencyService = idempotencyService;
  }

  public ProviderDepositResponse postDeposit(
      CreateProviderDepositRequest request, String idempotencyKey) {
    String scope = idempotencyService.resolveCurrentScope();
    String key =
        (idempotencyKey != null && !idempotencyKey.isBlank())
            ? idempotencyKey
            : "deposit:" + request.depositId();

    return idempotencyService.execute(
        scope,
        key,
        request,
        () -> doPostDeposit(request, key, scope),
        ProviderDepositResponse.class);
  }

  @Transactional
  public ProviderDepositResponse doPostDeposit(
      CreateProviderDepositRequest request, String idempotencyKey, String scope) {
    Account bankCash =
        accountRepository
            .findByCode(SystemAccountInitializer.BANK_CASH)
            .or(() -> accountRepository.findByCode(SystemAccountInitializer.USER_FUNDS_IN_TRANSIT))
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "account_missing",
                        "Bank cash system account missing"));

    Account providerReceivable =
        accountRepository
            .findByCode(SystemAccountInitializer.PROVIDER_RECEIVABLE)
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "account_missing",
                        "Provider receivable system account missing"));

    Money netMoney = Money.ofPaise(request.netAmount());
    Money feeMoney = Money.ofPaise(request.feeAmount());
    Money grossMoney = Money.ofPaise(request.grossAmount());

    List<JournalLeg> legs = new ArrayList<>();
    legs.add(JournalLeg.debit(bankCash.getId(), netMoney));

    if (request.feeAmount() > 0) {
      Account feeExpense =
          accountRepository
              .findByCode(SystemAccountInitializer.PROVIDER_FEE_EXPENSE)
              .or(() -> accountRepository.findByCode(SystemAccountInitializer.ADJUSTMENT_EXPENSE))
              .orElseThrow(
                  () ->
                      new PayCoreException(
                          HttpStatus.INTERNAL_SERVER_ERROR,
                          "account_missing",
                          "Provider fee expense system account missing"));
      legs.add(JournalLeg.debit(feeExpense.getId(), feeMoney));
    }

    legs.add(JournalLeg.credit(providerReceivable.getId(), grossMoney));

    String jrnKey = idempotencyKey != null ? idempotencyKey : "deposit:" + request.depositId();
    JournalRequest journalRequest =
        new JournalRequest(
            scope,
            jrnKey,
            request.depositId(),
            JournalType.DEPOSIT,
            "Provider deposit settlement: "
                + (request.reference() != null ? request.reference() : request.depositId()),
            legs);

    PostingResult postingResult = strategyRouter.getActiveStrategy().post(journalRequest);

    ProviderDepositResponse response =
        new ProviderDepositResponse(
            request.depositId(),
            postingResult.journal().getId(),
            request.netAmount(),
            request.feeAmount(),
            request.grossAmount(),
            request.reference(),
            postingResult.journal().getPostedAt());

    idempotencyService.completeKey(
        scope, jrnKey, idempotencyService.serialize(response), 201);

    return response;
  }
}
