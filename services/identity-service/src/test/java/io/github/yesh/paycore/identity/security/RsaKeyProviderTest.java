package io.github.yesh.paycore.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class RsaKeyProviderTest {

  @Test
  @DisplayName("Non-prod profile: dynamically generates ephemeral RSA key when no PEM is provided")
  void testDevProfileGeneratesEphemeralKey() {
    MockEnvironment devEnv = new MockEnvironment();
    devEnv.setActiveProfiles("dev");

    RsaKeyProvider provider = new RsaKeyProvider(devEnv, "test-key-dev", "", "");
    assertThat(provider.getCurrentKeyId()).isEqualTo("test-key-dev");
    assertThat(provider.getPrivateKey()).isNotNull();
    assertThat(provider.getPublicJwkSet().getKeys()).hasSize(1);
    assertThat(provider.getPublicJwkSet().getKeys().get(0).getKeyID()).isEqualTo("test-key-dev");
  }

  @Test
  @DisplayName("Prod profile: REFUSES startup when private key PEM is missing or blank")
  void testProdProfileRefusesStartupWithoutKey() {
    MockEnvironment prodEnv = new MockEnvironment();
    prodEnv.setActiveProfiles("prod");

    assertThatThrownBy(() -> new RsaKeyProvider(prodEnv, "test-key-prod", "", ""))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            "Production profile ('prod') requires a preconfigured RS256 private key");
  }

  @Test
  @DisplayName("Prod profile: starts successfully when valid RS256 PKCS8 PEM is provided")
  void testProdProfileLoadsConfiguredPem() throws Exception {
    KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
    kpg.initialize(2048);
    KeyPair kp = kpg.generateKeyPair();

    String pemPriv =
        "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(kp.getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----";

    String pemPub =
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getMimeEncoder(64, new byte[] {'\n'})
                .encodeToString(kp.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";

    MockEnvironment prodEnv = new MockEnvironment();
    prodEnv.setActiveProfiles("prod");

    RsaKeyProvider provider = new RsaKeyProvider(prodEnv, "prod-key-1", pemPriv, pemPub);
    assertThat(provider.getCurrentKeyId()).isEqualTo("prod-key-1");
    assertThat(provider.getPrivateKey().getModulus())
        .isEqualTo(((java.security.interfaces.RSAPrivateKey) kp.getPrivate()).getModulus());
    assertThat(provider.getPublicJwkSet().getKeys().get(0).getKeyID()).isEqualTo("prod-key-1");
  }
}
