package io.github.yesh.paycore.settlement.controller;

import io.github.yesh.paycore.settlement.dto.AdjustmentResponse;
import io.github.yesh.paycore.settlement.dto.ProposeAdjustmentRequest;
import io.github.yesh.paycore.settlement.service.AdjustmentService;
import jakarta.validation.Valid;
import java.security.Principal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/adjustments")
public class AdjustmentController {

  private final AdjustmentService adjustmentService;

  public AdjustmentController(AdjustmentService adjustmentService) {
    this.adjustmentService = adjustmentService;
  }

  @PostMapping("/propose")
  public ResponseEntity<AdjustmentResponse> propose(
      @Valid @RequestBody ProposeAdjustmentRequest request, Principal principal) {
    String proposer = principal != null ? principal.getName() : "ANALYST_1";
    return ResponseEntity.ok(adjustmentService.proposeAdjustment(request, proposer));
  }

  @PostMapping("/{adjustmentId}/approve")
  public ResponseEntity<AdjustmentResponse> approve(
      @PathVariable String adjustmentId, Principal principal) {
    String approver = principal != null ? principal.getName() : "ADMIN_APPROVER";
    return ResponseEntity.ok(adjustmentService.approveAdjustment(adjustmentId, approver));
  }

  @PostMapping("/{adjustmentId}/reject")
  public ResponseEntity<AdjustmentResponse> reject(
      @PathVariable String adjustmentId,
      @RequestParam(required = false) String reason,
      Principal principal) {
    String rejector = principal != null ? principal.getName() : "ADMIN_REJECTOR";
    return ResponseEntity.ok(adjustmentService.rejectAdjustment(adjustmentId, rejector, reason));
  }

  @GetMapping("/{adjustmentId}")
  public ResponseEntity<AdjustmentResponse> getAdjustment(@PathVariable String adjustmentId) {
    return ResponseEntity.ok(adjustmentService.getAdjustment(adjustmentId));
  }

  @GetMapping
  public ResponseEntity<Page<AdjustmentResponse>> listAdjustments(Pageable pageable) {
    return ResponseEntity.ok(adjustmentService.listAdjustments(pageable));
  }
}
