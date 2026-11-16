package io.github.yesh.paycore.identity.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Manages RS256 key pairs and produces Nimbus JWKS public key sets. In 'prod' profile, refuses
 * startup unless a preconfigured RS256 private key is provided via
 * paycore.security.jwt.private-key-pem. Only non-prod profiles (dev, test, demo) may generate keys
 * in-memory.
 */
@Component
public class RsaKeyProvider {

  private static final Logger log = LoggerFactory.getLogger(RsaKeyProvider.class);

  private final String currentKeyId;
  private final RSAKey currentRsaKey;

  public RsaKeyProvider(String keyId) {
    this(null, keyId, null, null);
  }

  @Autowired
  public RsaKeyProvider(
      Environment environment,
      @Value("${paycore.security.jwt.key-id:key-2026-v1}") String keyId,
      @Value("${paycore.security.jwt.private-key-pem:}") String privateKeyPem,
      @Value("${paycore.security.jwt.public-key-pem:}") String publicKeyPem) {
    this.currentKeyId = keyId;

    boolean isProd =
        environment != null
            && environment.getActiveProfiles() != null
            && Arrays.stream(environment.getActiveProfiles())
                .anyMatch(p -> p.equalsIgnoreCase("prod") || p.equalsIgnoreCase("production"));

    if (privateKeyPem != null && !privateKeyPem.isBlank()) {
      log.info("Loading preconfigured RS256 key pair with ID: {}", keyId);
      this.currentRsaKey = loadRsaKeyFromPem(keyId, privateKeyPem, publicKeyPem);
    } else {
      if (isProd) {
        throw new IllegalStateException(
            "CRITICAL SECURITY CONFIGURATION ERROR: Production profile ('prod') requires a preconfigured "
                + "RS256 private key via 'paycore.security.jwt.private-key-pem'. Dynamic in-memory key generation is strictly forbidden in production.");
      }
      log.warn(
          "Active profile is non-prod. Generating dynamic ephemeral RS256 key pair with ID: {}",
          keyId);
      this.currentRsaKey = generateRsaKey(keyId);
    }
  }

  private RSAKey loadRsaKeyFromPem(String keyId, String privateKeyPem, String publicKeyPem) {
    try {
      String cleanPriv =
          privateKeyPem
              .replaceAll("-----BEGIN [A-Z ]+-----", "")
              .replaceAll("-----END [A-Z ]+-----", "")
              .replaceAll("\\s+", "");
      byte[] privBytes = Base64.getDecoder().decode(cleanPriv);
      PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privBytes);
      KeyFactory kf = KeyFactory.getInstance("RSA");
      RSAPrivateKey privateKey = (RSAPrivateKey) kf.generatePrivate(privSpec);

      RSAPublicKey publicKey;
      if (publicKeyPem != null && !publicKeyPem.isBlank()) {
        String cleanPub =
            publicKeyPem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s+", "");
        byte[] pubBytes = Base64.getDecoder().decode(cleanPub);
        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(pubBytes);
        publicKey = (RSAPublicKey) kf.generatePublic(pubSpec);
      } else if (privateKey instanceof RSAPrivateCrtKey crtKey) {
        RSAPublicKeySpec pubSpec =
            new RSAPublicKeySpec(crtKey.getModulus(), crtKey.getPublicExponent());
        publicKey = (RSAPublicKey) kf.generatePublic(pubSpec);
      } else {
        throw new IllegalArgumentException(
            "Public key PEM must be supplied if private key does not contain CRT parameters");
      }

      return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build();
    } catch (Exception ex) {
      throw new IllegalStateException(
          "Failed to parse provided RS256 PEM keys: " + ex.getMessage(), ex);
    }
  }

  private RSAKey generateRsaKey(String keyId) {
    try {
      KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
      generator.initialize(2048);
      KeyPair keyPair = generator.generateKeyPair();
      RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
      RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

      return new RSAKey.Builder(publicKey).privateKey(privateKey).keyID(keyId).build();
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException("RSA algorithm unavailable", ex);
    }
  }

  public RSAPrivateKey getPrivateKey() {
    try {
      return currentRsaKey.toRSAPrivateKey();
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to extract RSA private key", ex);
    }
  }

  public String getCurrentKeyId() {
    return currentKeyId;
  }

  public JWKSet getPublicJwkSet() {
    return new JWKSet(List.of(currentRsaKey.toPublicJWK()));
  }
}
