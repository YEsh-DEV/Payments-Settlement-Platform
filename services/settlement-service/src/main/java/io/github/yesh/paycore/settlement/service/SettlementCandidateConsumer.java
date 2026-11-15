package io.github.yesh.paycore.settlement.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.settlement.domain.ProcessedEvent;
import io.github.yesh.paycore.settlement.domain.SettlementRecord;
import io.github.yesh.paycore.settlement.dto.SettlementCandidateRequest;
import io.github.yesh.paycore.settlement.repository.ProcessedEventRepository;
import io.github.yesh.paycore.settlement.repository.SettlementRecordRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettlementCandidateConsumer {

  private static final Logger log = LoggerFactory.getLogger(SettlementCandidateConsumer.class);

  private final SettlementRecordRepository settlementRecordRepository;
  private final ProcessedEventRepository processedEventRepository;
  private final ObjectMapper objectMapper;

  public SettlementCandidateConsumer(
      SettlementRecordRepository settlementRecordRepository,
      ProcessedEventRepository processedEventRepository,
      ObjectMapper objectMapper) {
    this.settlementRecordRepository = settlementRecordRepository;
    this.processedEventRepository = processedEventRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public Optional<SettlementRecord> ingestCandidate(SettlementCandidateRequest request) {
    String eventId =
        (request.eventId() != null && !request.eventId().isBlank())
            ? request.eventId()
            : "MANUAL_" + UUID.randomUUID();

    if (processedEventRepository.existsByEventId(eventId)) {
      log.info("Event {} already processed. Skipping duplicate candidate ingestion.", eventId);
      return Optional.empty();
    }

    // Record deduplication event
    processedEventRepository.save(
        new ProcessedEvent(eventId, request.source() != null ? request.source() : "DIRECT_API"));

    String recordId = "REC_" + UUID.randomUUID();
    SettlementRecord record =
        new SettlementRecord(
            recordId,
            request.paymentId(),
            request.sellerId(),
            request.providerTxnId(),
            request.orderId(),
            request.kind() != null ? request.kind() : "CAPTURE",
            request.grossPaise(),
            request.feePaise(),
            request.netPaise(),
            request.occurredAt() != null ? request.occurredAt() : Instant.now());

    SettlementRecord saved = settlementRecordRepository.save(record);
    log.info(
        "Ingested settlement record {} for payment {} (net: {} paise)",
        saved.getRecordId(),
        saved.getPaymentId(),
        saved.getNetPaise());
    return Optional.of(saved);
  }

  @KafkaListener(
      topics = "${paycore.settlement.kafka-topic:paycore.ledger.events}",
      groupId = "${spring.kafka.consumer.group-id:settlement-group}",
      autoStartup = "${paycore.settlement.kafka-enabled:false}")
  @Transactional
  public void onLedgerDomainEvent(String payload) {
    try {
      JsonNode node = objectMapper.readTree(payload);
      String eventId = node.path("eventId").asText(null);
      String eventType = node.path("eventType").asText("");

      if (eventId != null && processedEventRepository.existsByEventId(eventId)) {
        log.info("Kafka event {} already processed. Skipping.", eventId);
        return;
      }

      if ("PAYMENT_CAPTURED".equalsIgnoreCase(eventType)
          || "REFUND_PROCESSED".equalsIgnoreCase(eventType)) {
        JsonNode data = node.path("payload");
        String paymentId = data.path("paymentId").asText();
        String sellerId = data.path("sellerId").asText();
        String providerTxnId = data.path("providerTxnId").asText(null);
        String orderId = data.path("orderId").asText(null);
        long grossPaise = data.path("grossPaise").asLong(0L);
        long feePaise = data.path("feePaise").asLong(0L);
        long netPaise = data.path("netPaise").asLong(grossPaise - feePaise);

        SettlementCandidateRequest request =
            new SettlementCandidateRequest(
                eventId,
                "KAFKA_LEDGER_EVENTS",
                paymentId,
                sellerId,
                providerTxnId,
                orderId,
                eventType.startsWith("REFUND") ? "REFUND" : "CAPTURE",
                grossPaise,
                feePaise,
                netPaise,
                Instant.now());

        ingestCandidate(request);
      }
    } catch (Exception e) {
      log.error("Failed to process ledger domain event from Kafka: {}", payload, e);
      throw new RuntimeException("Error processing Kafka settlement candidate event", e);
    }
  }
}
