package io.github.yesh.paycore.settlement.controller;

import io.github.yesh.paycore.settlement.dto.ReconciliationMatchResponse;
import io.github.yesh.paycore.settlement.dto.ReconciliationRunRequest;
import io.github.yesh.paycore.settlement.dto.ReconciliationRunResponse;
import io.github.yesh.paycore.settlement.reconciliation.ReconciliationEngine;
import io.github.yesh.paycore.settlement.repository.ReconciliationMatchRepository;
import io.github.yesh.paycore.settlement.repository.ReconciliationRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/recon")
public class ReconciliationController {

  private final ReconciliationEngine reconciliationEngine;
  private final ReconciliationRunRepository runRepository;
  private final ReconciliationMatchRepository matchRepository;

  public ReconciliationController(
      ReconciliationEngine reconciliationEngine,
      ReconciliationRunRepository runRepository,
      ReconciliationMatchRepository matchRepository) {
    this.reconciliationEngine = reconciliationEngine;
    this.runRepository = runRepository;
    this.matchRepository = matchRepository;
  }

  @PostMapping("/runs")
  public ResponseEntity<ReconciliationRunResponse> triggerRun(
      @RequestBody(required = false) ReconciliationRunRequest request) {
    ReconciliationRunResponse response = reconciliationEngine.execute(request);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/runs")
  public ResponseEntity<Page<ReconciliationRunResponse>> listRuns(Pageable pageable) {
    return ResponseEntity.ok(
        runRepository
            .findAllByOrderByStartedAtDesc(pageable)
            .map(ReconciliationRunResponse::fromEntity));
  }

  @GetMapping("/runs/{runId}")
  public ResponseEntity<ReconciliationRunResponse> getRun(@PathVariable String runId) {
    return runRepository
        .findByRunId(runId)
        .map(ReconciliationRunResponse::fromEntity)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/runs/{runId}/matches")
  public ResponseEntity<Page<ReconciliationMatchResponse>> getMatches(
      @PathVariable String runId, Pageable pageable) {
    return ResponseEntity.ok(
        matchRepository.findByRunId(runId, pageable).map(ReconciliationMatchResponse::fromEntity));
  }
}
