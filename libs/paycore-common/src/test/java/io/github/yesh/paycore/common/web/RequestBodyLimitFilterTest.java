package io.github.yesh.paycore.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.yesh.paycore.common.error.PayCoreException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestBodyLimitFilterTest {

  private final RequestBodyLimitFilter filter = new RequestBodyLimitFilter();

  @Test
  @DisplayName(
      "E5: Request with Content-Length > 2 MB is immediately rejected with 413 Payload Too Large")
  void testContentLengthExceeding2MbReturns413() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payments");
    request.setContentType("application/json");
    request.setContent(new byte[2 * 1024 * 1024 + 100]);

    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentAsString()).contains("payload_too_large");
    assertThat(response.getContentAsString())
        .contains("Request body exceeds the maximum permitted limit of 2 MB");
  }

  @Test
  @DisplayName("E5: Normal JSON request within 2 MB passes through filter cleanly")
  void testNormalJsonRequestPasses() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payments");
    request.setContentType("application/json");
    request.setContent("{\"amount\": 1000}".getBytes());

    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName(
      "E5: Multipart file uploads are exempt from the 2 MB JSON limit (handled by multipart 20 MB cap)")
  void testMultipartExemptFrom2MbLimit() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/files/upload");
    request.setContentType("multipart/form-data; boundary=----WebKitFormBoundary");
    request.addHeader("Content-Length", String.valueOf(5 * 1024 * 1024)); // 5 MB multipart

    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain filterChain = new MockFilterChain();

    filter.doFilter(request, response, filterChain);

    // Passed through without 413
    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  @DisplayName(
      "E5: Chunked stream without Content-Length header exceeding 2 MB throws PayCoreException 413 on stream read")
  void testChunkedPayloadExceedingLimitThrows413() throws Exception {
    byte[] oversized = new byte[2 * 1024 * 1024 + 10];
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", "/v1/payments") {
          @Override
          public long getContentLengthLong() {
            return -1L;
          }
        };
    request.setContentType("application/json");
    request.setContent(oversized);

    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain =
        (req, res) -> {
          ServletInputStream in = req.getInputStream();
          byte[] buf = new byte[8192];
          while (in.read(buf) != -1) {
            // reading stream
          }
        };

    assertThatThrownBy(() -> filter.doFilter(request, response, chain))
        .isInstanceOf(PayCoreException.class)
        .hasMessageContaining("Request body exceeds the maximum permitted limit of 2 MB");
  }
}
