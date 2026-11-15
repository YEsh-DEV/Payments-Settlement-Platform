package io.github.yesh.paycore.ledger.service;

import io.github.yesh.paycore.ledger.domain.OutboxEvent;
import io.github.yesh.paycore.ledger.repository.OutboxEventRepository;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxRelayService {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelayService.class);

  private final OutboxEventRepository outboxEventRepository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final String topic;
  private final int batchSize;

  public OutboxRelayService(
      OutboxEventRepository outboxEventRepository,
      @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate,
      @Value("${paycore.ledger.outbox.topic:paycore.ledger.events}") String topic,
      @Value("${paycore.ledger.outbox.batch-size:50}") int batchSize) {
    this.outboxEventRepository = outboxEventRepository;
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${paycore.ledger.outbox.poll-interval-ms:1000}")
  @Transactional
  public int publishPendingEvents() {
    List<OutboxEvent> events = outboxEventRepository.findUnpublished(PageRequest.of(0, batchSize));
    if (events.isEmpty()) {
      return 0;
    }

    for (OutboxEvent event : events) {
      try {
        if (kafkaTemplate != null) {
          kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload());
        }
        event.setPublishedAt(Instant.now());
        outboxEventRepository.save(event);
      } catch (Exception ex) {
        log.error("Failed to relay outbox event id={}: {}", event.getId(), ex.getMessage());
        break;
      }
    }

    return events.size();
  }
}
