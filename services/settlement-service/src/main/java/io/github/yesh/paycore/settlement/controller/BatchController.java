package io.github.yesh.paycore.settlement.controller;

import io.github.yesh.paycore.settlement.domain.SettlementRecord;
import io.github.yesh.paycore.settlement.dto.BatchCutoffRequest;
import io.github.yesh.paycore.settlement.dto.BatchResponse;
import io.github.yesh.paycore.settlement.service.BatchCutoffService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/batches")
public class BatchController {

  private final BatchCutoffService batchCutoffService;

  public BatchController(BatchCutoffService batchCutoffService) {
    this.batchCutoffService = batchCutoffService;
  }

  @PostMapping("/run")
  public ResponseEntity<BatchResponse> runCutoff(
      @RequestBody(required = false) BatchCutoffRequest request) {
    BatchResponse response = batchCutoffService.runCutoff(request);
    return ResponseEntity.ok(response);
  }

  @GetMapping
  public ResponseEntity<Page<BatchResponse>> listBatches(Pageable pageable) {
    return ResponseEntity.ok(batchCutoffService.listBatches(pageable));
  }

  @GetMapping("/{batchId}")
  public ResponseEntity<BatchResponse> getBatch(@PathVariable String batchId) {
    return ResponseEntity.ok(batchCutoffService.getBatch(batchId));
  }

  @GetMapping("/{batchId}/records")
  public ResponseEntity<Page<SettlementRecord>> getBatchRecords(
      @PathVariable String batchId, Pageable pageable) {
    return ResponseEntity.ok(batchCutoffService.getBatchRecords(batchId, pageable));
  }
}
