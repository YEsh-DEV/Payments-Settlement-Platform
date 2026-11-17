package io.github.yesh.paycore.settlement.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.github.yesh.paycore.settlement.BaseIntegrationTest;
import io.github.yesh.paycore.settlement.client.LedgerClient;
import io.github.yesh.paycore.settlement.domain.BatchStatus;
import io.github.yesh.paycore.settlement.domain.SettlementPayout;
import io.github.yesh.paycore.settlement.domain.SettlementRecord;
import io.github.yesh.paycore.settlement.dto.BatchCutoffRequest;
import io.github.yesh.paycore.settlement.dto.BatchResponse;
import io.github.yesh.paycore.settlement.exception.InvalidBatchStateException;
import io.github.yesh.paycore.settlement.repository.BatchRepository;
import io.github.yesh.paycore.settlement.repository.SettlementPayoutRepository;
import io.github.yesh.paycore.settlement.repository.SettlementRecordRepository;
import io.github.yesh.paycore.settlement.service.BatchCutoffService;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
public class BatchCutoffAdvisoryLockPostgresIntegrationTest extends BaseIntegrationTest {

  @Autowired private BatchCutoffService batchCutoffService;
  @Autowired private BatchRepository batchRepository;
  @Autowired private SettlementRecordRepository recordRepository;
  @Autowired private SettlementPayoutRepository payoutRepository;

  @MockBean private LedgerClient ledgerClient;

  @BeforeEach
  void cleanDatabase() {
    payoutRepository.deleteAll();
    recordRepository.deleteAll();
    batchRepository.deleteAll();
  }

  @Test
  @DisplayName(
      "Real PostgreSQL Session Advisory Lock: Concurrent cutoff invocations reject second thread with 409")
  void testConcurrentCutoffBlockedBySessionAdvisoryLock() throws Exception {
    // Seed 10 records for 2 sellers
    for (int i = 1; i <= 5; i++) {
      recordRepository.save(
          new SettlementRecord(
              "REC_A_" + i,
              "PAY_A_" + i,
              "seller_conc_a",
              "ptxn_a_" + i,
              "ord_a_" + i,
              "CAPTURE",
              10000L,
              200L,
              9800L,
              Instant.now()));
      recordRepository.save(
          new SettlementRecord(
              "REC_B_" + i,
              "PAY_B_" + i,
              "seller_conc_b",
              "ptxn_b_" + i,
              "ord_b_" + i,
              "CAPTURE",
              20000L,
              400L,
              19600L,
              Instant.now()));
    }

    // Simulate 350ms processing time inside ledger client to ensure concurrent overlap
    when(ledgerClient.initiatePayout(any(), any(), anyLong()))
        .thenAnswer(
            invocation -> {
              Thread.sleep(350);
              return Map.of("status", "SUCCESS");
            });

    int concurrency = 2;
    ExecutorService executor = Executors.newFixedThreadPool(concurrency);
    CountDownLatch readyLatch = new CountDownLatch(concurrency);
    CountDownLatch startLatch = new CountDownLatch(1);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger rejectedCount = new AtomicInteger(0);
    List<Throwable> exceptions = new CopyOnWriteArrayList<>();

    List<Future<?>> futures = new ArrayList<>();
    for (int i = 0; i < concurrency; i++) {
      futures.add(
          executor.submit(
              () -> {
                readyLatch.countDown();
                try {
                  startLatch.await();
                  batchCutoffService.runCutoff(new BatchCutoffRequest(Instant.now(), 5000L, null));
                  successCount.incrementAndGet();
                } catch (InvalidBatchStateException e) {
                  rejectedCount.incrementAndGet();
                } catch (Throwable t) {
                  exceptions.add(t);
                }
              }));
    }

    readyLatch.await(5, TimeUnit.SECONDS);
    startLatch.countDown();

    for (Future<?> f : futures) {
      f.get(10, TimeUnit.SECONDS);
    }
    executor.shutdown();

    assertThat(exceptions).isEmpty();
    assertThat(successCount.get()).isEqualTo(1);
    assertThat(rejectedCount.get()).isEqualTo(1);

    // Verify exactly 1 batch was created
    assertThat(batchRepository.count()).isEqualTo(1);

    // Verify after the winning thread finishes, a second run works without lock contention
    BatchResponse secondRun =
        batchCutoffService.runCutoff(new BatchCutoffRequest(Instant.now(), 5000L, null));
    assertThat(secondRun).isNotNull();
    assertThat(secondRun.status()).isEqualTo(BatchStatus.CLOSED); // 0 pending records left
  }

  @Test
  @DisplayName(
      "Resilience & Resumption: Interrupted cutoff sets PARTIAL_FAILURE; resumeBatch completes without duplicate payouts")
  void testInterruptedCutoffResumesWithoutDuplicatePayouts() {
    String seller1 = "seller_retry_1";
    String seller2 = "seller_retry_2";

    recordRepository.save(
        new SettlementRecord(
            "REC_R1",
            "PAY_R1",
            seller1,
            "ptxn_r1",
            "ord_r1",
            "CAPTURE",
            100000L,
            2000L,
            98000L,
            Instant.now()));
    recordRepository.save(
        new SettlementRecord(
            "REC_R2",
            "PAY_R2",
            seller2,
            "ptxn_r2",
            "ord_r2",
            "CAPTURE",
            200000L,
            4000L,
            196000L,
            Instant.now()));

    // Seller 1 succeeds, Seller 2 fails with network error
    when(ledgerClient.initiatePayout(contains(seller1), eq(seller1), anyLong()))
        .thenReturn(Map.of("status", "SUCCESS"));
    when(ledgerClient.initiatePayout(contains(seller2), eq(seller2), anyLong()))
        .thenThrow(new RuntimeException("Transient connection refused to Ledger Service"));

    BatchResponse initialBatch =
        batchCutoffService.runCutoff(new BatchCutoffRequest(Instant.now(), 10000L, null));
    assertThat(initialBatch.status()).isEqualTo(BatchStatus.PARTIAL_FAILURE);

    // Verify Seller 1 has ledger payout id; Seller 2 has status FAILED and null ledger payout id
    List<SettlementPayout> payouts = payoutRepository.findByBatchId(initialBatch.batchId());
    assertThat(payouts).hasSize(2);

    SettlementPayout p1 =
        payouts.stream().filter(p -> p.getSellerId().equals(seller1)).findFirst().orElseThrow();
    SettlementPayout p2 =
        payouts.stream().filter(p -> p.getSellerId().equals(seller2)).findFirst().orElseThrow();

    assertThat(p1.getLedgerPayoutId()).isNotNull();
    assertThat(p2.getLedgerPayoutId()).isNull();
    assertThat(p2.getStatus()).isEqualTo("FAILED");

    // Network recovers: Seller 2 now succeeds
    when(ledgerClient.initiatePayout(contains(seller2), eq(seller2), anyLong()))
        .thenReturn(Map.of("status", "SUCCESS"));

    BatchResponse resumedBatch = batchCutoffService.resumeBatch(initialBatch.batchId());
    assertThat(resumedBatch.status()).isEqualTo(BatchStatus.PAYOUTS_REQUESTED);

    // Verify Seller 2 is now completed with the exact same deterministic payout ID
    SettlementPayout p2Resumed = payoutRepository.findById(p2.getId()).orElseThrow();
    assertThat(p2Resumed.getLedgerPayoutId()).isEqualTo(p2.getPayoutId());
    assertThat(p2Resumed.getStatus()).isEqualTo("REQUESTED");

    // Verify total payouts for batch is still exactly 2 (zero duplicates)
    assertThat(payoutRepository.findByBatchId(initialBatch.batchId())).hasSize(2);

    // Verify ledgerClient was called once for seller1, and twice for seller2 (1 fail + 1 retry)
    verify(ledgerClient, times(1)).initiatePayout(eq(p1.getPayoutId()), eq(seller1), anyLong());
    verify(ledgerClient, times(2)).initiatePayout(eq(p2.getPayoutId()), eq(seller2), anyLong());
  }
}
