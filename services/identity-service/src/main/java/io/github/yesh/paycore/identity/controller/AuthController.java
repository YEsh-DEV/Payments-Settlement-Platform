package io.github.yesh.paycore.identity.controller;

import com.nimbusds.jwt.SignedJWT;
import io.github.yesh.paycore.identity.dto.*;
import io.github.yesh.paycore.identity.security.JwtTokenService;
import io.github.yesh.paycore.identity.security.RsaKeyProvider;
import io.github.yesh.paycore.identity.service.AuthService;
import io.github.yesh.paycore.identity.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(AuthController.class);

  public static final String REFRESH_COOKIE_NAME = "paycore_refresh_token";

  private final AuthService authService;
  private final UserService userService;
  private final RsaKeyProvider rsaKeyProvider;
  private final boolean cookieSecure;
  private final long refreshTokenValiditySeconds;
  private final long accessTokenValiditySeconds;

  public AuthController(
      AuthService authService,
      UserService userService,
      RsaKeyProvider rsaKeyProvider,
      @Value("${paycore.security.cookie.secure:false}") boolean cookieSecure,
      @Value("${paycore.security.jwt.refresh-token-validity-seconds:604800}")
          long refreshTokenValiditySeconds,
      @Value("${paycore.security.jwt.access-token-validity-seconds:900}")
          long accessTokenValiditySeconds) {
    this.authService = authService;
    this.userService = userService;
    this.rsaKeyProvider = rsaKeyProvider;
    this.cookieSecure = cookieSecure;
    this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
    this.accessTokenValiditySeconds = accessTokenValiditySeconds;
  }

  @PostMapping("/auth/login")
  public ResponseEntity<AuthResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletResponse response) {
    AuthService.LoginResult result = authService.login(request.username(), request.password());
    setRefreshCookie(response, result.rawRefreshToken(), refreshTokenValiditySeconds);

    AuthResponse body =
        new AuthResponse(
            result.accessToken(),
            accessTokenValiditySeconds,
            result.user().getRoles(),
            result.user().getSellerId());
    return ResponseEntity.ok(body);
  }

  @PostMapping("/auth/refresh")
  public ResponseEntity<AuthResponse> refresh(
      @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String rawRefreshToken,
      @RequestHeader(name = "X-Requested-With", required = false) String xRequestedWith,
      HttpServletResponse response) {
    if (xRequestedWith == null || xRequestedWith.isBlank()) {
      throw new io.github.yesh.paycore.common.error.PayCoreException(
          org.springframework.http.HttpStatus.BAD_REQUEST,
          "csrf_protection_header_missing",
          "X-Requested-With header required for refresh token rotation");
    }
    AuthService.LoginResult result = authService.rotateRefreshToken(rawRefreshToken);
    setRefreshCookie(response, result.rawRefreshToken(), refreshTokenValiditySeconds);

    AuthResponse body =
        new AuthResponse(
            result.accessToken(),
            accessTokenValiditySeconds,
            result.user().getRoles(),
            result.user().getSellerId());
    return ResponseEntity.ok(body);
  }

  @PostMapping("/auth/logout")
  public ResponseEntity<Void> logout(
      @CookieValue(name = REFRESH_COOKIE_NAME, required = false) String rawRefreshToken,
      @RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authHeader,
      HttpServletResponse response) {
    String jti = null;
    Duration remaining = null;

    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      try {
        SignedJWT jwt = SignedJWT.parse(authHeader.substring(7));
        jti = jwt.getJWTClaimsSet().getJWTID();
        Date exp = jwt.getJWTClaimsSet().getExpirationTime();
        if (exp != null) {
          remaining = Duration.between(Instant.now(), exp.toInstant());
        }
      } catch (java.text.ParseException e) {
        log.debug("Failed to parse Bearer token during logout: {}", e.getMessage());
      }
    }

    authService.logout(rawRefreshToken, jti, remaining);
    clearRefreshCookie(response);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/auth/service-token")
  public ResponseEntity<ServiceTokenResponse> serviceToken(
      @Valid @RequestBody ServiceTokenRequest request) {
    JwtTokenService.AccessTokenResult result =
        authService.authenticateServiceClient(request.clientId(), request.clientSecret());
    return ResponseEntity.ok(
        new ServiceTokenResponse(
            result.token(), Duration.between(Instant.now(), result.expiresAt()).toSeconds()));
  }

  @GetMapping("/auth/me")
  public ResponseEntity<UserResponse> me(
      org.springframework.security.core.Authentication authentication) {
    if (authentication == null || authentication.getName() == null) {
      throw new io.github.yesh.paycore.common.error.PayCoreException(
          org.springframework.http.HttpStatus.UNAUTHORIZED,
          "unauthorized",
          "Authentication required");
    }
    String identifier = authentication.getName();
    try {
      java.util.UUID userId = java.util.UUID.fromString(identifier);
      return ResponseEntity.ok(userService.getUser(userId));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.ok(userService.getUserByUsername(identifier));
    }
  }

  @GetMapping("/.well-known/jwks.json")
  public ResponseEntity<Map<String, Object>> getJwks() {
    return ResponseEntity.ok(rsaKeyProvider.getPublicJwkSet().toJSONObject());
  }

  private void setRefreshCookie(HttpServletResponse response, String token, long maxAgeSeconds) {
    ResponseCookie cookie =
        ResponseCookie.from(REFRESH_COOKIE_NAME, token)
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/auth")
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
            .sameSite("Strict")
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }

  private void clearRefreshCookie(HttpServletResponse response) {
    ResponseCookie cookie =
        ResponseCookie.from(REFRESH_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(cookieSecure)
            .path("/auth")
            .maxAge(Duration.ZERO)
            .sameSite("Strict")
            .build();
    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
  }
}
