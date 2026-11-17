package io.github.yesh.paycore.ledger.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import io.github.yesh.paycore.ledger.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"paycore.security.enabled=true"})
public class CorsRejectionSecurityTest extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName(
      "E2: Ledger preflight OPTIONS from untrusted origin does NOT receive Access-Control-Allow-Origin or wildcard")
  void testLedgerCorsPreflightRejection() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                options("/v1/payments")
                    .header(HttpHeaders.ORIGIN, "https://evil-hacker.com")
                    .header("Access-Control-Request-Method", "POST")
                    .header(
                        "Access-Control-Request-Headers",
                        "Content-Type,Authorization,Idempotency-Key"))
            .andReturn();

    String allowOrigin = result.getResponse().getHeader("Access-Control-Allow-Origin");
    assertThat(allowOrigin)
        .as("Preflight must not grant Access-Control-Allow-Origin to untrusted origin")
        .satisfies(
            header ->
                assertThat(
                        header == null
                            || (!header.equals("*") && !header.equals("https://evil-hacker.com")))
                    .isTrue());

    String allowCredentials = result.getResponse().getHeader("Access-Control-Allow-Credentials");
    assertThat(allowCredentials)
        .as("Access-Control-Allow-Credentials must not be granted to untrusted origin")
        .isNotEqualTo("true");
  }
}
