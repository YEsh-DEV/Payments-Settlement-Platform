package io.github.yesh.paycore.ledger.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

/** Composite key for IdempotencyRecord scoped by caller identity. */
@Embeddable
public class IdempotencyKeyId implements Serializable {

  @Column(name = "scope", nullable = false, length = 128)
  private String scope;

  @Column(name = "key", nullable = false, length = 128)
  private String key;

  public IdempotencyKeyId() {}

  public IdempotencyKeyId(String scope, String key) {
    this.scope = (scope != null && !scope.isBlank()) ? scope : "global";
    this.key = key;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    IdempotencyKeyId that = (IdempotencyKeyId) o;
    return Objects.equals(scope, that.scope) && Objects.equals(key, that.key);
  }

  @Override
  public int hashCode() {
    return Objects.hash(scope, key);
  }
}
