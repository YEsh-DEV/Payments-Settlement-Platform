package io.github.yesh.paycore.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RedisRateLimiterFilterTest {

  private StringRedisTemplate redisTemplate;
  private ValueOperations<String, String> valueOperations;
  private FilterChain filterChain;
  private RedisRateLimiterFilter filter;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    redisTemplate = mock(StringRedisTemplate.class);
    valueOperations = mock(ValueOperations.class);
    filterChain = mock(FilterChain.class);
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    // Limit to 2 requests for fast deterministic unit testing
    filter = new RedisRateLimiterFilter(redisTemplate, 2, "test:ratelimit:");
  }

  @Test
  @DisplayName("GET requests bypass rate limiting completely")
  void testGetBypassesFilter() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/accounts/123");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verifyNoInteractions(redisTemplate);
  }

  @Test
  @DisplayName("POST request within limit proceeds normally")
  void testPostWithinLimitProceeds() throws Exception {
    when(valueOperations.increment(anyString())).thenReturn(1L);

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payments");
    request.setRemoteAddr("192.168.1.10");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(200);
    verify(redisTemplate).expire(anyString(), any());
  }

  @Test
  @DisplayName("POST request exceeding limit returns HTTP 429 rate_limit_exceeded")
  void testPostExceedingLimitReturns429() throws Exception {
    when(valueOperations.increment(anyString())).thenReturn(3L);

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payments");
    request.setRemoteAddr("192.168.1.10");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    verify(filterChain, never()).doFilter(request, response);
    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getHeader("Retry-After")).isEqualTo("60");
    assertThat(response.getContentAsString()).contains("rate_limit_exceeded");
  }

  @Test
  @DisplayName(
      "CRITICAL REQUIREMENT: Redis failure fails closed with HTTP 503 rate_limiter_unavailable")
  void testRedisDownFailsClosedWith503() throws Exception {
    when(valueOperations.increment(anyString()))
        .thenThrow(new RedisConnectionFailureException("Connection refused to Redis port 6379"));

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/payments");
    request.setRemoteAddr("192.168.1.10");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    // Verified: request NEVER proceeds to controller
    verify(filterChain, never()).doFilter(request, response);
    // Verified: HTTP 503 Service Unavailable returned
    assertThat(response.getStatus()).isEqualTo(503);
    assertThat(response.getContentAsString()).contains("rate_limiter_unavailable");
    assertThat(response.getContentAsString())
        .contains("Security rate limiting service unavailable. Mutating requests fail-closed.");
  }

  @Test
  @DisplayName(
      "Playbook requirement: When Redis is down, reads, jwks and health endpoints continue working")
  void testReadsAndJwksWorkWhenRedisIsDown() throws Exception {
    when(valueOperations.increment(anyString()))
        .thenThrow(new RedisConnectionFailureException("Connection refused to Redis port 6379"));

    // 1. GET /.well-known/jwks.json
    MockHttpServletRequest jwksReq = new MockHttpServletRequest("GET", "/.well-known/jwks.json");
    MockHttpServletResponse jwksResp = new MockHttpServletResponse();
    filter.doFilter(jwksReq, jwksResp, filterChain);
    verify(filterChain).doFilter(jwksReq, jwksResp);

    // 2. GET /actuator/health
    MockHttpServletRequest healthReq = new MockHttpServletRequest("GET", "/actuator/health");
    MockHttpServletResponse healthResp = new MockHttpServletResponse();
    filter.doFilter(healthReq, healthResp, filterChain);
    verify(filterChain).doFilter(healthReq, healthResp);

    // 3. GET /v1/sellers/seller_101
    MockHttpServletRequest readReq = new MockHttpServletRequest("GET", "/v1/sellers/seller_101");
    MockHttpServletResponse readResp = new MockHttpServletResponse();
    filter.doFilter(readReq, readResp, filterChain);
    verify(filterChain).doFilter(readReq, readResp);

    // Redis is never invoked for read operations
    verify(valueOperations, never()).increment(anyString());
  }
}
