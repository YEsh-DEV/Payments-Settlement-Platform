package io.github.yesh.paycore.ledger.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sellers")
public class Seller {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "seller_id", nullable = false, unique = true, length = 64)
  private String sellerId;

  @Column(nullable = false, length = 128)
  private String name;

  @Column(name = "fee_tier", nullable = false, length = 32)
  private String feeTier = "STANDARD";

  @Column(name = "fee_bps", nullable = false)
  private int feeBps = 200;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public Seller() {}

  public Seller(String sellerId, String name, String feeTier, int feeBps) {
    this.sellerId = sellerId;
    this.name = name;
    this.feeTier = feeTier != null ? feeTier : "STANDARD";
    this.feeBps = feeBps >= 0 ? feeBps : 200;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getSellerId() {
    return sellerId;
  }

  public String getName() {
    return name;
  }

  public String getFeeTier() {
    return feeTier;
  }

  public int getFeeBps() {
    return feeBps;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
