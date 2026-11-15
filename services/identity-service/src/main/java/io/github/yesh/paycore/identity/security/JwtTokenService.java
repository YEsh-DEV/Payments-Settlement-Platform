package io.github.yesh.paycore.identity.security;

import io.github.yesh.paycore.identity.domain.User;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtTokenService {

  private final RsaKeyProvider rsaKeyProvider;
  private final String issuer;
  private final String audience;
  private final long accessTokenValiditySeconds;
  private final long serviceTokenValiditySeconds;
  private final SecureRandom secureRandom = new SecureRandom();

  public JwtTokenService(
      RsaKeyProvider rsaKeyProvider,
      @Value("${paycore.security.jwt.issuer:paycore-identity}") String issuer,
      @Value("${paycore.security.jwt.audience:paycore}") String audience,
      @Value("${paycore.security.jwt.access-token-validity-seconds:900}")
          long accessTokenValiditySeconds,
      @Value("${paycore.security.jwt.service-token-validity-seconds:600}")
          long serviceTokenValiditySeconds) {
    this.rsaKeyProvider = rsaKeyProvider;
    this.issuer = issuer;
    this.audience = audience;
    this.accessTokenValiditySeconds = accessTokenValiditySeconds;
    this.serviceTokenValiditySeconds = serviceTokenValiditySeconds;
  }

  public record AccessTokenResult(String token, String jti, Instant expiresAt) {}

  public AccessTokenResult generateAccessToken(User user) {
    Instant now = Instant.now();
    Instant exp = now.plus(accessTokenValiditySeconds, ChronoUnit.SECONDS);
    String jti = "jwt_" + UUID.randomUUID().toString().replace("-", "");

    Map<String, Object> claims = new HashMap<>();
    claims.put("roles", user.getRoles());
    if (user.getSellerId() != null) {
      claims.put("sellerId", user.getSellerId());
    }

    String token =
        Jwts.builder()
            .header()
            .keyId(rsaKeyProvider.getCurrentKeyId())
            .and()
            .subject(user.getId().toString())
            .issuer(issuer)
            .audience()
            .add(audience)
            .and()
            .id(jti)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .claims(claims)
            .signWith(rsaKeyProvider.getPrivateKey(), Jwts.SIG.RS256)
            .compact();

    return new AccessTokenResult(token, jti, exp);
  }

  public AccessTokenResult generateServiceToken(String clientId, Set<String> roles) {
    Instant now = Instant.now();
    Instant exp = now.plus(serviceTokenValiditySeconds, ChronoUnit.SECONDS);
    String jti = "svc_" + UUID.randomUUID().toString().replace("-", "");

    Map<String, Object> claims = new HashMap<>();
    claims.put("roles", roles);

    String token =
        Jwts.builder()
            .header()
            .keyId(rsaKeyProvider.getCurrentKeyId())
            .and()
            .subject(clientId)
            .issuer(issuer)
            .audience()
            .add(audience)
            .and()
            .id(jti)
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp))
            .claims(claims)
            .signWith(rsaKeyProvider.getPrivateKey(), Jwts.SIG.RS256)
            .compact();

    return new AccessTokenResult(token, jti, exp);
  }

  public String generateRawRefreshToken() {
    byte[] bytes = new byte[32]; // 256 bits of entropy
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  public String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      StringBuilder hexString = new StringBuilder();
      for (byte b : hash) {
        String hex = Integer.toHexString(0xff & b);
        if (hex.length() == 1) hexString.append('0');
        hexString.append(hex);
      }
      return hexString.toString();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("SHA-256 unavailable", ex);
    }
  }
}
