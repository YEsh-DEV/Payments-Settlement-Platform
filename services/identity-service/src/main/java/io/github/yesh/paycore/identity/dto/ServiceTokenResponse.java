package io.github.yesh.paycore.identity.dto;

public record ServiceTokenResponse(String accessToken, long expiresIn, String tokenType) {

  public ServiceTokenResponse(String accessToken, long expiresIn) {
    this(accessToken, expiresIn, "Bearer");
  }
}
