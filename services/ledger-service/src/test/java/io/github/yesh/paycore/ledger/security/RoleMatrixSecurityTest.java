package io.github.yesh.paycore.ledger.security;

import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.yesh.paycore.ledger.domain.Seller;
import io.github.yesh.paycore.ledger.dto.AccountResponse;
import io.github.yesh.paycore.ledger.service.AccountService;
import io.github.yesh.paycore.ledger.service.SellerService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"paycore.security.enabled=true"})
public class RoleMatrixSecurityTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private SellerService sellerService;
  @MockBean private AccountService accountService;

  @Test
  @DisplayName("Playbook Section 12: Unauthenticated requests to protected endpoints return 401")
  void testUnauthenticatedReturns401() throws Exception {
    mockMvc.perform(get("/v1/sellers/seller_101")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/v1/payouts/dispatch")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/v1/payments")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Playbook Section 12: SERVICE role can post payouts and deposits")
  void testServiceRoleCanPostPayoutsAndDeposits() throws Exception {
    mockMvc
        .perform(
            post("/v1/provider-deposits")
                .with(
                    jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_SERVICE"))
                        .jwt(j -> j.claim("roles", List.of("SERVICE"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"depositId\":\"dep_test\",\"netAmount\":1000,\"feeAmount\":10,\"reference\":\"ref\",\"valueDate\":\"2026-11-17\"}"))
        // Endpoint reached (not 401/403)
        .andExpect(
            result ->
                org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus())
                    .isNotIn(401, 403));
  }

  @Test
  @DisplayName("Playbook Section 12: Non-SERVICE roles cannot post payouts or deposits (403)")
  void testNonServiceCannotPostPayouts() throws Exception {
    // ADMIN cannot post payouts directly (only SERVICE role)
    mockMvc
        .perform(
            post("/v1/payouts/dispatch")
                .with(
                    jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        .jwt(j -> j.claim("roles", List.of("ADMIN"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());

    // MERCHANT cannot post payouts
    mockMvc
        .perform(
            post("/v1/payouts/dispatch")
                .with(
                    jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"))
                        .jwt(j -> j.claim("roles", List.of("MERCHANT"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName(
      "Playbook Section 12: MERCHANT reads only own seller data (allowed for own sellerId)")
  void testMerchantCanReadOwnData() throws Exception {
    Seller seller = new Seller("seller_101", "ACME Corp", "STANDARD", 200);
    when(sellerService.getSeller("seller_101")).thenReturn(seller);

    mockMvc
        .perform(
            get("/v1/sellers/seller_101")
                .with(
                    jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"))
                        .jwt(
                            j ->
                                j.claim("roles", List.of("MERCHANT"))
                                    .claim("sellerId", "seller_101"))))
        .andExpect(status().isOk());
  }

  @Test
  @DisplayName("Playbook Section 12: MERCHANT cannot read other seller data (403 Forbidden)")
  void testMerchantCannotReadOtherSellerData() throws Exception {
    mockMvc
        .perform(
            get("/v1/sellers/seller_999_other")
                .with(
                    jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"))
                        .jwt(
                            j ->
                                j.claim("roles", List.of("MERCHANT"))
                                    .claim("sellerId", "seller_101"))))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Playbook Section 12: MERCHANT cannot read platform system accounts (403 Forbidden)")
  void testMerchantCannotReadPlatformAccounts() throws Exception {
    mockMvc
        .perform(
            get("/v1/accounts/PLATFORM_CASH")
                .with(
                    jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"))
                        .jwt(
                            j ->
                                j.claim("roles", List.of("MERCHANT"))
                                    .claim("sellerId", "seller_101"))))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Playbook Section 12: MERCHANT can read own account statement")
  void testMerchantCanReadOwnAccount() throws Exception {
    AccountResponse acc =
        new AccountResponse(
            UUID.randomUUID(),
            "seller:seller_101:payable",
            io.github.yesh.paycore.ledger.domain.AccountType.LIABILITY,
            "INR",
            50000L,
            0L,
            50000L,
            false);
    when(accountService.getAccount("seller:seller_101:payable")).thenReturn(acc);

    mockMvc
        .perform(
            get("/v1/accounts/seller:seller_101:payable")
                .with(
                    jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"))
                        .jwt(
                            j ->
                                j.claim("roles", List.of("MERCHANT"))
                                    .claim("sellerId", "seller_101"))))
        .andExpect(status().isOk());
  }
}
