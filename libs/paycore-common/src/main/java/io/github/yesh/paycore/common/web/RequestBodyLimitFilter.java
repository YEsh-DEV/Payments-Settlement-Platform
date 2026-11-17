package io.github.yesh.paycore.common.web;

import io.github.yesh.paycore.common.error.PayCoreException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces maximum request body size (2 MB default) for non-multipart JSON payloads. Returns HTTP
 * 413 Payload Too Large with RFC 9457 Problem Details.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestBodyLimitFilter extends OncePerRequestFilter {

  public static final long MAX_NON_MULTIPART_BYTES = 2L * 1024 * 1024; // 2 MB

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain filterChain)
      throws ServletException, IOException {

    String contentType = request.getContentType();
    boolean isMultipart = contentType != null && contentType.toLowerCase().startsWith("multipart/");

    if (!isMultipart) {
      long contentLength = request.getContentLengthLong();
      if (contentLength > MAX_NON_MULTIPART_BYTES) {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String requestId = RequestIdFilter.getCurrentRequestId();
        String json =
            String.format(
                "{\"type\":\"https://api.paycore.dev/errors/payload-too-large\",\"title\":\"Payload Too Large\",\"status\":413,\"code\":\"payload_too_large\",\"detail\":\"Request body exceeds the maximum permitted limit of 2 MB\",\"requestId\":\"%s\"}",
                requestId != null ? requestId : "");
        response.getWriter().write(json);
        return;
      }

      // Wrap input stream to guard against unbuffered or chunked payloads exceeding 2 MB
      HttpServletRequest wrappedRequest =
          new HttpServletRequestWrapper(request) {
            private ServletInputStream stream;

            @Override
            public ServletInputStream getInputStream() throws IOException {
              if (stream == null) {
                stream =
                    new BoundedServletInputStream(
                        request.getInputStream(), MAX_NON_MULTIPART_BYTES);
              }
              return stream;
            }
          };

      filterChain.doFilter(wrappedRequest, response);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private static class BoundedServletInputStream extends ServletInputStream {
    private final ServletInputStream delegate;
    private final long maxBytes;
    private long bytesRead = 0;

    BoundedServletInputStream(ServletInputStream delegate, long maxBytes) {
      this.delegate = delegate;
      this.maxBytes = maxBytes;
    }

    @Override
    public boolean isFinished() {
      return delegate.isFinished();
    }

    @Override
    public boolean isReady() {
      return delegate.isReady();
    }

    @Override
    public void setReadListener(ReadListener readListener) {
      delegate.setReadListener(readListener);
    }

    @Override
    public int read() throws IOException {
      int b = delegate.read();
      if (b != -1) {
        bytesRead++;
        checkLimit();
      }
      return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      int read = delegate.read(b, off, len);
      if (read > 0) {
        bytesRead += read;
        checkLimit();
      }
      return read;
    }

    private void checkLimit() {
      if (bytesRead > maxBytes) {
        throw new PayCoreException(
            HttpStatus.PAYLOAD_TOO_LARGE,
            "payload_too_large",
            "Request body exceeds the maximum permitted limit of 2 MB");
      }
    }
  }
}
