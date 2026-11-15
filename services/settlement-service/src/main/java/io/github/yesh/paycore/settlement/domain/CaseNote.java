package io.github.yesh.paycore.settlement.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "case_notes")
public class CaseNote {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "case_id", nullable = false, length = 64)
  private String caseId;

  @Column(nullable = false, length = 64)
  private String author;

  @Column(nullable = false, columnDefinition = "text")
  private String content;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  public CaseNote() {}

  public CaseNote(String caseId, String author, String content) {
    this.caseId = caseId;
    this.author = author;
    this.content = content;
    this.createdAt = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getCaseId() {
    return caseId;
  }

  public String getAuthor() {
    return author;
  }

  public String getContent() {
    return content;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
