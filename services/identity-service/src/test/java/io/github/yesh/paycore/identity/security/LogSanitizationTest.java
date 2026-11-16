package io.github.yesh.paycore.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.github.yesh.paycore.identity.domain.User;
import io.github.yesh.paycore.identity.repository.AuditLogRepository;
import io.github.yesh.paycore.identity.repository.RefreshTokenRepository;
import io.github.yesh.paycore.identity.repository.ServiceClientRepository;
import io.github.yesh.paycore.identity.repository.UserRepository;
import io.github.yesh.paycore.identity.service.AuthService;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;

public class LogSanitizationTest {

  @Test
  @DisplayName("Log Sanitization: raw passwords and JWT tokens never appear in application logs")
  void testNoSensitiveDataInLogs() {
    Logger logger = (Logger) LoggerFactory.getLogger(AuthService.class);
    ListAppender<ILoggingEvent> listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);

    UserRepository userRepo = Mockito.mock(UserRepository.class);
    RefreshTokenRepository tokenRepo = Mockito.mock(RefreshTokenRepository.class);
    ServiceClientRepository clientRepo = Mockito.mock(ServiceClientRepository.class);
    AuditLogRepository auditRepo = Mockito.mock(AuditLogRepository.class);
    PasswordEncoder passwordEncoder = Mockito.mock(PasswordEncoder.class);
    JwtTokenService jwtTokenService = Mockito.mock(JwtTokenService.class);
    LoginAttemptService loginAttemptService = Mockito.mock(LoginAttemptService.class);
    TokenDenyListService tokenDenyListService = Mockito.mock(TokenDenyListService.class);

    User user = new User("alice", "hashed_secret_pw", Set.of("ADMIN"), null);
    user.setId(java.util.UUID.randomUUID());
    Mockito.when(userRepo.findByUsernameIgnoreCase("alice")).thenReturn(Optional.of(user));
    Mockito.when(passwordEncoder.matches("super_secret_raw_password", "hashed_secret_pw"))
        .thenReturn(true);
    Mockito.when(jwtTokenService.generateAccessToken(user))
        .thenReturn(
            new JwtTokenService.AccessTokenResult(
                "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.sensitive_payload",
                "jti_1",
                java.time.Instant.now().plusSeconds(3600)));
    Mockito.when(jwtTokenService.generateRawRefreshToken())
        .thenReturn("raw_refresh_token_256bit_secret");
    Mockito.when(jwtTokenService.hashToken("raw_refresh_token_256bit_secret"))
        .thenReturn("sha256_hash_value");

    AuthService authService =
        new AuthService(
            userRepo,
            tokenRepo,
            clientRepo,
            auditRepo,
            passwordEncoder,
            jwtTokenService,
            loginAttemptService,
            tokenDenyListService,
            604800L);

    authService.login("alice", "super_secret_raw_password");
    authService.logout("raw_refresh_token_256bit_secret", "jti_1", Duration.ofMinutes(10));

    for (ILoggingEvent event : listAppender.list) {
      String formattedMessage = event.getFormattedMessage();
      assertThat(formattedMessage).doesNotContain("super_secret_raw_password");
      assertThat(formattedMessage).doesNotContain("eyJhbGciOiJSUzI1Ni");
      assertThat(formattedMessage).doesNotContain("raw_refresh_token_256bit_secret");
    }
  }
}
