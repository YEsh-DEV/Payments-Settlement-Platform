package io.github.yesh.paycore.ledger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.ledger.domain.Account;
import io.github.yesh.paycore.ledger.domain.AccountType;
import io.github.yesh.paycore.ledger.domain.Seller;
import io.github.yesh.paycore.ledger.dto.OnboardSellerRequest;
import io.github.yesh.paycore.ledger.dto.SellerResponse;
import io.github.yesh.paycore.ledger.repository.AccountRepository;
import io.github.yesh.paycore.ledger.repository.SellerRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class SellerServiceTest {

  private SellerRepository sellerRepository;
  private AccountRepository accountRepository;
  private SellerService sellerService;

  @BeforeEach
  void setUp() {
    sellerRepository = mock(SellerRepository.class);
    accountRepository = mock(AccountRepository.class);
    sellerService = new SellerService(sellerRepository, accountRepository);
  }

  @Test
  @DisplayName("F-L-01: Onboard seller provisions seller and creates merchant liability account")
  void testOnboardSellerSuccess() {
    OnboardSellerRequest request =
        new OnboardSellerRequest("seller_100", "Acme Store", "ENTERPRISE", 150);

    when(sellerRepository.findBySellerId("seller_100")).thenReturn(Optional.empty());

    Seller saved = new Seller("seller_100", "Acme Store", "ENTERPRISE", 150);
    when(sellerRepository.save(any(Seller.class))).thenReturn(saved);

    Account account = new Account("MERCHANT_seller_100", AccountType.LIABILITY, false, 1);
    when(accountRepository.findByCode("MERCHANT_seller_100")).thenReturn(Optional.of(account));

    SellerResponse response = sellerService.onboardSeller(request);

    assertThat(response.sellerId()).isEqualTo("seller_100");
    assertThat(response.name()).isEqualTo("Acme Store");
    assertThat(response.feeTier()).isEqualTo("ENTERPRISE");
    assertThat(response.feeBps()).isEqualTo(150);
    assertThat(response.accountCode()).isEqualTo("MERCHANT_seller_100");
  }

  @Test
  @DisplayName("F-L-01: Onboard duplicate seller throws conflict exception")
  void testOnboardDuplicateSellerFails() {
    OnboardSellerRequest request =
        new OnboardSellerRequest("seller_100", "Acme Store", "STANDARD", 200);

    when(sellerRepository.findBySellerId("seller_100"))
        .thenReturn(Optional.of(new Seller("seller_100", "Acme Store", "STANDARD", 200)));

    assertThatThrownBy(() -> sellerService.onboardSeller(request))
        .isInstanceOf(PayCoreException.class)
        .hasMessageContaining("already exists");
  }

  @Test
  @DisplayName("F-L-01: Get seller returns seller response or throws 404")
  void testGetSeller() {
    Seller seller = new Seller("seller_200", "Beta Shop", "STANDARD", 200);
    when(sellerRepository.findBySellerId("seller_200")).thenReturn(Optional.of(seller));
    Account account = new Account("MERCHANT_seller_200", AccountType.LIABILITY, false, 1);
    when(accountRepository.findByCode("MERCHANT_seller_200")).thenReturn(Optional.of(account));

    Seller response = sellerService.getSeller("seller_200");
    assertThat(response.getName()).isEqualTo("Beta Shop");

    when(sellerRepository.findBySellerId("unknown")).thenReturn(Optional.empty());
    assertThatThrownBy(() -> sellerService.getSeller("unknown"))
        .isInstanceOf(PayCoreException.class)
        .hasMessageContaining("not found");
  }
}
