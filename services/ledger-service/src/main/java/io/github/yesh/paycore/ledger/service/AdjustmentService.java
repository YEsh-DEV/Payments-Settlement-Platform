package io.github.yesh.paycore.ledger.service;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.common.model.Money;
import io.github.yesh.paycore.ledger.domain.Account;
import io.github.yesh.paycore.ledger.domain.JournalType;
import io.github.yesh.paycore.ledger.domain.Seller;
import io.github.yesh.paycore.ledger.dto.AdjustmentResponse;
import io.github.yesh.paycore.ledger.dto.CreateAdjustmentRequest;
import io.github.yesh.paycore.ledger.dto.JournalLeg;
import io.github.yesh.paycore.ledger.dto.JournalRequest;
import io.github.yesh.paycore.ledger.dto.PostingResult;
import io.github.yesh.paycore.ledger.repository.AccountRepository;
import io.github.yesh.paycore.ledger.repository.SellerRepository;
import io.github.yesh.paycore.ledger.strategy.PostingStrategyRouter;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdjustmentService {

  private static final Set<String> ALLOWED_SYSTEM_ACCOUNTS =
      Set.of(
          SystemAccountInitializer.PROVIDER_RECEIVABLE,
          SystemAccountInitializer.PROVIDER_FEE_EXPENSE,
          SystemAccountInitializer.WRITE_OFF_EXPENSE,
          SystemAccountInitializer.BANK_CASH,
          SystemAccountInitializer.ADJUSTMENT_EXPENSE);

  private final AccountRepository accountRepository;
  private final SellerRepository sellerRepository;
  private final PostingStrategyRouter strategyRouter;

  public AdjustmentService(
      AccountRepository accountRepository,
      SellerRepository sellerRepository,
      PostingStrategyRouter strategyRouter) {
    this.accountRepository = accountRepository;
    this.sellerRepository = sellerRepository;
    this.strategyRouter = strategyRouter;
  }

  @Transactional
  public AdjustmentResponse postAdjustment(CreateAdjustmentRequest request) {
    if (request.amountPaise() == null || request.amountPaise() < 1) {
      throw new PayCoreException(
          HttpStatus.BAD_REQUEST, "invalid_amount", "Adjustment amount must be at least 1 paise");
    }

    // Playbook 8.3 / WP-F path: system account to system account
    if (request.debitAccount() != null || request.creditAccount() != null) {
      String debitCode = request.debitAccount();
      String creditCode = request.creditAccount();

      if (debitCode == null || creditCode == null) {
        throw new PayCoreException(
            HttpStatus.BAD_REQUEST,
            "invalid_accounts",
            "Both debitAccount and creditAccount are required");
      }

      // Playbook 8.3 / WP-F Rule: Only system accounts are accepted; seller accounts are rejected
      if (debitCode.startsWith("SELLER_")
          || debitCode.startsWith("MERCHANT_")
          || creditCode.startsWith("SELLER_")
          || creditCode.startsWith("MERCHANT_")
          || !ALLOWED_SYSTEM_ACCOUNTS.contains(debitCode)
          || !ALLOWED_SYSTEM_ACCOUNTS.contains(creditCode)) {
        throw new PayCoreException(
            HttpStatus.UNPROCESSABLE_ENTITY,
            "seller_accounts_not_supported",
            "Only system accounts are accepted for administrative adjustments; seller accounts are rejected");
      }

      Account debitAcc =
          accountRepository
              .findByCode(debitCode)
              .orElseThrow(
                  () ->
                      new PayCoreException(
                          HttpStatus.INTERNAL_SERVER_ERROR,
                          "account_missing",
                          "Debit system account missing: " + debitCode));

      Account creditAcc =
          accountRepository
              .findByCode(creditCode)
              .orElseThrow(
                  () ->
                      new PayCoreException(
                          HttpStatus.INTERNAL_SERVER_ERROR,
                          "account_missing",
                          "Credit system account missing: " + creditCode));

      Money amount = Money.ofPaise(request.amountPaise());
      List<JournalLeg> legs =
          List.of(
              JournalLeg.debit(debitAcc.getId(), amount),
              JournalLeg.credit(creditAcc.getId(), amount));

      String adjId =
          request.adjustmentId() != null
              ? request.adjustmentId()
              : "adj_" + UUID.randomUUID().toString().replace("-", "");
      String ref =
          request.caseId() != null
              ? request.caseId()
              : (request.referenceId() != null ? request.referenceId() : adjId);

      JournalRequest journalRequest =
          new JournalRequest(
              "adjustment:" + adjId,
              ref,
              JournalType.ADJUSTMENT,
              "System adjustment: " + (request.reason() != null ? request.reason() : adjId),
              legs);

      PostingResult result = strategyRouter.getActiveStrategy().post(journalRequest);

      return new AdjustmentResponse(
          result.journal().getId(),
          null,
          debitCode + " -> " + creditCode,
          amount.paise(),
          request.reason(),
          ref,
          result.journal().getPostedAt());
    }

    // Legacy seller adjustment path
    if (request.sellerId() == null) {
      throw new PayCoreException(
          HttpStatus.BAD_REQUEST, "invalid_request", "Seller ID or debit/credit accounts required");
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

    Account adjustmentExpense =
        accountRepository
            .findByCode(SystemAccountInitializer.ADJUSTMENT_EXPENSE)
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "account_missing",
                        "Adjustment expense account missing"));

    Money amount = Money.ofPaise(request.amountPaise());
    List<JournalLeg> legs;

    if ("CREDIT".equalsIgnoreCase(request.direction())) {
      legs =
          List.of(
              JournalLeg.debit(adjustmentExpense.getId(), amount),
              JournalLeg.credit(merchantAccount.getId(), amount));
    } else {
      legs =
          List.of(
              JournalLeg.debit(merchantAccount.getId(), amount),
              JournalLeg.credit(adjustmentExpense.getId(), amount));
    }

    String jrnKey = "adj_" + UUID.randomUUID().toString().replace("-", "");
    JournalRequest journalRequest =
        new JournalRequest(
            jrnKey,
            request.referenceId(),
            JournalType.ADJUSTMENT,
            "Operational adjustment: " + request.reason(),
            legs);

    PostingResult result = strategyRouter.getActiveStrategy().post(journalRequest);

    return new AdjustmentResponse(
        result.journal().getId(),
        seller.getSellerId(),
        request.direction() != null ? request.direction().toUpperCase() : "UNKNOWN",
        amount.paise(),
        request.reason(),
        request.referenceId(),
        result.journal().getPostedAt());
  }
}
