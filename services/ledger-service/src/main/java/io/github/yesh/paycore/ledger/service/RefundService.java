package io.github.yesh.paycore.ledger.service;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.common.model.Money;
import io.github.yesh.paycore.ledger.domain.*;
import io.github.yesh.paycore.ledger.dto.JournalLeg;
import io.github.yesh.paycore.ledger.dto.JournalRequest;
import io.github.yesh.paycore.ledger.dto.PostingResult;
import io.github.yesh.paycore.ledger.dto.RefundRequest;
import io.github.yesh.paycore.ledger.dto.RefundResponse;
import io.github.yesh.paycore.ledger.idempotency.IdempotencyService;
import io.github.yesh.paycore.ledger.repository.*;
import io.github.yesh.paycore.ledger.strategy.PostingStrategyRouter;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundService {

  private final RefundRepository refundRepository;
  private final PaymentRepository paymentRepository;
  private final SellerRepository sellerRepository;
  private final AccountRepository accountRepository;
  private final PostingStrategyRouter strategyRouter;
  private final IdempotencyService idempotencyService;

  public RefundService(
      RefundRepository refundRepository,
      PaymentRepository paymentRepository,
      SellerRepository sellerRepository,
      AccountRepository accountRepository,
      PostingStrategyRouter strategyRouter,
      IdempotencyService idempotencyService) {
    this.refundRepository = refundRepository;
    this.paymentRepository = paymentRepository;
    this.sellerRepository = sellerRepository;
    this.accountRepository = accountRepository;
    this.strategyRouter = strategyRouter;
    this.idempotencyService = idempotencyService;
  }

  public RefundResponse processRefund(RefundRequest request, String idempotencyKey) {
    return idempotencyService.execute(
        idempotencyKey, request, () -> doRefund(request, idempotencyKey), RefundResponse.class);
  }

  @Transactional
  public RefundResponse doRefund(RefundRequest request, String idempotencyKey) {
    Optional<Refund> existing = refundRepository.findByRefundId(request.refundId());
    if (existing.isPresent()) {
      Refund r = existing.get();
      return new RefundResponse(
          r.getId(),
          r.getRefundId(),
          r.getPaymentId(),
          r.getAmountPaise(),
          r.getStatus().name(),
          r.getJournalId(),
          r.getCreatedAt());
    }

    Payment payment =
        paymentRepository
            .findByPaymentId(request.paymentId())
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND,
                        "payment_not_found",
                        "Payment " + request.paymentId() + " not found"));

    if (payment.getStatus() == PaymentStatus.REFUNDED) {
      throw new PayCoreException(
          HttpStatus.BAD_REQUEST, "already_refunded", "Payment is already fully refunded");
    }

    List<Refund> existingRefunds = refundRepository.findByPaymentId(payment.getPaymentId());
    long totalRefundedSoFar =
        existingRefunds.stream()
            .filter(r -> r.getStatus() == RefundStatus.SUCCEEDED)
            .mapToLong(Refund::getAmountPaise)
            .sum();

    long maxRefundable = payment.getGrossPaise() - totalRefundedSoFar;
    if (request.amountPaise() > maxRefundable) {
      throw new PayCoreException(
          HttpStatus.BAD_REQUEST,
          "refund_exceeds_gross",
          "Requested refund amount ("
              + request.amountPaise()
              + ") exceeds refundable balance ("
              + maxRefundable
              + ")");
    }

    Seller seller =
        sellerRepository
            .findBySellerId(payment.getSellerId())
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.NOT_FOUND, "seller_not_found", "Seller not found"));

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

    Money refundGross = Money.ofPaise(request.amountPaise());
    Money refundFee = refundGross.calculateFeeBps(seller.getFeeBps());
    Money refundNet = refundGross.minus(refundFee);

    List<JournalLeg> legs =
        List.of(
            JournalLeg.debit(merchantAccount.getId(), refundNet),
            JournalLeg.debit(feeAccount.getId(), refundFee),
            JournalLeg.credit(userFundsAccount.getId(), refundGross));

    String journalKey = idempotencyKey != null ? idempotencyKey : "ref_jrn_" + request.refundId();
    JournalRequest journalRequest =
        new JournalRequest(
            journalKey,
            request.refundId(),
            JournalType.REFUND,
            "Refund for "
                + payment.getPaymentId()
                + (request.reason() != null ? ": " + request.reason() : ""),
            legs);

    PostingResult postingResult = strategyRouter.getActiveStrategy().post(journalRequest);

    Refund refund =
        new Refund(
            request.refundId(),
            payment.getPaymentId(),
            refundGross.paise(),
            request.reason(),
            RefundStatus.SUCCEEDED,
            postingResult.journal().getId());
    Refund savedRefund = refundRepository.save(refund);

    long newTotalRefunded = totalRefundedSoFar + refundGross.paise();
    if (newTotalRefunded == payment.getGrossPaise()) {
      payment.setStatus(PaymentStatus.REFUNDED);
    } else {
      payment.setStatus(PaymentStatus.PARTIALLY_REFUNDED);
    }
    paymentRepository.save(payment);

    return new RefundResponse(
        savedRefund.getId(),
        savedRefund.getRefundId(),
        savedRefund.getPaymentId(),
        savedRefund.getAmountPaise(),
        savedRefund.getStatus().name(),
        savedRefund.getJournalId(),
        savedRefund.getCreatedAt());
  }
}
