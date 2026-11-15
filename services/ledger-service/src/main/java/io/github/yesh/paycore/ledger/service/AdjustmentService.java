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
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdjustmentService {

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
      // Platform absorbs expense and credits merchant
      legs =
          List.of(
              JournalLeg.debit(adjustmentExpense.getId(), amount),
              JournalLeg.credit(merchantAccount.getId(), amount));
    } else {
      // Debits merchant and credits adjustment expense
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
        request.direction().toUpperCase(),
        amount.paise(),
        request.reason(),
        request.referenceId(),
        result.journal().getPostedAt());
  }
}
