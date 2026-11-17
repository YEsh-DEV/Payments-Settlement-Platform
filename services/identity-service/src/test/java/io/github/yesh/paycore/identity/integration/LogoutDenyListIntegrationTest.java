package io.github.yesh.paycore.identity.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import io.github.yesh.paycore.identity.BaseIntegrationTest;
import io.github.yesh.paycore.identity.controller.AuthController;
import io.github.yesh.paycore.identity.domain.User;
import io.github.yesh.paycore.identity.dto.LoginRequest;
import io.github.yesh.paycore.identity.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
public class LogoutDenyListIntegrationTest extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private StringRedisTemplate redisTemplate;

  private static final String TEST_USER = "logout_test_user";
  private static final String TEST_PASS = "ValidPassword123!";

  @BeforeEach
  void setUpUser() {
    userRepository.findByUsernameIgnoreCase(TEST_USER).ifPresent(u -> userRepository.delete(u));

    User user = new User(TEST_USER, passwordEncoder.encode(TEST_PASS), Set.of("OPERATOR"), null);
    userRepository.save(user);
  }

  @Test
  @DisplayName(
      "E1: Logout writes access-token jti to Redis with positive TTL and subsequent request is rejected with 401")
  void testLogoutDenyListLifecycle() throws Exception {
    // 1. Authenticate via POST /auth/login
    LoginRequest loginRequest = new LoginRequest(TEST_USER, TEST_PASS);
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();

    String loginResponseJson = loginResult.getResponse().getContentAsString();
    JsonNode jsonNode = objectMapper.readTree(loginResponseJson);
    String accessToken = jsonNode.get("accessToken").asText();

    Cookie refreshCookie = loginResult.getResponse().getCookie(AuthController.REFRESH_COOKIE_NAME);
    assertThat(refreshCookie).isNotNull();

    // 2. Parse token and extract jti
    SignedJWT signedJWT = SignedJWT.parse(accessToken);
    String jti = signedJWT.getJWTClaimsSet().getJWTID();
    assertThat(jti).isNotNull().isNotBlank();

    String redisKey = "paycore:denylist:" + jti;
    assertThat(redisTemplate.hasKey(redisKey)).isFalse();

    // 3. Request protected endpoint GET /auth/me before logout -> 200 OK
    mockMvc
        .perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value(TEST_USER));

    // 4. Call POST /auth/logout
    mockMvc
        .perform(
            post("/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .cookie(refreshCookie))
        .andExpect(status().isNoContent());

    // 5. Verify Redis deny-list entry and TTL
    Boolean keyExists = redisTemplate.hasKey(redisKey);
    assertThat(keyExists).isTrue();

    String redisValue = redisTemplate.opsForValue().get(redisKey);
    assertThat(redisValue).isEqualTo("1");

    Long ttl = redisTemplate.getExpire(redisKey, TimeUnit.SECONDS);
    assertThat(ttl).isNotNull();
    assertThat(ttl).isGreaterThan(0L);
    assertThat(ttl).isLessThanOrEqualTo(900L); // Max access token lifetime is 900s (15 min)

    // 6. Request protected endpoint with the revoked token -> 401 Unauthorized
    mockMvc
        .perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
        .andExpect(status().isUnauthorized());
  }
}
