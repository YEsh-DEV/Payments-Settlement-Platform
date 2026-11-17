package io.github.yesh.paycore.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.common.error.ProblemDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Token-bucket distributed rate limiter filter backed by Redis. Protects mutating write endpoints
 * (POST, PUT, DELETE, PATCH).
 *
 * <p>CRITICAL SECURITY GUARANTEE: Fails closed with HTTP 503 Service Unavailable if Redis is
 * unreachable or throws an exception.
 */
public class RedisRateLimiterFilter extends OncePerRequestFilter {

  private static final Logger log = LoggerFactory.getLogger(RedisRateLimiterFilter.class);
  private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");
  private static final Duration WINDOW_DURATION = Duration.ofMinutes(1);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

  private final StringRedisTemplate redisTemplate;
  private final int maxRequestsPerWindow;
  private final String keyPrefix;

  public RedisRateLimiterFilter(StringRedisTemplate redisTemplate) {
    this(redisTemplate, 120, "paycore:ratelimit:write:");
  }

  public RedisRateLimiterFilter(
      StringRedisTemplate redisTemplate, int maxRequestsPerWindow, String keyPrefix) {
    this.redisTemplate = redisTemplate;
    this.maxRequestsPerWindow = maxRequestsPerWindow;
    this.keyPrefix = keyPrefix;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    if (path.startsWith("/actuator")
        || path.startsWith("/swagger-ui")
        || path.startsWith("/v3/api-docs")
        || path.startsWith("/.well-known")
        || path.equals("/error")) {
      return true;
    }
    // Only filter mutating write operations (POST, PUT, DELETE, PATCH)
    // Read endpoints (GET, HEAD, OPTIONS) bypass rate limiting so a Redis outage never stops reads
    // or JWT verification
    return !WRITE_METHODS.contains(request.getMethod().toUpperCase());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {

    String clientIdentifier = extractClientIdentifier(request);
    long currentMinute = Instant.now().getEpochSecond() / 60;
    String redisKey = keyPrefix + clientIdentifier + ":" + currentMinute;

    try {
      Long currentCount = redisTemplate.opsForValue().increment(redisKey);
      if (currentCount != null && currentCount == 1) {
        redisTemplate.expire(redisKey, WINDOW_DURATION);
      }

      if (currentCount != null && currentCount > maxRequestsPerWindow) {
        log.warn(
            "Rate limit exceeded for client {} on {} {}. Count: {}, Max: {}",
            clientIdentifier,
            request.getMethod(),
            request.getRequestURI(),
            currentCount,
            maxRequestsPerWindow);

        writeProblemResponse(
            response,
            HttpStatus.TOO_MANY_REQUESTS,
            "rate_limit_exceeded",
            "Rate limit exceeded on mutating operations. Maximum allowed is "
                + maxRequestsPerWindow
                + " requests per minute.",
            60);
        return;
      }
    } catch (Exception ex) {
      log.error(
          "SECURITY FAIL-CLOSED: Redis rate limiter unavailable during mutating request {} {}: {}. Returning 503.",
          request.getMethod(),
          request.getRequestURI(),
          ex.getMessage());

      writeProblemResponse(
          response,
          HttpStatus.SERVICE_UNAVAILABLE,
          "rate_limiter_unavailable",
          "Security rate limiting service unavailable. Mutating requests fail-closed.",
          null);
      return;
    }

    filterChain.doFilter(request, response);
  }

  private String extractClientIdentifier(HttpServletRequest request) {
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
      return xForwardedFor.split(",")[0].trim();
    }
    String remoteAddr = request.getRemoteAddr();
    return remoteAddr != null ? remoteAddr : "unknown-client";
  }

  private void writeProblemResponse(
      HttpServletResponse response,
      HttpStatus status,
      String code,
      String detail,
      Integer retryAfterSeconds)
      throws IOException {

    String requestId = RequestIdFilter.getCurrentRequestId();
    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    if (retryAfterSeconds != null) {
      response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
    }

    ProblemDetails problem =
        ProblemDetails.builder()
            .type("https://api.paycore.dev/errors/" + code.replace('_', '-'))
            .title(status.getReasonPhrase())
            .status(status.value())
            .code(code)
            .detail(detail)
            .requestId(requestId)
            .timestamp(Instant.now())
            .build();

    OBJECT_MAPPER.writeValue(response.getOutputStream(), problem);
  }
}
