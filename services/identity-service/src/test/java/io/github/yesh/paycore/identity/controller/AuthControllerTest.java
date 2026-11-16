package io.github.yesh.paycore.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.identity.domain.User;
import io.github.yesh.paycore.identity.dto.AuthResponse;
import io.github.yesh.paycore.identity.dto.LoginRequest;
import io.github.yesh.paycore.identity.dto.ServiceTokenRequest;
import io.github.yesh.paycore.identity.dto.ServiceTokenResponse;
import io.github.yesh.paycore.identity.dto.UserResponse;
import io.github.yesh.paycore.identity.security.JwtTokenService;
import io.github.yesh.paycore.identity.security.RsaKeyProvider;
import io.github.yesh.paycore.identity.service.AuthService;
import io.github.yesh.paycore.identity.service.UserService;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

public class AuthControllerTest {

  private AuthService authService;
  private UserService userService;
  private RsaKeyProvider rsaKeyProvider;
  private AuthController controller;

  @BeforeEach
  void setUp() {
    authService = mock(AuthService.class);
    userService = mock(UserService.class);
    rsaKeyProvider = mock(RsaKeyProvider.class);
    controller = new AuthController(authService, userService, rsaKeyProvider, false, 604800L, 900L);
  }

  @Test
  @DisplayName("F-I-01: Successful login sets httpOnly cookie and returns access token")
  void testSuccessfulLogin() {
    User user = new User("alice", "encoded_pwd", Set.of("ADMIN"), null);
    when(authService.login("alice", "password123"))
        .thenReturn(new AuthService.LoginResult("jwt_access_123", "refresh_raw_456", user));

    MockHttpServletResponse response = new MockHttpServletResponse();
    ResponseEntity<AuthResponse> entity =
        controller.login(new LoginRequest("alice", "password123"), response);

    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(entity.getBody()).isNotNull();
    assertThat(entity.getBody().accessToken()).isEqualTo("jwt_access_123");
    assertThat(response.getHeader("Set-Cookie")).contains("paycore_refresh_token=refresh_raw_456");
  }

  @Test
  @DisplayName("F-I-01: Login with invalid credentials throws 401")
  void testFailedLoginThrows() {
    when(authService.login("alice", "wrong_password"))
        .thenThrow(
            new PayCoreException(
                HttpStatus.UNAUTHORIZED, "invalid_credentials", "Invalid username or password"));

    MockHttpServletResponse response = new MockHttpServletResponse();
    assertThatThrownBy(
            () -> controller.login(new LoginRequest("alice", "wrong_password"), response))
        .isInstanceOf(PayCoreException.class)
        .hasMessageContaining("Invalid username or password");
  }

  @Test
  @DisplayName(
      "F-I-02: Successful refresh returns fresh tokens when X-Requested-With header is present")
  void testSuccessfulRefresh() {
    User user = new User("bob", "encoded_pwd", Set.of("OPERATOR"), "seller_100");
    when(authService.rotateRefreshToken("valid_cookie_token"))
        .thenReturn(new AuthService.LoginResult("new_access_jwt", "new_refresh_token", user));

    MockHttpServletResponse response = new MockHttpServletResponse();
    ResponseEntity<AuthResponse> entity =
        controller.refresh("valid_cookie_token", "XMLHttpRequest", response);

    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(entity.getBody()).isNotNull();
    assertThat(entity.getBody().accessToken()).isEqualTo("new_access_jwt");
    assertThat(response.getHeader("Set-Cookie"))
        .contains("paycore_refresh_token=new_refresh_token")
        .contains("Path=/auth");
  }

  @Test
  @DisplayName("F-I-02: Refresh request without X-Requested-With header fails with 400")
  void testRefreshMissingCsrfHeaderThrows() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    assertThatThrownBy(() -> controller.refresh("valid_cookie_token", null, response))
        .isInstanceOf(PayCoreException.class)
        .hasMessageContaining("X-Requested-With header required");
  }

  @Test
  @DisplayName("F-I-03: Logout clears cookie and revokes session")
  void testLogoutClearsCookie() {
    MockHttpServletResponse response = new MockHttpServletResponse();
    ResponseEntity<Void> entity =
        controller.logout("cookie_token", "Bearer eyJhbGciOiJSUzI1NiJ9.dummy.sig", response);

    assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
    verify(authService).logout(eq("cookie_token"), any(), any());
  }

  @Test
  @DisplayName("F-I-04: Current user endpoint returns authenticated user details")
  void testCurrentUserSuccess() {
    UUID userId = UUID.randomUUID();
    UserResponse expected =
        new UserResponse(userId, "alice", Set.of("ADMIN"), null, true, Instant.now());
    when(userService.getUserByUsername("alice")).thenReturn(expected);

    Authentication auth = new UsernamePasswordAuthenticationToken("alice", "credentials");
    ResponseEntity<UserResponse> response = controller.me(auth);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(expected);
  }

  @Test
  @DisplayName("F-I-04: Current user endpoint fails if unauthenticated")
  void testCurrentUserUnauthenticatedThrows() {
    assertThatThrownBy(() -> controller.me(null))
        .isInstanceOf(PayCoreException.class)
        .hasMessageContaining("Authentication required");
  }

  @Test
  @DisplayName("F-I-05: JWKS endpoint returns active and retired key set")
  void testJwksEndpoint() {
    when(rsaKeyProvider.getPublicJwkSet()).thenReturn(new com.nimbusds.jose.jwk.JWKSet());

    ResponseEntity<Map<String, Object>> response = controller.getJwks();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
  }

  @Test
  @DisplayName("F-I-06: Service token issued for valid client credentials")
  void testServiceTokenIssued() {
    JwtTokenService.AccessTokenResult tokenResult =
        new JwtTokenService.AccessTokenResult(
            "svc_token_jwt", "svc_123", Instant.now().plusSeconds(3600));
    when(authService.authenticateServiceClient("settlement-service", "secret_123"))
        .thenReturn(tokenResult);

    ResponseEntity<ServiceTokenResponse> response =
        controller.serviceToken(new ServiceTokenRequest("settlement-service", "secret_123"));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().accessToken()).isEqualTo("svc_token_jwt");
  }

  @Test
  @DisplayName("F-I-06: Service token rejected for invalid client secret")
  void testServiceTokenRejected() {
    when(authService.authenticateServiceClient("settlement-service", "bad_secret"))
        .thenThrow(
            new PayCoreException(
                HttpStatus.UNAUTHORIZED, "invalid_credentials", "Invalid client secret"));

    assertThatThrownBy(
            () ->
                controller.serviceToken(
                    new ServiceTokenRequest("settlement-service", "bad_secret")))
        .isInstanceOf(PayCoreException.class);
  }
}
