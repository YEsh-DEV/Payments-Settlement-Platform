package io.github.yesh.paycore.ledger.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.ledger.controller.AdjustmentController;
import io.github.yesh.paycore.ledger.controller.PayoutController;
import io.github.yesh.paycore.ledger.controller.ProviderDepositController;
import io.github.yesh.paycore.ledger.dto.CreateAdjustmentRequest;
import io.github.yesh.paycore.ledger.dto.CreateProviderDepositRequest;
import io.github.yesh.paycore.ledger.dto.InitiatePayoutRequest;
import io.github.yesh.paycore.ledger.service.AdjustmentService;
import io.github.yesh.paycore.ledger.service.PayoutService;
import io.github.yesh.paycore.ledger.service.ProviderDepositService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
    controllers = {
      PayoutController.class,
      ProviderDepositController.class,
      AdjustmentController.class
    })
@Import(io.github.yesh.paycore.ledger.config.SecurityConfig.class)
@TestPropertySource(
    properties = {
      "paycore.security.enabled=true",
      "paycore.security.jwks-uri=http://localhost:8081/.well-known/jwks.json"
    })
class LedgerSecurityRoleEnforcementTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private PayoutService payoutService;
  @MockBean private ProviderDepositService providerDepositService;
  @MockBean private AdjustmentService adjustmentService;
  @MockBean private JwtDecoder jwtDecoder;

  private SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor jwtWithRole(String role) {
    return SecurityMockMvcRequestPostProcessors.jwt()
        .jwt(
            builder -> {
              builder.claim("roles", List.of(role));
              builder.subject("test-subject");
            })
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }

  @Test
  @DisplayName("Unauthenticated request to POST /v1/payouts returns 401 Unauthorized")
  void testUnauthenticatedPayoutsReturns401() throws Exception {
    InitiatePayoutRequest request = new InitiatePayoutRequest("p_1", "sel_1", 50000L);

    mockMvc
        .perform(
            post("/v1/payouts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("MERCHANT or ANALYST token to POST /v1/payouts returns 403 Forbidden")
  void testMerchantOrAnalystPayoutsReturns403() throws Exception {
    InitiatePayoutRequest request = new InitiatePayoutRequest("p_2", "sel_1", 50000L);

    mockMvc
        .perform(
            post("/v1/payouts")
                .with(jwtWithRole("MERCHANT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/v1/payouts")
                .with(jwtWithRole("ANALYST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("SERVICE token to POST /v1/payouts is permitted (201 Created)")
  void testServicePayoutsPermitted() throws Exception {
    InitiatePayoutRequest request = new InitiatePayoutRequest("p_3", "sel_1", 50000L);

    mockMvc
        .perform(
            post("/v1/payouts")
                .with(jwtWithRole("SERVICE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("ANALYST token to POST /v1/provider-deposits returns 403 Forbidden")
  void testAnalystProviderDepositsReturns403() throws Exception {
    CreateProviderDepositRequest request =
        new CreateProviderDepositRequest("dep_1", 100000L, 500L, "REF_1", LocalDate.now());

    mockMvc
        .perform(
            post("/v1/provider-deposits")
                .with(jwtWithRole("ANALYST"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("SERVICE token to POST /v1/provider-deposits is permitted (201 Created)")
  void testServiceProviderDepositsPermitted() throws Exception {
    CreateProviderDepositRequest request =
        new CreateProviderDepositRequest("dep_2", 100000L, 500L, "REF_2", LocalDate.now());

    mockMvc
        .perform(
            post("/v1/provider-deposits")
                .with(jwtWithRole("SERVICE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("MERCHANT token to POST /v1/adjustments returns 403 Forbidden")
  void testMerchantAdjustmentsReturns403() throws Exception {
    CreateAdjustmentRequest request =
        new CreateAdjustmentRequest("sel_1", "CREDIT", 1000L, "Reason", "ref");

    mockMvc
        .perform(
            post("/v1/adjustments")
                .with(jwtWithRole("MERCHANT"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("SERVICE token to POST /v1/adjustments is permitted (201 Created)")
  void testServiceAdjustmentsPermitted() throws Exception {
    CreateAdjustmentRequest request =
        new CreateAdjustmentRequest("sel_1", "CREDIT", 1000L, "Reason", "ref");

    mockMvc
        .perform(
            post("/v1/adjustments")
                .with(jwtWithRole("SERVICE"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
        .andExpect(status().isCreated());
  }
}
