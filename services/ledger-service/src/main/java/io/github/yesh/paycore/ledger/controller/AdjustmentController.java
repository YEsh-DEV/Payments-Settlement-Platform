package io.github.yesh.paycore.ledger.controller;

import io.github.yesh.paycore.ledger.dto.AdjustmentResponse;
import io.github.yesh.paycore.ledger.dto.CreateAdjustmentRequest;
import io.github.yesh.paycore.ledger.service.AdjustmentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/adjustments")
public class AdjustmentController {

  private final AdjustmentService adjustmentService;

  public AdjustmentController(AdjustmentService adjustmentService) {
    this.adjustmentService = adjustmentService;
  }

  @PostMapping
  public ResponseEntity<AdjustmentResponse> postAdjustment(
      @Valid @RequestBody CreateAdjustmentRequest request) {
    AdjustmentResponse response = adjustmentService.postAdjustment(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
