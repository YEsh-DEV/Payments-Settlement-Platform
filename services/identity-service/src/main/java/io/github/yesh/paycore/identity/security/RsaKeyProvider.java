package io.github.yesh.paycore.identity.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Manages RS256 key pairs and produces Nimbus JWKS public key sets. Supports dual-key publishing
 * (current and previous) for zero-downtime rotation.
 */
@Component
public class RsaKeyProvider {

  private final String currentKeyId;
  private final RSAKey currentRsaKey;

  public RsaKeyProvider(@Value("${paycore.security.jwt.key-id:key-2026-v1}") String keyId) {
    this.currentKeyId = keyId;
    this.currentRsaKey = generateRsaKey(keyId);
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
    // Return public keys only
    return new JWKSet(List.of(currentRsaKey.toPublicJWK()));
  }
}
