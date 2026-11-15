package io.github.yesh.paycore.identity.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.SignedJWT;
import io.github.yesh.paycore.identity.domain.User;
import java.security.interfaces.RSAPublicKey;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

  private RsaKeyProvider rsaKeyProvider;
  private JwtTokenService jwtTokenService;

  @BeforeEach
  void setUp() {
    rsaKeyProvider = new RsaKeyProvider("test-key-v1");
    jwtTokenService = new JwtTokenService(rsaKeyProvider, "paycore-identity", "paycore", 900, 600);
  }

  @Test
  @DisplayName("Should sign access token with RS256 and verify with public key from JWKS")
  void testSignAndVerifyAccessToken() throws Exception {
    User user = new User("admin", "hash", Set.of("ADMIN", "OPERATOR"), null);
    user.setId(UUID.randomUUID());

    JwtTokenService.AccessTokenResult result = jwtTokenService.generateAccessToken(user);

    assertThat(result.token()).isNotBlank();
    assertThat(result.jti()).startsWith("jwt_");

    SignedJWT signedJwt = SignedJWT.parse(result.token());
    assertThat(signedJwt.getHeader().getKeyID()).isEqualTo("test-key-v1");

    RSAPublicKey publicKey =
        (RSAPublicKey) rsaKeyProvider.getPublicJwkSet().getKeys().get(0).toRSAKey().toPublicKey();
    JWSVerifier verifier = new RSASSAVerifier(publicKey);
    assertThat(signedJwt.verify(verifier)).isTrue();

    assertThat(signedJwt.getJWTClaimsSet().getSubject()).isEqualTo(user.getId().toString());
    assertThat(signedJwt.getJWTClaimsSet().getIssuer()).isEqualTo("paycore-identity");
    assertThat(signedJwt.getJWTClaimsSet().getAudience()).contains("paycore");
    assertThat(signedJwt.getJWTClaimsSet().getStringListClaim("roles"))
        .containsExactlyInAnyOrder("ADMIN", "OPERATOR");
  }

  @Test
  @DisplayName("Should sign service token with client credentials claims")
  void testSignServiceToken() throws Exception {
    JwtTokenService.AccessTokenResult result =
        jwtTokenService.generateServiceToken("settlement-service", Set.of("SETTLEMENT_SERVICE"));

    assertThat(result.token()).isNotBlank();
    assertThat(result.jti()).startsWith("svc_");

    SignedJWT signedJwt = SignedJWT.parse(result.token());
    RSAPublicKey publicKey =
        (RSAPublicKey) rsaKeyProvider.getPublicJwkSet().getKeys().get(0).toRSAKey().toPublicKey();
    assertThat(signedJwt.verify(new RSASSAVerifier(publicKey))).isTrue();

    assertThat(signedJwt.getJWTClaimsSet().getSubject()).isEqualTo("settlement-service");
    assertThat(signedJwt.getJWTClaimsSet().getStringListClaim("roles"))
        .containsExactly("SETTLEMENT_SERVICE");
  }

  @Test
  @DisplayName("Token hash should be deterministic SHA-256 hex string")
  void testTokenHash() {
    String token = "sample-secret-refresh-token-12345";
    String hash1 = jwtTokenService.hashToken(token);
    String hash2 = jwtTokenService.hashToken(token);

    assertThat(hash1).isEqualTo(hash2);
    assertThat(hash1).hasSize(64);
  }
}
