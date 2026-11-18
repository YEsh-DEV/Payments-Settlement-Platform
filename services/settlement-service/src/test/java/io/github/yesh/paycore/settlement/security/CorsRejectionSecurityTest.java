package io.github.yesh.paycore.settlement.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import io.github.yesh.paycore.settlement.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
public class CorsRejectionSecurityTest extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName(
      "E2: Settlement Service Preflight OPTIONS from untrusted origin does NOT receive Access-Control-Allow-Origin or wildcard")
  void testSettlementCorsPreflightRejection() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                options("/v1/batches")
                    .header(HttpHeaders.ORIGIN, "https://malicious-site.example.com")
                    .header("Access-Control-Request-Method", "GET")
                    .header("Access-Control-Request-Headers", "Content-Type,Authorization"))
            .andReturn();

    String allowOrigin = result.getResponse().getHeader("Access-Control-Allow-Origin");
    assertThat(allowOrigin)
        .as("Cross-origin preflight must not grant Access-Control-Allow-Origin to untrusted origin")
        .satisfies(
            header ->
                assertThat(
                        header == null
                            || (!header.equals("*")
                                && !header.equals("https://malicious-site.example.com")))
                    .isTrue());

    String allowCredentials = result.getResponse().getHeader("Access-Control-Allow-Credentials");
    assertThat(allowCredentials)
        .as(
            "Access-Control-Allow-Credentials must not be granted to untrusted cross-origin requests")
        .isNotEqualTo("true");
  }

  @Test
  @DisplayName(
      "E2: Settlement Service Direct request with untrusted Origin header does not reflect wildcard or attacker origin")
  void testSettlementDirectRequestWithOriginHeader() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/v1/batches")
                    .header(HttpHeaders.ORIGIN, "https://evil-attacker.com")
                    .header(HttpHeaders.CONTENT_TYPE, "application/json"))
            .andReturn();

    String allowOrigin = result.getResponse().getHeader("Access-Control-Allow-Origin");
    assertThat(allowOrigin)
        .as("Direct request must not reflect untrusted origin")
        .satisfies(
            header ->
                assertThat(
                        header == null
                            || (!header.equals("*") && !header.equals("https://evil-attacker.com")))
                    .isTrue());

    String allowCredentials = result.getResponse().getHeader("Access-Control-Allow-Credentials");
    assertThat(allowCredentials)
        .as("Access-Control-Allow-Credentials must not be true for untrusted origin")
        .isNotEqualTo("true");
  }
}
