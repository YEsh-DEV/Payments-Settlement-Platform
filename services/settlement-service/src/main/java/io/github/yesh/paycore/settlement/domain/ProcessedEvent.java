package io.github.yesh.paycore.settlement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

  @Id
  @Column(name = "event_id", length = 128)
  private String eventId;

  @Column(nullable = false, length = 64)
  private String source;

  @Column(name = "processed_at", nullable = false, updatable = false)
  private Instant processedAt = Instant.now();

  public ProcessedEvent() {}

  public ProcessedEvent(String eventId, String source) {
    this.eventId = eventId;
    this.source = source;
    this.processedAt = Instant.now();
  }

  public String getEventId() {
    return eventId;
  }

  public String getSource() {
    return source;
  }

  public Instant getProcessedAt() {
    return processedAt;
  }
}
