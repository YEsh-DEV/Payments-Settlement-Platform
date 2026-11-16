package io.github.yesh.paycore.ledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

  @EmbeddedId private IdempotencyKeyId id;

  @Column(name = "request_hash", nullable = false, length = 64)
  private String requestHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private IdempotencyStatus status;

  @Column(name = "response_body", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String responseBody;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt = Instant.now();

  public IdempotencyRecord() {}

  public IdempotencyRecord(
      IdempotencyKeyId id, String requestHash, IdempotencyStatus status, String responseBody) {
    this.id = id;
    this.requestHash = requestHash;
    this.status = status;
    this.responseBody = responseBody;
    this.createdAt = Instant.now();
  }

  public IdempotencyRecord(
      String scope, String key, String requestHash, IdempotencyStatus status, String responseBody) {
    this(new IdempotencyKeyId(scope, key), requestHash, status, responseBody);
  }

  public IdempotencyRecord(
      String key, String requestHash, IdempotencyStatus status, String responseBody) {
    this("global", key, requestHash, status, responseBody);
  }

  public IdempotencyKeyId getId() {
    return id;
  }

  public String getKey() {
    return id != null ? id.getKey() : null;
  }

  public String getScope() {
    return id != null ? id.getScope() : null;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public IdempotencyStatus getStatus() {
    return status;
  }

  public void setStatus(IdempotencyStatus status) {
    this.status = status;
  }

  public String getResponseBody() {
    return responseBody;
  }

  public void setResponseBody(String responseBody) {
    this.responseBody = responseBody;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Instant createdAt) {
    this.createdAt = createdAt;
  }
}
