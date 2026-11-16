package io.github.yesh.paycore.settlement.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.settlement.client.LedgerClient;
import io.github.yesh.paycore.settlement.domain.*;
import io.github.yesh.paycore.settlement.dto.ReconciliationRunRequest;
import io.github.yesh.paycore.settlement.dto.ReconciliationRunResponse;
import io.github.yesh.paycore.settlement.exception.FileIntegrityException;
import io.github.yesh.paycore.settlement.repository.*;
import io.github.yesh.paycore.settlement.service.StreamingFileParser;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GoldenFixturesReconciliationTest {

  private ReconciliationRunRepository runRepo;
  private ReconciliationMatchRepository matchRepo;
  private SettlementRecordRepository recordRepo;
  private ProviderLineRepository lineRepo;
  private BatchRepository batchRepo;
  private BankDepositRepository depositRepo;
  private CaseRepository caseRepo;
  private SimulationManifestRepository manifestRepo;
  private ProviderSettlementFileRepository fileRepo;
  private LedgerClient ledgerClient;
  private ReconciliationEngine engine;
  private StreamingFileParser fileParser;

  @BeforeEach
  void setUp() {
    runRepo = mock(ReconciliationRunRepository.class);
    matchRepo = mock(ReconciliationMatchRepository.class);
    recordRepo = mock(SettlementRecordRepository.class);
    lineRepo = mock(ProviderLineRepository.class);
    batchRepo = mock(BatchRepository.class);
    depositRepo = mock(BankDepositRepository.class);
    caseRepo = mock(CaseRepository.class);
    manifestRepo = mock(SimulationManifestRepository.class);
    fileRepo = mock(ProviderSettlementFileRepository.class);
    ledgerClient = mock(LedgerClient.class);

    when(runRepo.save(any(ReconciliationRun.class))).thenAnswer(inv -> inv.getArgument(0));

    engine =
        new ReconciliationEngine(
            runRepo,
            matchRepo,
            recordRepo,
            lineRepo,
            batchRepo,
            depositRepo,
            caseRepo,
            manifestRepo,
            new ObjectMapper(),
            new SimpleMeterRegistry());
    engine.setLedgerClient(ledgerClient);

    fileParser = new StreamingFileParser(fileRepo, lineRepo);
  }

  private Batch createBatch(String batchId, long gross, long fee, long net) {
    Batch b = new Batch(batchId, "WINDOW_1");
    b.setTotalCount(1);
    b.setTotalGrossPaise(gross);
    b.setTotalFeePaise(fee);
    b.setTotalNetPaise(net);
    b.setStatus(BatchStatus.COMPLETED);
    return b;
  }

  @Test
  @DisplayName("Fixture 1: Trailer disagreement throws FileIntegrityException")
  void testTrailerDisagreementThrowsFileIntegrityException() {
    InputStream stream =
        getClass()
            .getClassLoader()
            .getResourceAsStream("fixtures/golden/golden_trailer_disagreement.csv");
    assertThat(stream).isNotNull();

    when(fileRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    assertThatThrownBy(() -> fileParser.parseAndIngest("trailer_fail.csv", "RAZORPAY", stream))
        .isInstanceOf(FileIntegrityException.class)
        .hasMessageContaining("trailer amount 400000 does not match parsed amount 300000");
  }

  @Test
  @DisplayName("Fixture 2: Duplicate provider txn id creates DUPLICATE_PROVIDER_LINE case")
  void testDuplicateProviderTxnIdCreatesCase() {
    String batchId = "BATCH_GOLDEN_3";
    ProviderLine line1 =
        new ProviderLine(
            UUID.randomUUID(),
            1,
            batchId,
            "ptxn_duplicate_golden_999",
            "CAPTURE",
            "ORD_DUP_1",
            "ORD_DUP_1",
            100000L,
            2000L,
            98000L,
            "INR",
            Instant.parse("2026-11-16T12:00:00Z"),
            LocalDate.of(2026, 11, 16),
            "{}");
    line1.setId(101L);

    ProviderLine line2 =
        new ProviderLine(
            UUID.randomUUID(),
            2,
            batchId,
            "ptxn_duplicate_golden_999",
            "CAPTURE",
            "ORD_DUP_2",
            "ORD_DUP_2",
            100000L,
            2000L,
            98000L,
            "INR",
            Instant.parse("2026-11-16T12:01:00Z"),
            LocalDate.of(2026, 11, 16),
            "{}");
    line2.setId(102L);

    SettlementRecord record =
        new SettlementRecord(
            "REC_1",
            "pay_1",
            "seller_1",
            "ptxn_duplicate_golden_999",
            "ORD_DUP_1",
            "CAPTURE",
            100000L,
            2000L,
            98000L,
            Instant.parse("2026-11-16T12:00:00Z"));
    record.setBatchId(batchId);

    when(recordRepo.findByBatchId(batchId)).thenReturn(List.of(record));
    when(lineRepo.findByProviderBatchId(batchId)).thenReturn(List.of(line1, line2));
    when(depositRepo.findByStatus("UNMATCHED")).thenReturn(Collections.emptyList());
    when(batchRepo.findByStatus(BatchStatus.COMPLETED)).thenReturn(Collections.emptyList());

    ReconciliationRunResponse response = engine.execute(new ReconciliationRunRequest(batchId));

    assertThat(response.matchedCount()).isEqualTo(1);
    assertThat(response.discrepancyCount()).isEqualTo(1);

    verify(caseRepo)
        .saveAll(
            argThat(
                cases -> {
                  List<DiscrepancyCase> list = (List<DiscrepancyCase>) cases;
                  return list.stream()
                      .anyMatch(
                          c ->
                              c.getType() == CaseType.DUPLICATE_PROVIDER_LINE
                                  && c.getSeverity() == CaseSeverity.HIGH);
                }));
  }

  @Test
  @DisplayName("Fixture 3: Two candidates with same amount inside 10-minute window must not guess")
  void testTwoCandidatesSameAmountInside10MinWindowRefusesToGuess() {
    String batchId = "BATCH_GOLDEN_1";
    Instant lineTime = Instant.parse("2026-11-16T10:00:00Z");

    ProviderLine line =
        new ProviderLine(
            UUID.randomUUID(),
            1,
            batchId,
            "ptxn_golden_ambig_1",
            "CAPTURE",
            "UNKNOWN_REF",
            "UNKNOWN_REF",
            50000L,
            1000L,
            49000L,
            "INR",
            lineTime,
            LocalDate.of(2026, 11, 16),
            "{}");
    line.setId(201L);

    // Two candidates with same amount (50,000 paise) within +/- 10 min, neither has matching merchantRef
    SettlementRecord candidate1 =
        new SettlementRecord(
            "REC_A",
            "pay_A",
            "seller_1",
            null,
            "ORD_OTHER_1",
            "CAPTURE",
            50000L,
            1000L,
            49000L,
            lineTime.plusSeconds(120)); // +2 min
    candidate1.setBatchId(batchId);

    SettlementRecord candidate2 =
        new SettlementRecord(
            "REC_B",
            "pay_B",
            "seller_2",
            null,
            "ORD_OTHER_2",
            "CAPTURE",
            50000L,
            1000L,
            49000L,
            lineTime.plusSeconds(300)); // +5 min
    candidate2.setBatchId(batchId);

    when(recordRepo.findByBatchId(batchId)).thenReturn(List.of(candidate1, candidate2));
    when(lineRepo.findByProviderBatchId(batchId)).thenReturn(List.of(line));
    when(depositRepo.findByStatus("UNMATCHED")).thenReturn(Collections.emptyList());
    when(batchRepo.findByStatus(BatchStatus.COMPLETED)).thenReturn(Collections.emptyList());

    ReconciliationRunResponse response = engine.execute(new ReconciliationRunRequest(batchId));

    // The system MUST NOT guess between the two candidates!
    assertThat(response.matchedCount()).isEqualTo(0);
    assertThat(response.discrepancyCount()).isGreaterThanOrEqualTo(2);

    verify(caseRepo)
        .saveAll(
            argThat(
                cases -> {
                  List<DiscrepancyCase> list = (List<DiscrepancyCase>) cases;
                  boolean hasMissingProvider =
                      list.stream().anyMatch(c -> c.getType() == CaseType.MISSING_IN_PROVIDER);
                  boolean hasMissingLedger =
                      list.stream().anyMatch(c -> c.getType() == CaseType.MISSING_IN_LEDGER);
                  return hasMissingProvider && hasMissingLedger;
                }));
  }

  @Test
  @DisplayName("Fixture 4: One deposit covering three batches is resolved by subset search")
  void testOneDepositCoveringThreeBatches() {
    Batch b1 = createBatch("BATCH_TRIPLE_1", 120000L, 2400L, 117600L);
    Batch b2 = createBatch("BATCH_TRIPLE_2", 180000L, 3600L, 176400L);
    Batch b3 = createBatch("BATCH_TRIPLE_3", 200000L, 4000L, 196000L);

    // Sum of net: 117600 + 176400 + 196000 = 490000 paise
    BankDeposit deposit =
        new BankDeposit("DEP_TRIPLE_1", LocalDate.now(), 490000L, "INR", "BULK_DEPOSIT_TRIPLE");

    when(recordRepo.findByBatchId(any())).thenReturn(Collections.emptyList());
    when(lineRepo.findByProviderBatchId(any())).thenReturn(Collections.emptyList());
    when(depositRepo.findByStatus("UNMATCHED")).thenReturn(List.of(deposit));
    when(batchRepo.findByStatus(BatchStatus.COMPLETED)).thenReturn(List.of(b1, b2, b3));
    when(batchRepo.findByBatchId("BATCH_TRIPLE_1")).thenReturn(Optional.of(b1));
    when(batchRepo.findByBatchId("BATCH_TRIPLE_2")).thenReturn(Optional.of(b2));
    when(batchRepo.findByBatchId("BATCH_TRIPLE_3")).thenReturn(Optional.of(b3));

    engine.execute(new ReconciliationRunRequest("BATCH_TRIPLE_1"));

    assertThat(deposit.getStatus()).isEqualTo("MATCHED");
    assertThat(b1.getStatus()).isEqualTo(BatchStatus.RECONCILED);
    assertThat(b2.getStatus()).isEqualTo(BatchStatus.RECONCILED);
    assertThat(b3.getStatus()).isEqualTo(BatchStatus.RECONCILED);
    verify(depositRepo).save(deposit);
  }

  @Test
  @DisplayName("Fixture 5: Deposit with reserve holdback matches using tolerance fallback")
  void testDepositWithReserveHoldbackMatches() {
    // Batch net is 100,000 paise; bank deposit is 95,000 paise (5,000 paise holdback)
    Batch b = createBatch("BATCH_GOLDEN_HOLDBACK", 102000L, 2000L, 100000L);

    BankDeposit deposit =
        new BankDeposit("DEP_HB_1", LocalDate.now(), 95000L, "INR", "BANK_SETTLE_NO_REF");

    when(recordRepo.findByBatchId(any())).thenReturn(Collections.emptyList());
    when(lineRepo.findByProviderBatchId(any())).thenReturn(Collections.emptyList());
    when(depositRepo.findByStatus("UNMATCHED")).thenReturn(List.of(deposit));
    when(batchRepo.findByStatus(BatchStatus.COMPLETED)).thenReturn(List.of(b));
    when(batchRepo.findByBatchId("BATCH_GOLDEN_HOLDBACK")).thenReturn(Optional.of(b));

    // Execute with reserveHoldbackToleranceMinor = 5000L
    ReconciliationRunRequest request =
        new ReconciliationRunRequest("BATCH_GOLDEN_HOLDBACK", null, null, 200, 0L, 1L, 5000L);

    engine.execute(request);

    assertThat(deposit.getStatus()).isEqualTo("MATCHED");
    assertThat(b.getStatus()).isEqualTo(BatchStatus.RECONCILED);
    verify(depositRepo).save(deposit);
  }

  @Test
  @DisplayName("Fixture 6: Stripped reference matches by exact net amount in Stage 3")
  void testStrippedReferenceMatchesExactNetAmount() {
    Batch b = createBatch("BATCH_GOLDEN_STRIPPED", 250000L, 5000L, 245000L);

    // Reference contains NO mention of the batch ID
    BankDeposit deposit =
        new BankDeposit("DEP_STRIPPED_1", LocalDate.now(), 245000L, "INR", "MISC_NEFT_CREDIT_9988");

    when(recordRepo.findByBatchId(any())).thenReturn(Collections.emptyList());
    when(lineRepo.findByProviderBatchId(any())).thenReturn(Collections.emptyList());
    when(depositRepo.findByStatus("UNMATCHED")).thenReturn(List.of(deposit));
    when(batchRepo.findByStatus(BatchStatus.COMPLETED)).thenReturn(List.of(b));

    engine.execute(new ReconciliationRunRequest("BATCH_GOLDEN_STRIPPED"));

    assertThat(deposit.getStatus()).isEqualTo("MATCHED");
    assertThat(deposit.getMatchedBatchId()).isEqualTo("BATCH_GOLDEN_STRIPPED");
    assertThat(b.getStatus()).isEqualTo(BatchStatus.RECONCILED);
  }

  @Test
  @DisplayName("Re-running reconciliation preserves matches and never duplicates cases")
  void testRerunningReconciliationNeverDuplicatesCases() {
    String batchId = "BATCH_RERUN_1";
    SettlementRecord rec =
        new SettlementRecord(
            "REC_FEE",
            "pay_fee",
            "seller_1",
            "ptxn_fee_1",
            "ORD_FEE",
            "CAPTURE",
            100000L,
            2000L,
            98000L,
            Instant.now());
    rec.setBatchId(batchId);

    // Provider line has fee variance: fee is 2500 paise instead of expected 2000 paise
    ProviderLine line =
        new ProviderLine(
            UUID.randomUUID(),
            1,
            batchId,
            "ptxn_fee_1",
            "CAPTURE",
            "ORD_FEE",
            "ORD_FEE",
            100000L,
            2500L,
            97500L,
            "INR",
            Instant.now(),
            LocalDate.now(),
            "{}");
    line.setId(501L);

    when(recordRepo.findByBatchId(batchId)).thenReturn(List.of(rec));
    when(lineRepo.findByProviderBatchId(batchId)).thenReturn(List.of(line));
    when(depositRepo.findByStatus("UNMATCHED")).thenReturn(Collections.emptyList());
    when(batchRepo.findByStatus(BatchStatus.COMPLETED)).thenReturn(Collections.emptyList());

    // First run: case does not exist in repo yet
    when(caseRepo.existsByTypeAndReferenceId(CaseType.FEE_VARIANCE, "REC_FEE")).thenReturn(false);

    engine.execute(new ReconciliationRunRequest(batchId));

    // Verify first run added 1 case
    verify(caseRepo, times(1))
        .saveAll(
            argThat(
                cases -> {
                  List<DiscrepancyCase> list = (List<DiscrepancyCase>) cases;
                  return list.size() == 1 && list.get(0).getType() == CaseType.FEE_VARIANCE;
                }));

    // Second run: now caseRepo reports case already exists!
    when(caseRepo.existsByTypeAndReferenceId(CaseType.FEE_VARIANCE, "REC_FEE")).thenReturn(true);

    engine.execute(new ReconciliationRunRequest(batchId));

    // On second run, saveAll is NOT called with a duplicate case!
    verify(caseRepo, times(1)).saveAll(any());
  }
}
