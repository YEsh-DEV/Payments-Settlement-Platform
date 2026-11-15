package io.github.yesh.paycore.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.ledger.domain.*;
import io.github.yesh.paycore.ledger.dto.*;
import io.github.yesh.paycore.ledger.engine.PostingEngine;
import io.github.yesh.paycore.ledger.idempotency.IdempotencyService;
import io.github.yesh.paycore.ledger.repository.*;
import io.github.yesh.paycore.ledger.strategy.PostingStrategyRouter;
import io.github.yesh.paycore.ledger.strategy.S0PessimisticStrategy;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentAndRefundFlowTest {

  @Mock private SellerRepository sellerRepository;
  @Mock private AccountRepository accountRepository;
  @Mock private PaymentRepository paymentRepository;
  @Mock private RefundRepository refundRepository;
  @Mock private PostingEngine postingEngine;
  @Mock private IdempotencyRecordRepository idempotencyRecordRepository;

  private PaymentService paymentService;
  private RefundService refundService;
  private SellerService sellerService;

  @BeforeEach
  void setUp() {
    IdempotencyService idempotencyService =
        new IdempotencyService(idempotencyRecordRepository, new ObjectMapper());
    sellerService = new SellerService(sellerRepository, accountRepository);
    PostingStrategyRouter router =
        new PostingStrategyRouter(
            List.of(new S0PessimisticStrategy(postingEngine)), "S0_PESSIMISTIC");
    paymentService =
        new PaymentService(
            paymentRepository, sellerRepository, accountRepository, router, idempotencyService);
    refundService =
        new RefundService(
            refundRepository,
            paymentRepository,
            sellerRepository,
            accountRepository,
            router,
            idempotencyService);
  }

  @Test
  @DisplayName(
      "Complete flow: Seller onboarding -> Payment capture (200 bps fee) -> Partial refund")
  void testPaymentAndRefundFlow() {
    String sellerId = "sel_prime_merchant";
    Seller seller = new Seller(sellerId, "Prime Merchant", "STANDARD", 200); // 2.0% fee

    Account userFunds =
        new Account(SystemAccountInitializer.USER_FUNDS_IN_TRANSIT, AccountType.ASSET, true, 10);
    userFunds.setId(UUID.randomUUID());

    Account merchantAccount = new Account("MERCHANT_" + sellerId, AccountType.LIABILITY, false, 1);
    merchantAccount.setId(UUID.randomUUID());

    Account feeAccount =
        new Account(SystemAccountInitializer.PLATFORM_FEE_REVENUE, AccountType.REVENUE, true, 10);
    feeAccount.setId(UUID.randomUUID());

    when(sellerRepository.findBySellerId(sellerId)).thenReturn(Optional.of(seller));
    when(accountRepository.findByCode(SystemAccountInitializer.USER_FUNDS_IN_TRANSIT))
        .thenReturn(Optional.of(userFunds));
    when(accountRepository.findByCode("MERCHANT_" + sellerId))
        .thenReturn(Optional.of(merchantAccount));
    when(accountRepository.findByCode(SystemAccountInitializer.PLATFORM_FEE_REVENUE))
        .thenReturn(Optional.of(feeAccount));

    // 1. Capture Payment of Rs 1000.00 (100,000 paise)
    // Fee = 2.0% of 100000 = 2000 paise (Rs 20.00)
    // Net = 98000 paise (Rs 980.00)
    Journal paymentJournal = new Journal("pay_idemp", "pay_1", JournalType.PAYMENT, "desc");
    paymentJournal.setId(UUID.randomUUID());
    when(postingEngine.post(any())).thenReturn(new PostingResult(paymentJournal, List.of()));
    when(paymentRepository.findByPaymentId("pay_1")).thenReturn(Optional.empty());
    when(paymentRepository.save(any(Payment.class)))
        .thenAnswer(
            inv -> {
              Payment p = inv.getArgument(0);
              return p;
            });

    CapturePaymentRequest payReq = new CapturePaymentRequest("pay_1", sellerId, 100000L);
    PaymentResponse payResp = paymentService.capturePayment(payReq, null);

    assertThat(payResp.grossPaise()).isEqualTo(100000L);
    assertThat(payResp.feePaise()).isEqualTo(2000L);
    assertThat(payResp.netPaise()).isEqualTo(98000L);
    assertThat(payResp.status()).isEqualTo("CAPTURED");

    // 2. Partial Refund of Rs 400.00 (40,000 paise)
    // Proportional fee = 2.0% of 40000 = 800 paise
    // Proportional net = 39200 paise
    Payment capturedPayment =
        new Payment(
            "pay_1",
            sellerId,
            100000L,
            2000L,
            98000L,
            PaymentStatus.CAPTURED,
            paymentJournal.getId());
    when(paymentRepository.findByPaymentId("pay_1")).thenReturn(Optional.of(capturedPayment));
    when(refundRepository.findByPaymentId("pay_1")).thenReturn(List.of());
    when(refundRepository.findByRefundId("ref_1")).thenReturn(Optional.empty());

    Journal refundJournal = new Journal("ref_idemp", "ref_1", JournalType.REFUND, "desc");
    refundJournal.setId(UUID.randomUUID());
    when(postingEngine.post(any())).thenReturn(new PostingResult(refundJournal, List.of()));
    when(refundRepository.save(any(Refund.class))).thenAnswer(inv -> inv.getArgument(0));

    RefundRequest refReq = new RefundRequest("ref_1", "pay_1", 40000L, "Customer return");
    RefundResponse refResp = refundService.processRefund(refReq, null);

    assertThat(refResp.amountPaise()).isEqualTo(40000L);
    assertThat(refResp.status()).isEqualTo("SUCCEEDED");
    assertThat(capturedPayment.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
  }
}
