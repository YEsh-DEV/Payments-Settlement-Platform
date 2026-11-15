package io.github.yesh.paycore.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ensures every incoming HTTP request carries a traceable correlation ID in the MDC and response
 * header.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

  public static final String HEADER_NAME = "X-Request-Id";
  public static final String MDC_KEY = "requestId";

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String requestId = request.getHeader(HEADER_NAME);
    if (requestId == null || requestId.isBlank()) {
      requestId = "req_" + UUID.randomUUID().toString().replace("-", "");
    } else {
      requestId = requestId.trim();
      if (requestId.length() > 64) {
        requestId = requestId.substring(0, 64);
      }
    }

    MDC.put(MDC_KEY, requestId);
    response.setHeader(HEADER_NAME, requestId);

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_KEY);
    }
  }

  public static String getCurrentRequestId() {
    String id = MDC.get(MDC_KEY);
    return id != null ? id : "system";
  }
}
