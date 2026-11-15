package io.github.yesh.paycore.settlement.domain;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "simulation_manifests")
public class SimulationManifest {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "batch_id", nullable = false, length = 64)
  private String batchId;

  @Column(nullable = false, length = 128)
  private String filename;

  @Column(name = "fault_type", nullable = false, length = 64)
  private String faultType;

  @Column(name = "reference_id", nullable = false, length = 128)
  private String referenceId;

  @Column(columnDefinition = "text")
  private String description;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public SimulationManifest() {}

  public SimulationManifest(
      String batchId, String filename, String faultType, String referenceId, String description) {
    this.batchId = batchId;
    this.filename = filename;
    this.faultType = faultType;
    this.referenceId = referenceId;
    this.description = description;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getBatchId() {
    return batchId;
  }

  public String getFilename() {
    return filename;
  }

  public String getFaultType() {
    return faultType;
  }

  public String getReferenceId() {
    return referenceId;
  }

  public String getDescription() {
    return description;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
