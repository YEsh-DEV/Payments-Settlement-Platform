package io.github.yesh.paycore.common.web;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.servlet.ServletException;
import java.io.IOException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @Test
  @DisplayName("Should generate request id if missing in request header")
  void testGeneratesRequestId() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          String current = RequestIdFilter.getCurrentRequestId();
          assertNotNull(current);
          assertTrue(current.startsWith("req_"));
        });

    String headerValue = response.getHeader(RequestIdFilter.HEADER_NAME);
    assertNotNull(headerValue);
    assertTrue(headerValue.startsWith("req_"));
  }

  @Test
  @DisplayName("Should preserve existing request id from request header")
  void testPreservesRequestId() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(RequestIdFilter.HEADER_NAME, "test-client-id-12345");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(
        request,
        response,
        (req, res) -> {
          assertEquals("test-client-id-12345", RequestIdFilter.getCurrentRequestId());
        });

    assertEquals("test-client-id-12345", response.getHeader(RequestIdFilter.HEADER_NAME));
  }
}
