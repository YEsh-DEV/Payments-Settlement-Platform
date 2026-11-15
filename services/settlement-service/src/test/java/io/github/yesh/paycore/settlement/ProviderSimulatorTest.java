package io.github.yesh.paycore.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.github.yesh.paycore.settlement.domain.SettlementRecord;
import io.github.yesh.paycore.settlement.dto.SimulationRequest;
import io.github.yesh.paycore.settlement.dto.SimulationResult;
import io.github.yesh.paycore.settlement.repository.SettlementRecordRepository;
import io.github.yesh.paycore.settlement.repository.SimulationManifestRepository;
import io.github.yesh.paycore.settlement.service.ProviderSimulatorService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ProviderSimulatorTest {

  private SettlementRecordRepository recordRepo;
  private SimulationManifestRepository manifestRepo;
  private ProviderSimulatorService simulatorService;

  @BeforeEach
  void setUp() {
    recordRepo = mock(SettlementRecordRepository.class);
    manifestRepo = mock(SimulationManifestRepository.class);
    simulatorService = new ProviderSimulatorService(recordRepo, manifestRepo);
  }

  @Test
  @DisplayName("Should generate simulated provider CSV file with header and trailer")
  void testGenerateSettlementFile() {
    SettlementRecord r1 =
        new SettlementRecord(
            "REC_1",
            "PAY_1",
            "SEL_1",
            "ptxn_1",
            "ord_1",
            "CAPTURE",
            100000L,
            2000L,
            98000L,
            Instant.now());
    SettlementRecord r2 =
        new SettlementRecord(
            "REC_2",
            "PAY_2",
            "SEL_2",
            "ptxn_2",
            "ord_2",
            "CAPTURE",
            250000L,
            5000L,
            245000L,
            Instant.now());

    when(recordRepo.findByBatchId("BATCH_TEST")).thenReturn(List.of(r1, r2));

    SimulationResult result =
        simulatorService.generateSettlementFile(
            new SimulationRequest("BATCH_TEST", 12345L, null, "sim_test.csv"));

    assertThat(result).isNotNull();
    assertThat(result.batchId()).isEqualTo("BATCH_TEST");
    assertThat(result.filename()).isEqualTo("sim_test.csv");
    assertThat(result.csvContent()).contains("provider_batch_id,provider_txn_id");
    assertThat(result.csvContent()).contains("TRAILER");
    assertThat(result.totalLines()).isGreaterThan(0);
  }
}
