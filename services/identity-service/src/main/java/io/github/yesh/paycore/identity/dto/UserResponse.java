package io.github.yesh.paycore.identity.dto;

import io.github.yesh.paycore.identity.domain.User;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String username,
    Set<String> roles,
    String sellerId,
    boolean enabled,
    Instant createdAt) {

  public static UserResponse from(User user) {
    return new UserResponse(
        user.getId(),
        user.getUsername(),
        user.getRoles(),
        user.getSellerId(),
        user.isEnabled(),
        user.getCreatedAt());
  }
}
