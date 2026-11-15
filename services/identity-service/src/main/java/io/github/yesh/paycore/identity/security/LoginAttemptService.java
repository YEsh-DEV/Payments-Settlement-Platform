package io.github.yesh.paycore.identity.security;

import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/** Brute-force login defense. Locks accounts for 15 minutes upon 5 consecutive failed attempts. */
@Service
public class LoginAttemptService {

  private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);
  private static final int MAX_ATTEMPTS = 5;
  private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
  private static final String ATTEMPTS_PREFIX = "paycore:login:attempts:";
  private static final String LOCK_PREFIX = "paycore:login:lock:";

  private final StringRedisTemplate redisTemplate;

  public LoginAttemptService(StringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
  }

  public boolean isLocked(String username) {
    try {
      Boolean locked = redisTemplate.hasKey(LOCK_PREFIX + username.toLowerCase());
      return Boolean.TRUE.equals(locked);
    } catch (Exception ex) {
      log.warn("Redis error checking login lock for user {}: {}", username, ex.getMessage());
      return false;
    }
  }

  public void recordFailure(String username) {
    String key = ATTEMPTS_PREFIX + username.toLowerCase();
    try {
      Long attempts = redisTemplate.opsForValue().increment(key);
      if (attempts != null && attempts == 1) {
        redisTemplate.expire(key, LOCK_DURATION);
      }
      if (attempts != null && attempts >= MAX_ATTEMPTS) {
        redisTemplate.opsForValue().set(LOCK_PREFIX + username.toLowerCase(), "1", LOCK_DURATION);
        redisTemplate.delete(key);
        log.warn(
            "User {} exceeded {} failed attempts. Locked for {} minutes.",
            username,
            MAX_ATTEMPTS,
            LOCK_DURATION.toMinutes());
      }
    } catch (Exception ex) {
      log.warn("Redis error recording login failure for {}: {}", username, ex.getMessage());
    }
  }

  public void recordSuccess(String username) {
    try {
      redisTemplate.delete(ATTEMPTS_PREFIX + username.toLowerCase());
      redisTemplate.delete(LOCK_PREFIX + username.toLowerCase());
    } catch (Exception ex) {
      log.warn("Redis error clearing login attempts for {}: {}", username, ex.getMessage());
    }
  }
}
