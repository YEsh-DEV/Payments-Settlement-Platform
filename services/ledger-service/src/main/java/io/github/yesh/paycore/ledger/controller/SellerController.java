package io.github.yesh.paycore.ledger.controller;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.ledger.domain.Seller;
import io.github.yesh.paycore.ledger.dto.OnboardSellerRequest;
import io.github.yesh.paycore.ledger.dto.SellerResponse;
import io.github.yesh.paycore.ledger.service.SellerService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
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
  public ResponseEntity<Seller> getSeller(
      @PathVariable String sellerId, Authentication authentication) {
    validateSellerAccess(sellerId, authentication);
    return ResponseEntity.ok(sellerService.getSeller(sellerId));
  }

  private void validateSellerAccess(String targetSellerId, Authentication authentication) {
    if (authentication == null) {
      return;
    }
    boolean isMerchant =
        authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_MERCHANT"));

    if (isMerchant) {
      String tokenSellerId = null;
      if (authentication.getPrincipal() instanceof Jwt jwt) {
        tokenSellerId = jwt.getClaimAsString("sellerId");
        if (tokenSellerId == null) {
          tokenSellerId = jwt.getClaimAsString("seller_id");
        }
      }
      if (tokenSellerId == null || !tokenSellerId.equalsIgnoreCase(targetSellerId)) {
        throw new PayCoreException(
            HttpStatus.FORBIDDEN, "forbidden", "Merchant may only access their own seller data");
      }
    }
  }
}
