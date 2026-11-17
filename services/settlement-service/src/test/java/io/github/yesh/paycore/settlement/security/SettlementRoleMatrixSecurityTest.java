package io.github.yesh.paycore.settlement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.yesh.paycore.settlement.BaseIntegrationTest;
import io.github.yesh.paycore.settlement.service.AdjustmentService;
import io.github.yesh.paycore.settlement.service.BatchCutoffService;
import io.github.yesh.paycore.settlement.service.CaseService;
import java.util.List;
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
@TestPropertySource(
    properties = {
      "paycore.security.enabled=true",
      "paycore.security.jwks-uri=http://localhost:8081/.well-known/jwks.json"
    })
public class SettlementRoleMatrixSecurityTest extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private BatchCutoffService batchCutoffService;
  @MockBean private AdjustmentService adjustmentService;
  @MockBean private CaseService caseService;

  @Test
  @DisplayName("Playbook Section 12: Unauthenticated requests to settlement endpoints return 401")
  void testUnauthenticatedReturns401() throws Exception {
    mockMvc.perform(post("/v1/batches/run")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/v1/batches")).andExpect(status().isUnauthorized());
    mockMvc.perform(get("/v1/cases")).andExpect(status().isUnauthorized());
    mockMvc.perform(post("/v1/adjustments/ADJ_1/approve")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("Playbook Section 12: ADMIN and SERVICE roles can trigger batch cutoff")
  void testAdminAndServiceCanRunBatch() throws Exception {
    mockMvc
        .perform(
            post("/v1/batches/run")
                .with(
                    jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        .jwt(j -> j.claim("roles", List.of("ADMIN"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));

    mockMvc
        .perform(
            post("/v1/batches/run")
                .with(
                    jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_SERVICE"))
                        .jwt(j -> j.claim("roles", List.of("SERVICE"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
  }

  @Test
  @DisplayName(
      "Playbook Section 12: ANALYST and MERCHANT cannot trigger batch cutoff (403 Forbidden)")
  void testAnalystAndMerchantCannotRunBatch() throws Exception {
    mockMvc
        .perform(
            post("/v1/batches/run")
                .with(
                    jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ANALYST"))
                        .jwt(j -> j.claim("roles", List.of("ANALYST"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());

    mockMvc
        .perform(
            post("/v1/batches/run")
                .with(
                    jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"))
                        .jwt(j -> j.claim("roles", List.of("MERCHANT"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Playbook Section 12: Only ADMIN can approve adjustments (ANALYST gets 403)")
  void testOnlyAdminCanApproveAdjustments() throws Exception {
    // ANALYST cannot approve adjustments
    mockMvc
        .perform(
            post("/v1/adjustments/ADJ_1/approve")
                .with(
                    jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ANALYST"))
                        .jwt(j -> j.claim("roles", List.of("ANALYST")))))
        .andExpect(status().isForbidden());

    // ADMIN can approve adjustments
    mockMvc
        .perform(
            post("/v1/adjustments/ADJ_1/approve")
                .with(
                    jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        .jwt(j -> j.claim("roles", List.of("ADMIN")))))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403));
  }

  @Test
  @DisplayName("Playbook Section 12: ANALYST and ADMIN can view cases and batches, MERCHANT cannot")
  void testCaseAndBatchViewingPermissions() throws Exception {
    mockMvc
        .perform(
            get("/v1/cases")
                .with(
                    jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ANALYST"))
                        .jwt(j -> j.claim("roles", List.of("ANALYST")))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/v1/batches")
                .with(
                    jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
                        .jwt(j -> j.claim("roles", List.of("ADMIN")))))
        .andExpect(status().isOk());

    mockMvc
        .perform(
            get("/v1/cases")
                .with(
                    jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_MERCHANT"))
                        .jwt(j -> j.claim("roles", List.of("MERCHANT")))))
        .andExpect(status().isForbidden());
  }
}
