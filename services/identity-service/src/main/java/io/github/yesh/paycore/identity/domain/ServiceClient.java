package io.github.yesh.paycore.identity.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "service_clients")
public class ServiceClient {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "client_id", nullable = false, unique = true, length = 64)
  private String clientId;

  @Column(name = "secret_hash", nullable = false)
  private String secretHash;

  @Column(name = "roles", columnDefinition = "text[]")
  @JdbcTypeCode(SqlTypes.ARRAY)
  private Set<String> roles = new HashSet<>();

  @Column(nullable = false)
  private boolean enabled = true;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public ServiceClient() {}

  public ServiceClient(String clientId, String secretHash, Set<String> roles) {
    this.clientId = clientId;
    this.secretHash = secretHash;
    this.roles = roles != null ? roles : new HashSet<>();
    this.enabled = true;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getClientId() {
    return clientId;
  }

  public String getSecretHash() {
    return secretHash;
  }

  public Set<String> getRoles() {
    return roles;
  }

  public boolean isEnabled() {
    return enabled;
  }
}
