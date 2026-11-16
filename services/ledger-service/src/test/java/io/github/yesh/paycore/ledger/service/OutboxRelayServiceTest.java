package io.github.yesh.paycore.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.yesh.paycore.ledger.domain.OutboxEvent;
import io.github.yesh.paycore.ledger.repository.OutboxEventRepository;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

public class OutboxRelayServiceTest {

  private OutboxEventRepository repository;
  private KafkaTemplate<String, String> kafkaTemplate;
  private OutboxRelayService service;

  @BeforeEach
  void setUp() {
    repository = mock(OutboxEventRepository.class);
    kafkaTemplate = mock(KafkaTemplate.class);
    service = new OutboxRelayService(repository, kafkaTemplate, "ledger.domain-events", 50);
  }

  @Test
  @DisplayName("F-L-11: Empty outbox queue returns 0 published events")
  void testEmptyQueue() {
    when(repository.claimUnpublishedForUpdate(anyInt())).thenReturn(Collections.emptyList());

    int published = service.publishPendingEvents();
    assertThat(published).isEqualTo(0);
  }

  @Test
  @DisplayName("F-L-11: Outbox events successfully published to Kafka topic and marked published")
  void testSuccessfulRelay() {
    OutboxEvent event =
        new OutboxEvent("PAYMENT", "pay_123", "payment_captured", "{\"amount\":1000}");
    event.setId(101L);

    when(repository.claimUnpublishedForUpdate(anyInt())).thenReturn(List.of(event));

    SendResult<String, String> sendResult = mock(SendResult.class);
    when(kafkaTemplate.send(anyString(), anyString(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(sendResult));

    int published = service.publishPendingEvents();

    assertThat(published).isEqualTo(1);
    verify(kafkaTemplate).send("ledger.domain-events", "pay_123", "{\"amount\":1000}");
    verify(repository).markPublished(eq(List.of(101L)));
  }

  @Test
  @DisplayName("F-L-11: Kafka failure stops batch and uncommitted events are retried")
  void testKafkaFailureStopsBatch() {
    OutboxEvent event1 = new OutboxEvent("PAYMENT", "pay_1", "payment_captured", "{}");
    event1.setId(1L);
    OutboxEvent event2 = new OutboxEvent("PAYMENT", "pay_2", "payment_captured", "{}");
    event2.setId(2L);

    when(repository.claimUnpublishedForUpdate(anyInt())).thenReturn(List.of(event1, event2));

    CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
    failedFuture.completeExceptionally(new RuntimeException("Kafka broker down"));
    when(kafkaTemplate.send(anyString(), anyString(), anyString())).thenReturn(failedFuture);

    int published = service.publishPendingEvents();

    assertThat(published).isEqualTo(0);
  }
}
