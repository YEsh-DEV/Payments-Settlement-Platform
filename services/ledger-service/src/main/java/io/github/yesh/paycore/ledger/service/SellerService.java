package io.github.yesh.paycore.ledger.service;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.ledger.domain.Account;
import io.github.yesh.paycore.ledger.domain.AccountType;
import io.github.yesh.paycore.ledger.domain.Seller;
import io.github.yesh.paycore.ledger.dto.OnboardSellerRequest;
import io.github.yesh.paycore.ledger.dto.SellerResponse;
import io.github.yesh.paycore.ledger.repository.AccountRepository;
import io.github.yesh.paycore.ledger.repository.SellerRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SellerService {

  private final SellerRepository sellerRepository;
  private final AccountRepository accountRepository;

  public SellerService(SellerRepository sellerRepository, AccountRepository accountRepository) {
    this.sellerRepository = sellerRepository;
    this.accountRepository = accountRepository;
  }

  @Transactional
  public SellerResponse onboardSeller(OnboardSellerRequest request) {
    if (sellerRepository.findBySellerId(request.sellerId()).isPresent()) {
      throw new PayCoreException(
          HttpStatus.CONFLICT,
          "seller_exists",
          "Seller ID " + request.sellerId() + " already exists");
    }

    String feeTier = request.feeTier() != null ? request.feeTier() : "STANDARD";
    int feeBps = request.feeBps() != null ? request.feeBps() : 200;

    Seller seller = new Seller(request.sellerId(), request.name(), feeTier, feeBps);
    Seller savedSeller = sellerRepository.save(seller);

    String accountCode = "MERCHANT_" + request.sellerId();
    Account merchantAccount =
        accountRepository
            .findByCode(accountCode)
            .orElseGet(
                () -> {
                  Account acc = new Account(accountCode, AccountType.LIABILITY, false, 1);
                  return accountRepository.save(acc);
                });

    return new SellerResponse(
        savedSeller.getId(),
        savedSeller.getSellerId(),
        savedSeller.getName(),
        savedSeller.getFeeTier(),
        savedSeller.getFeeBps(),
        merchantAccount.getId(),
        merchantAccount.getCode());
  }

  @Transactional(readOnly = true)
  public Seller getSeller(String sellerId) {
    return sellerRepository
        .findBySellerId(sellerId)
        .orElseThrow(
            () ->
                new PayCoreException(
                    HttpStatus.NOT_FOUND, "seller_not_found", "Seller " + sellerId + " not found"));
  }
}
