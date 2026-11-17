package io.github.yesh.paycore.identity.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.yesh.paycore.identity.BaseIntegrationTest;
import io.github.yesh.paycore.identity.domain.User;
import io.github.yesh.paycore.identity.repository.UserRepository;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class IdentityRoleMatrixSecurityTest extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  @BeforeEach
  void setUpUsers() {
    if (userRepository.findByUsernameIgnoreCase("admin_user").isEmpty()) {
      userRepository.save(
          new User("admin_user", passwordEncoder.encode("pwd"), Set.of("ADMIN"), null));
    }
    if (userRepository.findByUsernameIgnoreCase("operator_user").isEmpty()) {
      userRepository.save(
          new User("operator_user", passwordEncoder.encode("pwd"), Set.of("OPERATOR"), null));
    }
  }

  @Test
  @DisplayName("Playbook Section 12: Unauthenticated request to /admin/users returns 401")
  void testUnauthenticatedUsersEndpointReturns401() throws Exception {
    mockMvc.perform(get("/admin/users")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(
      username = "operator_user",
      roles = {"OPERATOR"})
  @DisplayName(
      "Playbook Section 12: Non-ADMIN role (OPERATOR) to /admin/users returns 403 Forbidden")
  void testOperatorAccessToUsersForbidden() throws Exception {
    mockMvc.perform(get("/admin/users")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(
      username = "merchant_user",
      roles = {"MERCHANT"})
  @DisplayName(
      "Playbook Section 12: Non-ADMIN role (MERCHANT) to /admin/users returns 403 Forbidden")
  void testMerchantAccessToUsersForbidden() throws Exception {
    mockMvc.perform(get("/admin/users")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(
      username = "admin_user",
      roles = {"ADMIN"})
  @DisplayName("Playbook Section 12: ADMIN role to /admin/users is permitted (200 OK)")
  void testAdminAccessToUsersPermitted() throws Exception {
    mockMvc.perform(get("/admin/users")).andExpect(status().isOk());
  }

  @Test
  @DisplayName("Playbook Section 12: Unauthenticated request to /admin/audit returns 401")
  void testUnauthenticatedAuditEndpointReturns401() throws Exception {
    mockMvc.perform(get("/admin/audit")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(
      username = "merchant_user",
      roles = {"MERCHANT"})
  @DisplayName(
      "Playbook Section 12: Non-ADMIN/AUDITOR role (MERCHANT) to /admin/audit returns 403 Forbidden")
  void testMerchantAccessToAuditForbidden() throws Exception {
    mockMvc.perform(get("/admin/audit")).andExpect(status().isForbidden());
  }

  @Test
  @WithMockUser(
      username = "admin_user",
      roles = {"ADMIN"})
  @DisplayName("Playbook Section 12: ADMIN role to /admin/audit is permitted (200 OK)")
  void testAdminAccessToAuditPermitted() throws Exception {
    mockMvc.perform(get("/admin/audit")).andExpect(status().isOk());
  }

  @Test
  @WithMockUser(
      username = "auditor_user",
      roles = {"AUDITOR"})
  @DisplayName("Playbook Section 12: AUDITOR without ADMIN role to /admin/audit is Forbidden (403)")
  void testAuditorAccessToAuditForbidden() throws Exception {
    mockMvc.perform(get("/admin/audit")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("Playbook Section 12: Unauthenticated request to /auth/me returns 401")
  void testUnauthenticatedMeReturns401() throws Exception {
    mockMvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  @WithMockUser(
      username = "operator_user",
      roles = {"OPERATOR"})
  @DisplayName("Playbook Section 12: Authenticated user to /auth/me returns 200 OK")
  void testAuthenticatedMeReturns200() throws Exception {
    mockMvc.perform(get("/auth/me")).andExpect(status().isOk());
  }
}
