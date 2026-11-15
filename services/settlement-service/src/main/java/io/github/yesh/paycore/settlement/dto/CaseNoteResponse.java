package io.github.yesh.paycore.settlement.dto;

import io.github.yesh.paycore.settlement.domain.CaseNote;
import java.time.Instant;

public record CaseNoteResponse(
    Long id, String caseId, String author, String content, Instant createdAt) {

  public static CaseNoteResponse fromEntity(CaseNote n) {
    return new CaseNoteResponse(
        n.getId(), n.getCaseId(), n.getAuthor(), n.getContent(), n.getCreatedAt());
  }
}
