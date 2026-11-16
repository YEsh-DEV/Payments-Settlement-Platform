package io.github.yesh.paycore.settlement.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import io.github.yesh.paycore.settlement.client.LedgerClient;
import io.github.yesh.paycore.settlement.domain.Batch;
import io.github.yesh.paycore.settlement.domain.BatchStatus;
import io.github.yesh.paycore.settlement.domain.SettlementPayout;
import io.github.yesh.paycore.settlement.domain.SettlementRecord;
import io.github.yesh.paycore.settlement.domain.SettlementRecordStatus;
import io.github.yesh.paycore.settlement.dto.BatchCutoffRequest;
import io.github.yesh.paycore.settlement.dto.BatchResponse;
import io.github.yesh.paycore.settlement.dto.SettlementCandidateRequest;
import io.github.yesh.paycore.settlement.repository.BatchRepository;
import io.github.yesh.paycore.settlement.repository.SettlementPayoutRepository;
import io.github.yesh.paycore.settlement.repository.SettlementRecordRepository;
import io.github.yesh.paycore.settlement.service.BatchCutoffService;
import io.github.yesh.paycore.settlement.service.SettlementCandidateConsumer;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
public class PaymentToSettlementEndToEndIntegrationTest {

  @Autowired private BatchCutoffService batchCutoffService;
  @Autowired private SettlementCandidateConsumer candidateConsumer;
  @Autowired private BatchRepository batchRepository;
  @Autowired private SettlementRecordRepository recordRepository;
  @Autowired private SettlementPayoutRepository payoutRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockBean private LedgerClient ledgerClient;

  private RealisticLedgerDouble ledgerDouble;

  /**
   * Realistic double-entry ledger state machine tracking balance across system accounts per
   * Playbook Section 3.2 & 3.3.
   */
  static class RealisticLedgerDouble {
    final Map<String, Long> balances = new ConcurrentHashMap<>();
    final Set<String> initiatedPayouts = ConcurrentHashMap.newKeySet();
    final Set<String> confirmedPayouts = ConcurrentHashMap.newKeySet();

    RealisticLedgerDouble() {
      balances.put("PROVIDER_RECEIVABLE", 0L);
      balances.put("PLATFORM_FEE_REVENUE", 0L);
      balances.put("BANK_CASH", 0L);
      balances.put("PAYOUT_IN_TRANSIT", 0L);
    }

    void capturePayment(String sellerId, long gross, long fee) {
      long net = gross - fee;
      balances.merge("PROVIDER_RECEIVABLE", gross, Long::sum);
      balances.merge("PLATFORM_FEE_REVENUE", fee, Long::sum);
      balances.merge("MERCHANT_" + sellerId, net, Long::sum);
    }

    Map<String, Object> initiatePayout(String payoutId, String sellerId, long amount) {
      String merchantAccount = "MERCHANT_" + sellerId;
      long current = balances.getOrDefault(merchantAccount, 0L);
      if (current < amount) {
        throw new RuntimeException("insufficient_funds");
      }
      if (initiatedPayouts.add(payoutId)) {
        balances.put(merchantAccount, current - amount);
        balances.merge("PAYOUT_IN_TRANSIT", amount, Long::sum);
      }
      return Map.of("payoutId", payoutId, "status", "INITIATED");
    }

    Map<String, Object> confirmPayout(String payoutId, String batchId, long amount) {
      if (confirmedPayouts.add(payoutId)) {
        balances.merge("PAYOUT_IN_TRANSIT", -amount, Long::sum);
        balances.merge("BANK_CASH", -amount, Long::sum); // Platform bank pays out funds
      }
      return Map.of("payoutId", payoutId, "status", "PAID");
    }

    boolean verifyDoubleEntryBalance() {
      // Total Debits = PROVIDER_RECEIVABLE (100,000)
      // Total Credits = PLATFORM_FEE_REVENUE (2,000) + BANK_CASH outflow (98,000)
      long totalDebits = balances.get("PROVIDER_RECEIVABLE");
      long totalCredits =
          balances.get("PLATFORM_FEE_REVENUE")
              + balances.getOrDefault("PAYOUT_IN_TRANSIT", 0L)
              + (-balances.get("BANK_CASH"));
      return totalDebits == creditTotal(totalCredits);
    }

    private long creditTotal(long total) {
      return total;
    }
  }

  @BeforeEach
  void setUp() {
    ledgerDouble = new RealisticLedgerDouble();

    when(ledgerClient.initiatePayout(any(), any(), anyLong()))
        .thenAnswer(
            inv -> {
              String payoutId = inv.getArgument(0);
              String sellerId = inv.getArgument(1);
              long amount = inv.getArgument(2);
              return ledgerDouble.initiatePayout(payoutId, sellerId, amount);
            });

    when(ledgerClient.confirmPayout(any(), any()))
        .thenAnswer(
            inv -> {
              String payoutId = inv.getArgument(0);
              String batchId = inv.getArgument(1);
              return ledgerDouble.confirmPayout(payoutId, batchId, 98000L);
            });
  }

  @Test
  @DisplayName(
      "End-to-End: Payment capture -> Outbox candidate -> Batch cutoff -> Payout initiate -> Bank confirm -> Balance check")
  void testPaymentToSettlementEndToEndFlow() {
    String sellerId = "sel_e2e_" + UUID.randomUUID().toString().substring(0, 6);
    String paymentId = "pay_e2e_" + UUID.randomUUID().toString().substring(0, 8);
    String eventId = "evt_e2e_" + UUID.randomUUID().toString().substring(0, 8);

    long grossPaise = 100000L; // Rs 1,000
    long feePaise = 2000L; // Rs 20
    long netPaise = 98000L; // Rs 980

    // Step 1: Ledger captures payment
    ledgerDouble.capturePayment(sellerId, grossPaise, feePaise);
    assertThat(ledgerDouble.balances.get("MERCHANT_" + sellerId)).isEqualTo(98000L);
    assertThat(ledgerDouble.balances.get("PLATFORM_FEE_REVENUE")).isEqualTo(2000L);
    assertThat(ledgerDouble.balances.get("PROVIDER_RECEIVABLE")).isEqualTo(100000L);

    // Step 2: Outbox relay produces domain event -> Settlement Candidate Consumer ingests
    SettlementCandidateRequest candidateRequest =
        new SettlementCandidateRequest(
            eventId,
            "KAFKA_LEDGER_EVENTS",
            paymentId,
            sellerId,
            "ptxn_" + paymentId,
            "ord_" + paymentId,
            "CAPTURE",
            grossPaise,
            feePaise,
            netPaise,
            Instant.now());

    Optional<SettlementRecord> candidate = candidateConsumer.ingestCandidate(candidateRequest);
    assertThat(candidate).isPresent();
    assertThat(candidate.get().getStatus()).isEqualTo(SettlementRecordStatus.PENDING);

    // Step 3: Run Batch Cutoff with PostgreSQL Advisory Lock
    BatchResponse batchResponse =
        batchCutoffService.runCutoff(
            new BatchCutoffRequest(Instant.now().plusSeconds(60), 10000L, null));

    assertThat(batchResponse).isNotNull();
    String batchId = batchResponse.batchId();
    assertThat(batchResponse.status()).isEqualTo(BatchStatus.PAYOUTS_REQUESTED);

    // Verify candidate was linked and status updated to BATCHED
    SettlementRecord batchedRecord = recordRepository.findByPaymentId(paymentId).orElseThrow();
    assertThat(batchedRecord.getBatchId()).isEqualTo(batchId);
    assertThat(batchedRecord.getStatus()).isEqualTo(SettlementRecordStatus.BATCHED);

    // Verify deterministic payout was generated and ledger was called
    String expectedPayoutId = batchId + ":" + sellerId;
    SettlementPayout payout = payoutRepository.findByPayoutId(expectedPayoutId).orElseThrow();
    assertThat(payout.getAmountPaise()).isEqualTo(98000L);
    assertThat(payout.getLedgerPayoutId()).isEqualTo(expectedPayoutId);

    // Step 4: Verify ledger balances after payout initiation
    // SELLER_PAYABLE debited, PAYOUT_IN_TRANSIT credited
    assertThat(ledgerDouble.balances.get("MERCHANT_" + sellerId)).isEqualTo(0L);
    assertThat(ledgerDouble.balances.get("PAYOUT_IN_TRANSIT")).isEqualTo(98000L);

    // Step 5: Send bank payout file -> transitions to SENT
    String bankPayoutFile = batchCutoffService.sendPayoutFile(batchId);
    assertThat(bankPayoutFile).contains(expectedPayoutId);
    assertThat(bankPayoutFile).contains(sellerId);
    assertThat(bankPayoutFile).contains("98000");

    Batch sentBatch = batchRepository.findByBatchId(batchId).orElseThrow();
    assertThat(sentBatch.getStatus()).isEqualTo(BatchStatus.SENT);

    // Step 6: Bank confirmation response file arrives -> confirm payout
    String bankResultCsv =
        "payout_id,status,reason\n"
            + expectedPayoutId
            + ",CONFIRMED,Transfer settled successfully\n";
    BatchResponse completedBatch = batchCutoffService.processBankResults(batchId, bankResultCsv);

    assertThat(completedBatch.status()).isEqualTo(BatchStatus.COMPLETED);
    SettlementPayout confirmedPayout =
        payoutRepository.findByPayoutId(expectedPayoutId).orElseThrow();
    assertThat(confirmedPayout.getStatus()).isEqualTo("CONFIRMED");

    // Step 7: Final ledger balances verification
    // SELLER_PAYABLE = 0 (paid)
    // PAYOUT_IN_TRANSIT = 0 (cleared)
    // BANK_CASH = -98,000 (paid out from bank)
    // PLATFORM_FEE_REVENUE = 2,000 (earned)
    // PROVIDER_RECEIVABLE = 100,000 (money in transit from card provider)
    assertThat(ledgerDouble.balances.get("MERCHANT_" + sellerId)).isEqualTo(0L);
    assertThat(ledgerDouble.balances.get("PAYOUT_IN_TRANSIT")).isEqualTo(0L);
    assertThat(ledgerDouble.balances.get("BANK_CASH")).isEqualTo(-98000L);
    assertThat(ledgerDouble.balances.get("PLATFORM_FEE_REVENUE")).isEqualTo(2000L);
    assertThat(ledgerDouble.balances.get("PROVIDER_RECEIVABLE")).isEqualTo(100000L);

    // Invariant I3: Total debits equal total credits
    assertThat(ledgerDouble.verifyDoubleEntryBalance()).isTrue();
  }
}
