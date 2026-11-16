package io.github.yesh.paycore.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

public class SecurityHeadersFilterTest {

  private final SecurityHeadersFilter filter = new SecurityHeadersFilter();

  @Test
  @DisplayName("SecurityHeadersFilter adds OWASP mandated security response headers")
  void testSecurityHeadersInjected() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/payments");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (req, res) -> {};

    filter.doFilter(request, response, chain);

    assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    assertThat(response.getHeader("X-Frame-Options")).isEqualTo("DENY");
    assertThat(response.getHeader("Referrer-Policy")).isEqualTo("strict-origin-when-cross-origin");
    assertThat(response.getHeader("Content-Security-Policy")).isNotBlank();
  }
}
