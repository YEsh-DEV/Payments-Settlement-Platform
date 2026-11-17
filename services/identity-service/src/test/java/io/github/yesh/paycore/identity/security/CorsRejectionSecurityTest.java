package io.github.yesh.paycore.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import io.github.yesh.paycore.identity.BaseIntegrationTest;
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
      "E2: Preflight OPTIONS from untrusted origin does NOT receive Access-Control-Allow-Origin or wildcard")
  void testCorsPreflightRejection() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                options("/auth/login")
                    .header(HttpHeaders.ORIGIN, "https://malicious-site.example.com")
                    .header("Access-Control-Request-Method", "POST")
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
      "E2: Direct request with untrusted Origin header does not reflect wildcard or attacker origin")
  void testDirectRequestWithOriginHeader() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/auth/login")
                    .header(HttpHeaders.ORIGIN, "https://evil.org")
                    .contentType("application/json")
                    .content("{\"username\":\"admin\",\"password\":\"wrong\"}"))
            .andReturn();

    String allowOrigin = result.getResponse().getHeader("Access-Control-Allow-Origin");
    assertThat(allowOrigin)
        .as(
            "Direct requests must never return wildcard or attacker origin in Access-Control-Allow-Origin")
        .satisfies(
            header ->
                assertThat(
                        header == null
                            || (!header.equals("*") && !header.equals("https://evil.org")))
                    .isTrue());
  }
}
