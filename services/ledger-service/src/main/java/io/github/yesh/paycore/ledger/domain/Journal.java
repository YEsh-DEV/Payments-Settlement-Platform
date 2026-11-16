package io.github.yesh.paycore.ledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "journals",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_journals_scope_idempotency_key",
          columnNames = {"scope", "idempotency_key"})
    })
public class Journal {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "scope", nullable = false, length = 64)
  private String scope = "global";

  @Column(name = "idempotency_key", nullable = false, length = 128)
  private String idempotencyKey;

  @Column(name = "reference_id", length = 128)
  private String referenceId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private JournalType type;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "posted_at", nullable = false, updatable = false)
  private Instant postedAt = Instant.now();

  public Journal() {}

  public Journal(String idempotencyKey, String referenceId, JournalType type, String description) {
    this("global", idempotencyKey, referenceId, type, description);
  }

  public Journal(
      String scope,
      String idempotencyKey,
      String referenceId,
      JournalType type,
      String description) {
    this.id = UUID.randomUUID();
    this.scope = (scope != null && !scope.isBlank()) ? scope : "global";
    this.idempotencyKey = idempotencyKey;
    this.referenceId = referenceId;
    this.type = type;
    this.description = description;
    this.postedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public void setId(UUID id) {
    this.id = id;
  }

  public String getScope() {
    return scope;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getReferenceId() {
    return referenceId;
  }

  public JournalType getType() {
    return type;
  }

  public String getDescription() {
    return description;
  }

  public Instant getPostedAt() {
    return postedAt;
  }
}
