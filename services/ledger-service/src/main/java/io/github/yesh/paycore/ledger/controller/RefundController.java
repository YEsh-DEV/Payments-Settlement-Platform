package io.github.yesh.paycore.ledger.controller;

import io.github.yesh.paycore.ledger.dto.PaymentRefundRequest;
import io.github.yesh.paycore.ledger.dto.RefundRequest;
import io.github.yesh.paycore.ledger.dto.RefundResponse;
import io.github.yesh.paycore.ledger.service.RefundService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class RefundController {

  private final RefundService refundService;

  public RefundController(RefundService refundService) {
    this.refundService = refundService;
  }

  @PostMapping({"/v1/refunds", "/refunds"})
  public ResponseEntity<RefundResponse> processRefund(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody RefundRequest request) {
    RefundResponse response = refundService.processRefund(request, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PostMapping({"/v1/payments/{paymentId}/refunds", "/payments/{paymentId}/refunds"})
  public ResponseEntity<RefundResponse> processPaymentRefund(
      @PathVariable String paymentId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @Valid @RequestBody PaymentRefundRequest request) {
    String refundId =
        (request.refundId() != null && !request.refundId().isBlank())
            ? request.refundId()
            : "ref_" + UUID.randomUUID().toString().substring(0, 8);
    RefundRequest fullRequest =
        new RefundRequest(refundId, paymentId, request.amountPaise(), request.reason());
    RefundResponse response = refundService.processRefund(fullRequest, idempotencyKey);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
