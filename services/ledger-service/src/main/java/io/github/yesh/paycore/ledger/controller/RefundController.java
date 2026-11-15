package io.github.yesh.paycore.ledger.controller;

import io.github.yesh.paycore.ledger.dto.RefundRequest;
import io.github.yesh.paycore.ledger.dto.RefundResponse;
import io.github.yesh.paycore.ledger.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/refunds")
public class RefundController {

  private final RefundService refundService;

  public RefundController(RefundService refundService) {
    this.refundService = refundService;
  }

  @PostMapping
  public ResponseEntity<RefundResponse> processRefund(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody RefundRequest request) {
    RefundResponse response = refundService.processRefund(request, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
