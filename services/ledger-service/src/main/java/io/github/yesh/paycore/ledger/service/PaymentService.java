package io.github.yesh.paycore.ledger.service;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.common.model.Money;
import io.github.yesh.paycore.ledger.domain.Account;
import io.github.yesh.paycore.ledger.domain.JournalType;
import io.github.yesh.paycore.ledger.domain.Payment;
import io.github.yesh.paycore.ledger.domain.PaymentStatus;
import io.github.yesh.paycore.ledger.domain.Seller;
import io.github.yesh.paycore.ledger.dto.CapturePaymentRequest;
import io.github.yesh.paycore.ledger.dto.JournalLeg;
import io.github.yesh.paycore.ledger.dto.JournalRequest;
import io.github.yesh.paycore.ledger.dto.PaymentResponse;
import io.github.yesh.paycore.ledger.dto.PostingResult;
import io.github.yesh.paycore.ledger.idempotency.IdempotencyService;
import io.github.yesh.paycore.ledger.repository.AccountRepository;
import io.github.yesh.paycore.ledger.repository.PaymentRepository;
import io.github.yesh.paycore.ledger.repository.SellerRepository;
import io.github.yesh.paycore.ledger.strategy.PostingStrategyRouter;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {

  private final PaymentRepository paymentRepository;
  private final SellerRepository sellerRepository;
  private final AccountRepository accountRepository;
  private final PostingStrategyRouter strategyRouter;
  private final IdempotencyService idempotencyService;

  public PaymentService(
      PaymentRepository paymentRepository,
      SellerRepository sellerRepository,
      AccountRepository accountRepository,
      PostingStrategyRouter strategyRouter,
      IdempotencyService idempotencyService) {
    this.paymentRepository = paymentRepository;
    this.sellerRepository = sellerRepository;
    this.accountRepository = accountRepository;
    this.strategyRouter = strategyRouter;
    this.idempotencyService = idempotencyService;
  }

  public PaymentResponse capturePayment(CapturePaymentRequest request, String idempotencyKey) {
    return idempotencyService.execute(
        idempotencyKey, request, () -> doCapture(request, idempotencyKey), PaymentResponse.class);
  }

  @Transactional
  public PaymentResponse doCapture(CapturePaymentRequest request, String idempotencyKey) {
    Optional<Payment> existing = paymentRepository.findByPaymentId(request.paymentId());
    if (existing.isPresent()) {
      Payment p = existing.get();
      return new PaymentResponse(
          p.getId(),
          p.getPaymentId(),
          p.getSellerId(),
          p.getGrossPaise(),
          p.getFeePaise(),
          p.getNetPaise(),
          p.getStatus().name(),
          p.getJournalId(),
          p.getCreatedAt());
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

    Account userFundsAccount =
        accountRepository
            .findByCode(SystemAccountInitializer.USER_FUNDS_IN_TRANSIT)
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "account_missing",
                        "System account missing"));

    Account merchantAccount =
        accountRepository
            .findByCode("MERCHANT_" + seller.getSellerId())
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND,
                        "account_missing",
                        "Merchant account missing for " + seller.getSellerId()));

    Account feeAccount =
        accountRepository
            .findByCode(SystemAccountInitializer.PLATFORM_FEE_REVENUE)
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "account_missing",
                        "Fee account missing"));

    Money gross = Money.ofPaise(request.grossPaise());
    Money fee = gross.calculateFeeBps(seller.getFeeBps());
    Money net = gross.minus(fee);

    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(userFundsAccount.getId(), gross),
            JournalLeg.credit(merchantAccount.getId(), net),
            JournalLeg.credit(feeAccount.getId(), fee));

    String journalKey = idempotencyKey != null ? idempotencyKey : "pay_jrn_" + request.paymentId();
    JournalRequest journalRequest =
        new JournalRequest(
            journalKey,
            request.paymentId(),
            JournalType.PAYMENT,
            "Payment capture for " + request.paymentId(),
            legs);

    PostingResult postingResult = strategyRouter.getActiveStrategy().post(journalRequest);

    Payment payment =
        new Payment(
            request.paymentId(),
            seller.getSellerId(),
            gross.paise(),
            fee.paise(),
            net.paise(),
            PaymentStatus.CAPTURED,
            postingResult.journal().getId());

    Payment saved = paymentRepository.save(payment);

    return new PaymentResponse(
        saved.getId(),
        saved.getPaymentId(),
        saved.getSellerId(),
        saved.getGrossPaise(),
        saved.getFeePaise(),
        saved.getNetPaise(),
        saved.getStatus().name(),
        saved.getJournalId(),
        saved.getCreatedAt());
  }

  @Transactional(readOnly = true)
  public PaymentResponse getPayment(String paymentId) {
    Payment p =
        paymentRepository
            .findByPaymentId(paymentId)
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND,
                        "payment_not_found",
                        "Payment " + paymentId + " not found"));

    return new PaymentResponse(
        p.getId(),
        p.getPaymentId(),
        p.getSellerId(),
        p.getGrossPaise(),
        p.getFeePaise(),
        p.getNetPaise(),
        p.getStatus().name(),
        p.getJournalId(),
        p.getCreatedAt());
  }
}
