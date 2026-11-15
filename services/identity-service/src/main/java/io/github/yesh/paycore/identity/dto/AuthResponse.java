package io.github.yesh.paycore.identity.dto;

import java.util.Set;

public record AuthResponse(
    String accessToken, long expiresIn, String tokenType, Set<String> roles, String sellerId) {

  public AuthResponse(String accessToken, long expiresIn, Set<String> roles, String sellerId) {
    this(accessToken, expiresIn, "Bearer", roles, sellerId);
  }
}
