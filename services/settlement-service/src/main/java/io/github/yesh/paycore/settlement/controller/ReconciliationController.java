package io.github.yesh.paycore.settlement.controller;

import io.github.yesh.paycore.settlement.dto.ReconciliationMatchResponse;
import io.github.yesh.paycore.settlement.dto.ReconciliationRunRequest;
import io.github.yesh.paycore.settlement.dto.ReconciliationRunResponse;
import io.github.yesh.paycore.settlement.reconciliation.ReconciliationEngine;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/recon")
public class ReconciliationController {

  private final ReconciliationEngine reconciliationEngine;

  public ReconciliationController(ReconciliationEngine reconciliationEngine) {
    this.reconciliationEngine = reconciliationEngine;
  }

  @PostMapping("/runs")
  public ResponseEntity<ReconciliationRunResponse> triggerRun(
      @RequestBody(required = false) ReconciliationRunRequest request) {
    ReconciliationRunResponse response = reconciliationEngine.execute(request);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/runs")
  public ResponseEntity<Page<ReconciliationRunResponse>> listRuns(Pageable pageable) {
    return ResponseEntity.ok(reconciliationEngine.listRuns(pageable));
  }

  @GetMapping("/runs/{runId}")
  public ResponseEntity<ReconciliationRunResponse> getRun(@PathVariable String runId) {
    return reconciliationEngine
        .getRun(runId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }

  @GetMapping("/runs/{runId}/matches")
  public ResponseEntity<Page<ReconciliationMatchResponse>> getMatches(
      @PathVariable String runId, Pageable pageable) {
    return ResponseEntity.ok(reconciliationEngine.getMatches(runId, pageable));
  }
}
