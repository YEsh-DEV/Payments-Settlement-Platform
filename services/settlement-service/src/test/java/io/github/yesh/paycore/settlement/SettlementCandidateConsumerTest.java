package io.github.yesh.paycore.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.settlement.domain.ProcessedEvent;
import io.github.yesh.paycore.settlement.domain.SettlementRecord;
import io.github.yesh.paycore.settlement.dto.SettlementCandidateRequest;
import io.github.yesh.paycore.settlement.repository.ProcessedEventRepository;
import io.github.yesh.paycore.settlement.repository.SettlementRecordRepository;
import io.github.yesh.paycore.settlement.service.SettlementCandidateConsumer;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class SettlementCandidateConsumerTest {

  private SettlementRecordRepository recordRepo;
  private ProcessedEventRepository processedEventRepo;
  private SettlementCandidateConsumer consumer;

  @BeforeEach
  void setUp() {
    recordRepo = Mockito.mock(SettlementRecordRepository.class);
    processedEventRepo = Mockito.mock(ProcessedEventRepository.class);
    consumer = new SettlementCandidateConsumer(recordRepo, processedEventRepo, new ObjectMapper());
  }

  @Test
  @DisplayName("Should successfully ingest new settlement candidate")
  void testIngestNewCandidate() {
    String eventId = "evt_capture_101";
    SettlementCandidateRequest req =
        new SettlementCandidateRequest(
            eventId,
            "LEDGER",
            "pay_101",
            "sel_101",
            "ptxn_101",
            "ord_101",
            "CAPTURE",
            100000L,
            2000L,
            98000L,
            Instant.now());

    Mockito.when(processedEventRepo.existsByEventId(eventId)).thenReturn(false);
    Mockito.when(recordRepo.save(Mockito.any(SettlementRecord.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    Optional<SettlementRecord> result = consumer.ingestCandidate(req);

    assertThat(result).isPresent();
    assertThat(result.get().getPaymentId()).isEqualTo("pay_101");
    assertThat(result.get().getGrossPaise()).isEqualTo(100000L);
    assertThat(result.get().getNetPaise()).isEqualTo(98000L);

    Mockito.verify(processedEventRepo).save(Mockito.any(ProcessedEvent.class));
    Mockito.verify(recordRepo).save(Mockito.any(SettlementRecord.class));
  }

  @Test
  @DisplayName("Should deduplicate re-delivered event safely (Re-delivery safety)")
  void testDeduplicateRedeliveredEvent() {
    String eventId = "evt_duplicate_202";
    SettlementCandidateRequest req =
        new SettlementCandidateRequest(
            eventId,
            "LEDGER",
            "pay_202",
            "sel_202",
            "ptxn_202",
            "ord_202",
            "CAPTURE",
            50000L,
            1000L,
            49000L,
            Instant.now());

    Mockito.when(processedEventRepo.existsByEventId(eventId)).thenReturn(true);

    Optional<SettlementRecord> result = consumer.ingestCandidate(req);

    assertThat(result).isEmpty();
    Mockito.verify(recordRepo, Mockito.never()).save(Mockito.any());
  }
}
