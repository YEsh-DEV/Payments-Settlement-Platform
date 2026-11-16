package io.github.yesh.paycore.identity.service;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.common.web.RequestIdFilter;
import io.github.yesh.paycore.identity.domain.AuditLog;
import io.github.yesh.paycore.identity.domain.RefreshToken;
import io.github.yesh.paycore.identity.domain.ServiceClient;
import io.github.yesh.paycore.identity.domain.User;
import io.github.yesh.paycore.identity.repository.AuditLogRepository;
import io.github.yesh.paycore.identity.repository.RefreshTokenRepository;
import io.github.yesh.paycore.identity.repository.ServiceClientRepository;
import io.github.yesh.paycore.identity.repository.UserRepository;
import io.github.yesh.paycore.identity.security.JwtTokenService;
import io.github.yesh.paycore.identity.security.LoginAttemptService;
import io.github.yesh.paycore.identity.security.TokenDenyListService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final ServiceClientRepository serviceClientRepository;
  private final AuditLogRepository auditLogRepository;
  private final PasswordEncoder passwordEncoder;
  private final JwtTokenService jwtTokenService;
  private final LoginAttemptService loginAttemptService;
  private final TokenDenyListService tokenDenyListService;
  private final long refreshTokenValiditySeconds;

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      ServiceClientRepository serviceClientRepository,
      AuditLogRepository auditLogRepository,
      PasswordEncoder passwordEncoder,
      JwtTokenService jwtTokenService,
      LoginAttemptService loginAttemptService,
      TokenDenyListService tokenDenyListService,
      @Value("${paycore.security.jwt.refresh-token-validity-seconds:604800}")
          long refreshTokenValiditySeconds) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.serviceClientRepository = serviceClientRepository;
    this.auditLogRepository = auditLogRepository;
    this.passwordEncoder = passwordEncoder;
    this.jwtTokenService = jwtTokenService;
    this.loginAttemptService = loginAttemptService;
    this.tokenDenyListService = tokenDenyListService;
    this.refreshTokenValiditySeconds = refreshTokenValiditySeconds;
  }

  public record LoginResult(String accessToken, String rawRefreshToken, User user) {}

  @Transactional
  public LoginResult login(String username, String password) {
    if (loginAttemptService.isLocked(username)) {
      audit(
          username,
          "LOGIN_LOCKED",
          "USER",
          username,
          "Account temporarily locked due to consecutive failures");
      throw new PayCoreException(
          HttpStatus.UNAUTHORIZED, "invalid_credentials", "Invalid username or password");
    }

    Optional<User> userOpt = userRepository.findByUsernameIgnoreCase(username);
    if (userOpt.isEmpty()
        || !userOpt.get().isEnabled()
        || !passwordEncoder.matches(password, userOpt.get().getPasswordHash())) {
      loginAttemptService.recordFailure(username);
      audit(username, "LOGIN_FAILED", "USER", username, "Invalid credentials or account disabled");
      throw new PayCoreException(
          HttpStatus.UNAUTHORIZED, "invalid_credentials", "Invalid username or password");
    }

    User user = userOpt.get();
    loginAttemptService.recordSuccess(username);

    JwtTokenService.AccessTokenResult accessResult = jwtTokenService.generateAccessToken(user);
    String rawRefreshToken = jwtTokenService.generateRawRefreshToken();
    String tokenHash = jwtTokenService.hashToken(rawRefreshToken);

    UUID familyId = UUID.randomUUID();
    Instant expiresAt = Instant.now().plus(refreshTokenValiditySeconds, ChronoUnit.SECONDS);
    RefreshToken refreshToken = new RefreshToken(user.getId(), familyId, tokenHash, expiresAt);
    refreshTokenRepository.save(refreshToken);

    audit(user.getUsername(), "LOGIN_SUCCESS", "USER", user.getId().toString(), "Successful login");

    return new LoginResult(accessResult.token(), rawRefreshToken, user);
  }

  @Transactional
  public LoginResult rotateRefreshToken(String rawRefreshToken) {
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      throw new PayCoreException(
          HttpStatus.UNAUTHORIZED, "missing_refresh_token", "Refresh token cookie required");
    }

    String tokenHash = jwtTokenService.hashToken(rawRefreshToken);
    RefreshToken token =
        refreshTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.UNAUTHORIZED, "invalid_refresh_token", "Invalid refresh token"));

    // Reuse detection: If token was already used, a replay attack occurred!
    if (token.isUsed()) {
      refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now());
      audit(
          "SYSTEM",
          "REFRESH_TOKEN_REUSE_DETECTED",
          "TOKEN_FAMILY",
          token.getFamilyId().toString(),
          "Replay attack detected. Token family revoked.");
      throw new PayCoreException(
          HttpStatus.UNAUTHORIZED,
          "token_compromised",
          "Refresh token reuse detected. Family revoked.");
    }

    if (token.isRevoked() || token.isExpired()) {
      throw new PayCoreException(
          HttpStatus.UNAUTHORIZED, "expired_refresh_token", "Refresh token is expired or revoked");
    }

    // Mark current token used
    token.setUsedAt(Instant.now());
    refreshTokenRepository.save(token);

    User user =
        userRepository
            .findById(token.getUserId())
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.UNAUTHORIZED, "user_not_found", "User does not exist"));

    if (!user.isEnabled()) {
      throw new PayCoreException(HttpStatus.FORBIDDEN, "user_disabled", "User account is disabled");
    }

    // Issue new token in same family
    String newRawToken = jwtTokenService.generateRawRefreshToken();
    String newTokenHash = jwtTokenService.hashToken(newRawToken);
    Instant newExpiresAt = Instant.now().plus(refreshTokenValiditySeconds, ChronoUnit.SECONDS);

    RefreshToken newToken =
        new RefreshToken(user.getId(), token.getFamilyId(), newTokenHash, newExpiresAt);
    refreshTokenRepository.save(newToken);

    JwtTokenService.AccessTokenResult accessResult = jwtTokenService.generateAccessToken(user);
    return new LoginResult(accessResult.token(), newRawToken, user);
  }

  @Transactional
  public void logout(String rawRefreshToken, String jti, Duration remainingLifetime) {
    if (rawRefreshToken != null && !rawRefreshToken.isBlank()) {
      String tokenHash = jwtTokenService.hashToken(rawRefreshToken);
      refreshTokenRepository
          .findByTokenHash(tokenHash)
          .ifPresent(
              token -> {
                refreshTokenRepository.revokeFamily(token.getFamilyId(), Instant.now());
                audit(
                    "USER",
                    "LOGOUT",
                    "TOKEN_FAMILY",
                    token.getFamilyId().toString(),
                    "User logged out");
              });
    }
    if (jti != null && !jti.isBlank() && remainingLifetime != null) {
      tokenDenyListService.revoke(jti, remainingLifetime);
    }
  }

  @Transactional
  public JwtTokenService.AccessTokenResult authenticateServiceClient(
      String clientId, String clientSecret) {
    ServiceClient client =
        serviceClientRepository
            .findByClientId(clientId)
            .orElseThrow(
                () ->
                    new PayCoreException(
                        HttpStatus.UNAUTHORIZED, "invalid_client", "Invalid client credentials"));

    if (!client.isEnabled() || !passwordEncoder.matches(clientSecret, client.getSecretHash())) {
      audit(
          clientId,
          "SERVICE_AUTH_FAILED",
          "SERVICE_CLIENT",
          clientId,
          "Invalid client credentials");
      throw new PayCoreException(
          HttpStatus.UNAUTHORIZED, "invalid_client", "Invalid client credentials");
    }

    audit(clientId, "SERVICE_AUTH_SUCCESS", "SERVICE_CLIENT", clientId, "Machine token generated");
    return jwtTokenService.generateServiceToken(client.getClientId(), client.getRoles());
  }

  private void audit(String actor, String action, String entity, String entityId, String details) {
    String jsonDetails;
    if (details == null) {
      jsonDetails = "{}";
    } else if (details.trim().startsWith("{") || details.trim().startsWith("[")) {
      jsonDetails = details;
    } else {
      jsonDetails = "{\"message\":\"" + details.replace("\"", "\\\"") + "\"}";
    }
    AuditLog log =
        new AuditLog(
            actor, action, entity, entityId, jsonDetails, RequestIdFilter.getCurrentRequestId());
    auditLogRepository.save(log);
  }
}
