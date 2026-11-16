package io.github.yesh.paycore.ledger.controller;

import io.github.yesh.paycore.ledger.domain.Seller;
import io.github.yesh.paycore.ledger.dto.OnboardSellerRequest;
import io.github.yesh.paycore.ledger.dto.SellerResponse;
import io.github.yesh.paycore.ledger.service.SellerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/v1/sellers", "/sellers"})
public class SellerController {

  private final SellerService sellerService;

  public SellerController(SellerService sellerService) {
    this.sellerService = sellerService;
  }

  @PostMapping
  public ResponseEntity<SellerResponse> onboardSeller(
      @Valid @RequestBody OnboardSellerRequest request) {
    SellerResponse response = sellerService.onboardSeller(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @GetMapping("/{sellerId}")
  public ResponseEntity<Seller> getSeller(@PathVariable String sellerId) {
    return ResponseEntity.ok(sellerService.getSeller(sellerId));
  }
}
