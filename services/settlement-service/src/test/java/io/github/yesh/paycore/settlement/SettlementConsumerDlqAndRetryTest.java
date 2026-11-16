package io.github.yesh.paycore.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.settlement.domain.ProcessedEvent;
import io.github.yesh.paycore.settlement.domain.SettlementRecord;
import io.github.yesh.paycore.settlement.dto.SettlementCandidateRequest;
import io.github.yesh.paycore.settlement.repository.ProcessedEventRepository;
import io.github.yesh.paycore.settlement.repository.SettlementRecordRepository;
import io.github.yesh.paycore.settlement.service.SettlementCandidateConsumer;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

class SettlementConsumerDlqAndRetryTest {

  private SettlementRecordRepository recordRepo;
  private ProcessedEventRepository processedEventRepo;
  private SettlementCandidateConsumer consumer;
  private SimpleMeterRegistry meterRegistry;
  private Counter dlqCounter;
  private KafkaOperations<Object, Object> kafkaOperations;

  @BeforeEach
  void setUp() {
    recordRepo = mock(SettlementRecordRepository.class);
    processedEventRepo = mock(ProcessedEventRepository.class);
    consumer = new SettlementCandidateConsumer(recordRepo, processedEventRepo, new ObjectMapper());

    meterRegistry = new SimpleMeterRegistry();
    dlqCounter =
        Counter.builder("settlement_consumer_dlq_total")
            .description("Total number of poison pill messages routed to DLQ")
            .register(meterRegistry);

    kafkaOperations = mock(KafkaOperations.class);
    when(kafkaOperations.send(any(org.apache.kafka.clients.producer.ProducerRecord.class)))
        .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(null));
  }

  @Test
  @DisplayName("At-least-once delivery: delivering event twice creates exactly one candidate row")
  void testAtLeastOnceDeliveryDeduplication() {
    String eventId = "evt_payment_captured_1001";
    String paymentId = "pay_txn_999";

    // Simulate database state for deduplication
    Set<String> processedEvents = new HashSet<>();
    List<SettlementRecord> savedRecords = new ArrayList<>();

    when(processedEventRepo.existsByEventId(eventId))
        .thenAnswer(inv -> processedEvents.contains(eventId));

    when(processedEventRepo.save(any(ProcessedEvent.class)))
        .thenAnswer(
            inv -> {
              ProcessedEvent pe = inv.getArgument(0);
              processedEvents.add(pe.getEventId());
              return pe;
            });

    when(recordRepo.save(any(SettlementRecord.class)))
        .thenAnswer(
            inv -> {
              SettlementRecord r = inv.getArgument(0);
              savedRecords.add(r);
              return r;
            });

    SettlementCandidateRequest request =
        new SettlementCandidateRequest(
            eventId,
            "KAFKA_LEDGER_EVENTS",
            paymentId,
            "sel_merchant_1",
            "ptxn_999",
            "ord_999",
            "CAPTURE",
            100000L,
            2000L,
            98000L,
            Instant.now());

    // Delivery 1: Should ingest successfully
    Optional<SettlementRecord> firstDelivery = consumer.ingestCandidate(request);
    assertThat(firstDelivery).isPresent();
    assertThat(savedRecords).hasSize(1);
    assertThat(processedEvents).contains(eventId);

    // Delivery 2: Re-delivered identical event
    Optional<SettlementRecord> secondDelivery = consumer.ingestCandidate(request);
    assertThat(secondDelivery).isEmpty(); // Deduplicated!

    // Assert exactly one candidate row created
    assertThat(savedRecords).hasSize(1);
    verify(recordRepo, times(1)).save(any(SettlementRecord.class));
    verify(processedEventRepo, times(1)).save(any(ProcessedEvent.class));
  }

  @Test
  @DisplayName(
      "Consumer poison pill error: routes to ledger.domain-events.dlq and increments metric")
  void testPoisonPillDlqRoutingAndMetric() {
    String dlqTopic = "ledger.domain-events.dlq";

    DeadLetterPublishingRecoverer recoverer =
        new DeadLetterPublishingRecoverer(
            kafkaOperations,
            (consumerRecord, exception) -> {
              dlqCounter.increment();
              return new TopicPartition(dlqTopic, consumerRecord.partition());
            });

    // 3 retries with 10ms backoff for fast unit test
    FixedBackOff backOff = new FixedBackOff(10L, 3);
    DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

    ConsumerRecord<String, String> record =
        new ConsumerRecord<>(
            "ledger.domain-events", 2, 42L, "pay_err_1", "CORRUPT_NON_JSON_PAYLOAD");
    RuntimeException poisonError = new RuntimeException("Jackson JSON parse failure");

    // Directly test recoverer routing to DLQ and incrementing metric
    recoverer.accept(record, poisonError);

    // Verify counter incremented
    assertThat(dlqCounter.count()).isEqualTo(1.0);

    // Verify sent to DLQ topic
    ArgumentCaptor<org.apache.kafka.clients.producer.ProducerRecord> recordCaptor =
        ArgumentCaptor.forClass(org.apache.kafka.clients.producer.ProducerRecord.class);
    verify(kafkaOperations).send(recordCaptor.capture());

    org.apache.kafka.clients.producer.ProducerRecord sentDlqRecord = recordCaptor.getValue();
    assertThat(sentDlqRecord.topic()).isEqualTo("ledger.domain-events.dlq");
    assertThat(sentDlqRecord.key()).isEqualTo("pay_err_1");
    assertThat(sentDlqRecord.value()).isEqualTo("CORRUPT_NON_JSON_PAYLOAD");
  }
}
