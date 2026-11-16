package io.github.yesh.paycore.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.ledger.domain.Payment;
import io.github.yesh.paycore.ledger.dto.CapturePaymentRequest;
import io.github.yesh.paycore.ledger.dto.PaymentResponse;
import io.github.yesh.paycore.ledger.idempotency.IdempotencyService;
import io.github.yesh.paycore.ledger.repository.AccountRepository;
import io.github.yesh.paycore.ledger.repository.PaymentRepository;
import io.github.yesh.paycore.ledger.repository.SellerRepository;
import io.github.yesh.paycore.ledger.strategy.PostingStrategyRouter;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PaymentServiceTest {

  private PaymentRepository paymentRepository;
  private SellerRepository sellerRepository;
  private AccountRepository accountRepository;
  private PostingStrategyRouter strategyRouter;
  private IdempotencyService idempotencyService;
  private PaymentService paymentService;

  @BeforeEach
  void setUp() {
    paymentRepository = mock(PaymentRepository.class);
    sellerRepository = mock(SellerRepository.class);
    accountRepository = mock(AccountRepository.class);
    strategyRouter = mock(PostingStrategyRouter.class);
    idempotencyService = mock(IdempotencyService.class);
    paymentService =
        new PaymentService(
            paymentRepository,
            sellerRepository,
            accountRepository,
            strategyRouter,
            idempotencyService);
  }

  @Test
  @DisplayName("F-L-02: Capture fails if seller does not exist")
  void testCaptureUnknownSellerThrows() {
    CapturePaymentRequest request = new CapturePaymentRequest("pay_101", "unknown_seller", 10000L);

    when(paymentRepository.findByPaymentId("pay_101")).thenReturn(Optional.empty());
    when(sellerRepository.findBySellerId("unknown_seller")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> paymentService.doCapture(request, "idem_1", "global"))
        .isInstanceOf(PayCoreException.class)
        .hasMessageContaining("not found");
  }

  @Test
  @DisplayName("F-L-02: Get payment returns payment response or throws 404")
  void testGetPayment() {
    Payment p =
        new Payment(
            "pay_555",
            "seller_1",
            5000L,
            100L,
            4900L,
            io.github.yesh.paycore.ledger.domain.PaymentStatus.CAPTURED,
            java.util.UUID.randomUUID());
    when(paymentRepository.findByPaymentId("pay_555")).thenReturn(Optional.of(p));

    PaymentResponse response = paymentService.getPayment("pay_555");
    assertThat(response.paymentId()).isEqualTo("pay_555");
    assertThat(response.grossPaise()).isEqualTo(5000L);

    when(paymentRepository.findByPaymentId("unknown")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> paymentService.getPayment("unknown"))
        .isInstanceOf(PayCoreException.class)
        .hasMessageContaining("not found");
  }
}
