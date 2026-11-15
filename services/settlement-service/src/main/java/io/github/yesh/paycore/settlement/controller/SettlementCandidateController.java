package io.github.yesh.paycore.settlement.controller;

import io.github.yesh.paycore.settlement.domain.SettlementRecord;
import io.github.yesh.paycore.settlement.dto.SettlementCandidateRequest;
import io.github.yesh.paycore.settlement.service.SettlementCandidateConsumer;
import jakarta.validation.Valid;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/candidates")
public class SettlementCandidateController {

  private final SettlementCandidateConsumer consumer;

  public SettlementCandidateController(SettlementCandidateConsumer consumer) {
    this.consumer = consumer;
  }

  @PostMapping
  public ResponseEntity<SettlementRecord> createCandidate(
      @Valid @RequestBody SettlementCandidateRequest request) {
    Optional<SettlementRecord> record = consumer.ingestCandidate(request);
    return record
        .map(r -> ResponseEntity.status(HttpStatus.CREATED).body(r))
        .orElseGet(() -> ResponseEntity.status(HttpStatus.OK).build());
  }
}
