package io.github.yesh.paycore.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_log")
public class AuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 64)
  private String actor;

  @Column(nullable = false, length = 64)
  private String action;

  @Column(nullable = false, length = 64)
  private String entity;

  @Column(name = "entity_id", nullable = false, length = 64)
  private String entityId;

  @Column(columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String details;

  @Column(name = "request_id", length = 64)
  private String requestId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public AuditLog() {}

  public AuditLog(
      String actor,
      String action,
      String entity,
      String entityId,
      String details,
      String requestId) {
    this.actor = actor;
    this.action = action;
    this.entity = entity;
    this.entityId = entityId;
    this.details = details;
    this.requestId = requestId;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getActor() {
    return actor;
  }

  public String getAction() {
    return action;
  }

  public String getEntity() {
    return entity;
  }

  public String getEntityId() {
    return entityId;
  }

  public String getDetails() {
    return details;
  }

  public String getRequestId() {
    return requestId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
