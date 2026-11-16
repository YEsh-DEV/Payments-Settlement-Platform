package io.github.yesh.paycore.ledger.service;

import io.github.yesh.paycore.ledger.domain.OutboxEvent;
import io.github.yesh.paycore.ledger.repository.OutboxEventRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
      @Value("${paycore.ledger.outbox.topic:ledger.domain-events}") String topic,
      @Value("${paycore.ledger.outbox.batch-size:50}") int batchSize) {
    this.outboxEventRepository = outboxEventRepository;
    this.kafkaTemplate = kafkaTemplate;
    this.topic = topic;
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${paycore.ledger.outbox.poll-interval-ms:1000}")
  public int publishPendingEvents() {
    List<OutboxEvent> events = claimEvents(batchSize);
    if (events.isEmpty()) {
      return 0;
    }

    List<Long> publishedIds = new ArrayList<>();
    for (OutboxEvent event : events) {
      try {
        if (kafkaTemplate != null) {
          kafkaTemplate
              .send(topic, event.getAggregateId(), event.getPayload())
              .get(5, TimeUnit.SECONDS);
        }
        publishedIds.add(event.getId());
      } catch (Exception ex) {
        log.error("Failed to relay outbox event id={}: {}", event.getId(), ex.getMessage());
        break; // Stop batch on Kafka failure; un-marked events will be reclaimed after lease expiry
      }
    }

    if (!publishedIds.isEmpty()) {
      markEventsPublished(publishedIds);
    }

    return publishedIds.size();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public List<OutboxEvent> claimEvents(int limit) {
    try {
      return outboxEventRepository.claimUnpublishedForUpdate(limit);
    } catch (Exception e) {
      log.warn("Failed to claim outbox events with SKIP LOCKED: {}", e.getMessage());
      return List.of();
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void markEventsPublished(List<Long> ids) {
    outboxEventRepository.markPublished(ids);
  }
}
