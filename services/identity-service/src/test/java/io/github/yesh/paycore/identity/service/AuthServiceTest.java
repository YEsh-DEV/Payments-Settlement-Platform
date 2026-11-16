package io.github.yesh.paycore.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.identity.domain.RefreshToken;
import io.github.yesh.paycore.identity.domain.ServiceClient;
import io.github.yesh.paycore.identity.domain.User;
import io.github.yesh.paycore.identity.repository.AuditLogRepository;
import io.github.yesh.paycore.identity.repository.RefreshTokenRepository;
import io.github.yesh.paycore.identity.repository.ServiceClientRepository;
import io.github.yesh.paycore.identity.repository.UserRepository;
import io.github.yesh.paycore.identity.security.JwtTokenService;
import io.github.yesh.paycore.identity.security.LoginAttemptService;
import io.github.yesh.paycore.identity.security.RsaKeyProvider;
import io.github.yesh.paycore.identity.security.TokenDenyListService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private ServiceClientRepository serviceClientRepository;
  @Mock private AuditLogRepository auditLogRepository;
  @Mock private LoginAttemptService loginAttemptService;
  @Mock private TokenDenyListService tokenDenyListService;

  private PasswordEncoder passwordEncoder;
  private JwtTokenService jwtTokenService;
  private AuthService authService;

  @BeforeEach
  void setUp() {
    passwordEncoder = new BCryptPasswordEncoder(12);
    RsaKeyProvider rsaKeyProvider = new RsaKeyProvider("test-key");
    jwtTokenService = new JwtTokenService(rsaKeyProvider, "paycore-identity", "paycore", 900, 600);
    authService =
        new AuthService(
            userRepository,
            refreshTokenRepository,
            serviceClientRepository,
            auditLogRepository,
            passwordEncoder,
            jwtTokenService,
            loginAttemptService,
            tokenDenyListService,
            604800);
  }

  @Test
  @DisplayName(
      "Successful login returns access token, raw refresh token, and stores hashed token in DB")
  void testSuccessfulLogin() {
    String username = "operator";
    String password = "CorrectPassword123!";
    User user = new User(username, passwordEncoder.encode(password), Set.of("OPERATOR"), null);
    user.setId(UUID.randomUUID());

    when(loginAttemptService.isLocked(username)).thenReturn(false);
    when(userRepository.findByUsernameIgnoreCase(username)).thenReturn(Optional.of(user));

    AuthService.LoginResult result = authService.login(username, password);

    assertThat(result.accessToken()).isNotBlank();
    assertThat(result.rawRefreshToken()).isNotBlank();
    assertThat(result.user().getUsername()).isEqualTo(username);

    verify(loginAttemptService).recordSuccess(username);
    ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
    verify(refreshTokenRepository).save(captor.capture());

    RefreshToken savedToken = captor.getValue();
    assertThat(savedToken.getUserId()).isEqualTo(user.getId());
    assertThat(savedToken.getTokenHash())
        .isEqualTo(jwtTokenService.hashToken(result.rawRefreshToken()));
    verify(auditLogRepository).save(any());
  }

  @Test
  @DisplayName("Failed login records failure and throws UNAUTHORIZED")
  void testFailedLogin() {
    String username = "operator";
    User user = new User(username, passwordEncoder.encode("SecretPass!"), Set.of("OPERATOR"), null);
    user.setId(UUID.randomUUID());

    when(loginAttemptService.isLocked(username)).thenReturn(false);
    when(userRepository.findByUsernameIgnoreCase(username)).thenReturn(Optional.of(user));

    assertThatThrownBy(() -> authService.login(username, "WrongPassword"))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);

    verify(loginAttemptService).recordFailure(username);
    verify(refreshTokenRepository, never()).save(any());
  }

  @Test
  @DisplayName("Locked user is rejected without password check")
  void testLockedUserRejected() {
    String username = "target_user";
    when(loginAttemptService.isLocked(username)).thenReturn(true);

    assertThatThrownBy(() -> authService.login(username, "anyPassword"))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);

    verify(userRepository, never()).findByUsernameIgnoreCase(any());
  }

  @Test
  @DisplayName("Refresh token rotation successfully rotates token and marks previous as used")
  void testRotateRefreshTokenSuccess() {
    UUID userId = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    String oldRawToken = "old-raw-token-value-12345";
    String oldHash = jwtTokenService.hashToken(oldRawToken);

    RefreshToken existing =
        new RefreshToken(userId, familyId, oldHash, Instant.now().plusSeconds(3600));
    User user = new User("admin", "hash", Set.of("ADMIN"), null);
    user.setId(userId);

    when(refreshTokenRepository.findByTokenHash(oldHash)).thenReturn(Optional.of(existing));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    AuthService.LoginResult result = authService.rotateRefreshToken(oldRawToken);

    assertThat(result.accessToken()).isNotBlank();
    assertThat(result.rawRefreshToken()).isNotBlank().isNotEqualTo(oldRawToken);
    assertThat(existing.isUsed()).isTrue();

    verify(refreshTokenRepository, times(2)).save(any()); // 1 for old marked used, 1 for new token
  }

  @Test
  @DisplayName("Refresh token reuse detection: replaying already-used token revokes family")
  void testRefreshTokenReuseDetection() {
    UUID userId = UUID.randomUUID();
    UUID familyId = UUID.randomUUID();
    String replayedRawToken = "replayed-raw-token-12345";
    String tokenHash = jwtTokenService.hashToken(replayedRawToken);

    RefreshToken alreadyUsedToken =
        new RefreshToken(userId, familyId, tokenHash, Instant.now().plusSeconds(3600));
    alreadyUsedToken.setUsedAt(Instant.now().minusSeconds(60)); // Marked used earlier

    when(refreshTokenRepository.findByTokenHash(tokenHash))
        .thenReturn(Optional.of(alreadyUsedToken));

    assertThatThrownBy(() -> authService.rotateRefreshToken(replayedRawToken))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);

    verify(refreshTokenRepository).revokeFamily(eq(familyId), any(Instant.class));
    verify(auditLogRepository).save(any());
  }

  @Test
  @DisplayName("Logout revokes token family and adds access token JTI to Redis denylist")
  void testLogout() {
    String rawToken = "raw-refresh-logout-token";
    String tokenHash = jwtTokenService.hashToken(rawToken);
    UUID familyId = UUID.randomUUID();
    RefreshToken token =
        new RefreshToken(UUID.randomUUID(), familyId, tokenHash, Instant.now().plusSeconds(3600));

    when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(token));

    authService.logout(rawToken, "jwt_12345", Duration.ofMinutes(10));

    verify(refreshTokenRepository).revokeFamily(eq(familyId), any(Instant.class));
    verify(tokenDenyListService).revoke("jwt_12345", Duration.ofMinutes(10));
  }

  @Test
  @DisplayName("Service client authentication validates client_id and secret_hash")
  void testServiceClientAuth() {
    String clientId = "settlement-service";
    String secret = "settlement_dev_secret";
    ServiceClient client =
        new ServiceClient(clientId, passwordEncoder.encode(secret), Set.of("SERVICE"));

    when(serviceClientRepository.findByClientId(clientId)).thenReturn(Optional.of(client));

    JwtTokenService.AccessTokenResult result =
        authService.authenticateServiceClient(clientId, secret);
    assertThat(result.token()).isNotBlank();
    assertThat(result.jti()).startsWith("svc_");
    assertThat(Duration.between(Instant.now(), result.expiresAt()).toSeconds())
        .isBetween(590L, 610L); // ~10 minutes

    assertThatThrownBy(() -> authService.authenticateServiceClient(clientId, "wrongSecret"))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.UNAUTHORIZED);
  }
}
