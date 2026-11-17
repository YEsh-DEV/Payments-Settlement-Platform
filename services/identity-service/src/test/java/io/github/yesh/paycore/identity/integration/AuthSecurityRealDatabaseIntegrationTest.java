package io.github.yesh.paycore.identity.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.identity.BaseIntegrationTest;
import io.github.yesh.paycore.identity.controller.AuthController;
import io.github.yesh.paycore.identity.domain.AuditLog;
import io.github.yesh.paycore.identity.domain.RefreshToken;
import io.github.yesh.paycore.identity.domain.User;
import io.github.yesh.paycore.identity.dto.LoginRequest;
import io.github.yesh.paycore.identity.repository.AuditLogRepository;
import io.github.yesh.paycore.identity.repository.RefreshTokenRepository;
import io.github.yesh.paycore.identity.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthSecurityRealDatabaseIntegrationTest extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private RefreshTokenRepository refreshTokenRepository;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private StringRedisTemplate redisTemplate;

  private static final String LOCKOUT_USER = "lockout_victim";
  private static final String LOCKOUT_PASS = "ValidPassword123!";

  private static final String REFRESH_USER = "refresh_user";
  private static final String REFRESH_PASS = "ValidPassword123!";

  @BeforeEach
  void cleanUp() {
    // Clean Redis keys
    redisTemplate.delete("paycore:login:attempts:" + LOCKOUT_USER);
    redisTemplate.delete("paycore:login:lock:" + LOCKOUT_USER);
    redisTemplate.delete("paycore:login:attempts:" + REFRESH_USER);
    redisTemplate.delete("paycore:login:lock:" + REFRESH_USER);

    // Clean user accounts
    userRepository.findByUsernameIgnoreCase(LOCKOUT_USER).ifPresent(userRepository::delete);
    userRepository.findByUsernameIgnoreCase(REFRESH_USER).ifPresent(userRepository::delete);

    userRepository.save(
        new User(LOCKOUT_USER, passwordEncoder.encode(LOCKOUT_PASS), Set.of("OPERATOR"), null));

    userRepository.save(
        new User(REFRESH_USER, passwordEncoder.encode(REFRESH_PASS), Set.of("OPERATOR"), null));
  }

  @Test
  @DisplayName(
      "E4: 5 consecutive failed logins lock account for 15 minutes with identical 401 response shape")
  void testLockoutAfterFiveFailedAttempts() throws Exception {
    LoginRequest badLogin = new LoginRequest(LOCKOUT_USER, "WrongPassword!");

    // 1. Perform 5 consecutive failed login attempts
    for (int attempt = 1; attempt <= 5; attempt++) {
      mockMvc
          .perform(
              post("/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(badLogin)))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("invalid_credentials"))
          .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    // 2. Check Redis: account must be locked
    String lockKey = "paycore:login:lock:" + LOCKOUT_USER;
    assertThat(redisTemplate.hasKey(lockKey)).isTrue();
    Long ttl = redisTemplate.getExpire(lockKey, TimeUnit.SECONDS);
    assertThat(ttl).isNotNull().isGreaterThan(0L).isLessThanOrEqualTo(900L); // 15 minutes

    // 3. 6th attempt with CORRECT password must still return 401 with IDENTICAL shape
    LoginRequest correctLogin = new LoginRequest(LOCKOUT_USER, LOCKOUT_PASS);
    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(correctLogin)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("invalid_credentials"))
        .andExpect(jsonPath("$.detail").value("Invalid username or password"));

    // 4. Verify PostgreSQL audit log contains LOGIN_LOCKED entry
    List<AuditLog> auditLogs = auditLogRepository.findByActorOrderByCreatedAtDesc(LOCKOUT_USER);
    boolean hasLoginLocked =
        auditLogs.stream().anyMatch(log -> "LOGIN_LOCKED".equals(log.getAction()));
    assertThat(hasLoginLocked).isTrue();
  }

  @Test
  @DisplayName("E4: Replaying a used refresh token revokes entire family and writes audit row")
  void testRefreshTokenReuseRevocationAndAudit() throws Exception {
    // 1. Login to get initial refresh token
    LoginRequest login = new LoginRequest(REFRESH_USER, REFRESH_PASS);
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(login)))
            .andExpect(status().isOk())
            .andReturn();

    Cookie token1Cookie = loginResult.getResponse().getCookie(AuthController.REFRESH_COOKIE_NAME);
    assertThat(token1Cookie).isNotNull();
    String rawToken1 = token1Cookie.getValue();

    // 2. Rotate token legitimately (1st refresh)
    MvcResult refresh1Result =
        mockMvc
            .perform(
                post("/auth/refresh")
                    .cookie(token1Cookie)
                    .header("X-Requested-With", "XMLHttpRequest"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();

    Cookie token2Cookie =
        refresh1Result.getResponse().getCookie(AuthController.REFRESH_COOKIE_NAME);
    assertThat(token2Cookie).isNotNull();
    String rawToken2 = token2Cookie.getValue();
    assertThat(rawToken2).isNotEqualTo(rawToken1);

    // 3. Replay attack: Re-use the already consumed token 1
    mockMvc
        .perform(
            post("/auth/refresh").cookie(token1Cookie).header("X-Requested-With", "XMLHttpRequest"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("token_compromised"))
        .andExpect(jsonPath("$.detail").value("Refresh token reuse detected. Family revoked."));

    // 4. Verify that descendant token 2 is now ALSO revoked and rejected
    mockMvc
        .perform(
            post("/auth/refresh").cookie(token2Cookie).header("X-Requested-With", "XMLHttpRequest"))
        .andExpect(status().isUnauthorized());

    // 5. Verify PostgreSQL DB state: all tokens in this family have revoked_at NOT NULL
    User user = userRepository.findByUsernameIgnoreCase(REFRESH_USER).orElseThrow();
    List<RefreshToken> tokens = refreshTokenRepository.findByUserId(user.getId());
    assertThat(tokens).isNotEmpty();
    UUID familyId = tokens.get(0).getFamilyId();
    for (RefreshToken t : tokens) {
      if (t.getFamilyId().equals(familyId)) {
        assertThat(t.getRevokedAt()).as("Token " + t.getId() + " must be revoked").isNotNull();
      }
    }

    // 6. Verify Audit Log entry for REFRESH_TOKEN_REUSE_DETECTED
    List<AuditLog> systemAudits = auditLogRepository.findByActorOrderByCreatedAtDesc("SYSTEM");
    boolean hasReuseAudit =
        systemAudits.stream()
            .anyMatch(
                log ->
                    "REFRESH_TOKEN_REUSE_DETECTED".equals(log.getAction())
                        && familyId.toString().equals(log.getEntityId()));
    assertThat(hasReuseAudit).as("Expected audit entry for replay detection").isTrue();
  }
}
