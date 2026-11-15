package io.github.yesh.paycore.ledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "idempotency_keys")
public class IdempotencyRecord {

  @Id
  @Column(name = "key", length = 128)
  private String key;

  @Column(name = "request_hash", nullable = false, length = 64)
  private String requestHash;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private IdempotencyStatus status;

  @Column(name = "response_body", columnDefinition = "jsonb")
  @JdbcTypeCode(SqlTypes.JSON)
  private String responseBody;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public IdempotencyRecord() {}

  public IdempotencyRecord(
      String key, String requestHash, IdempotencyStatus status, String responseBody) {
    this.key = key;
    this.requestHash = requestHash;
    this.status = status;
    this.responseBody = responseBody;
    this.createdAt = Instant.now();
  }

  public String getKey() {
    return key;
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
}
