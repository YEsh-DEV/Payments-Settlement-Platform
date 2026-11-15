package io.github.yesh.paycore.identity.security;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Distributed token revocation deny-list backed by Redis. Enforces ADR-002: Fail-open fallback if
 * Redis is unavailable.
 */
@Service
public class TokenDenyListService {

  private static final Logger log = LoggerFactory.getLogger(TokenDenyListService.class);
  private static final String KEY_PREFIX = "paycore:denylist:";

  private final StringRedisTemplate redisTemplate;

  public TokenDenyListService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public void revoke(String jti, Duration remainingLifetime) {
    if (jti == null || jti.isBlank() || remainingLifetime.isNegative()) {
      return;
    }
    try {
      redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", remainingLifetime);
      log.info("Token jti={} added to Redis deny-list for {}", jti, remainingLifetime);
    } catch (Exception ex) {
      log.warn("Failed to write token revocation to Redis for jti={}: {}", jti, ex.getMessage());
    }
  }

  public boolean isRevoked(String jti) {
    if (jti == null || jti.isBlank()) {
      return false;
    }
    try {
      Boolean hasKey = redisTemplate.hasKey(KEY_PREFIX + jti);
      return Boolean.TRUE.equals(hasKey);
    } catch (Exception ex) {
      log.warn(
          "Redis unavailable during deny-list check for jti={}. Failing open per ADR-002.", jti);
      return false;
    }
  }
}
