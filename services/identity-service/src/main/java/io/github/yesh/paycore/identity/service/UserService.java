package io.github.yesh.paycore.identity.service;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.common.web.RequestIdFilter;
import io.github.yesh.paycore.identity.domain.AuditLog;
import io.github.yesh.paycore.identity.domain.User;
import io.github.yesh.paycore.identity.dto.CreateUserRequest;
import io.github.yesh.paycore.identity.dto.UpdateUserRequest;
import io.github.yesh.paycore.identity.dto.UserResponse;
import io.github.yesh.paycore.identity.repository.AuditLogRepository;
import io.github.yesh.paycore.identity.repository.UserRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository userRepository;
  private final AuditLogRepository auditLogRepository;
  private final PasswordEncoder passwordEncoder;

  public UserService(
      UserRepository userRepository,
      AuditLogRepository auditLogRepository,
      PasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.auditLogRepository = auditLogRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional(readOnly = true)
  public List<UserResponse> listUsers() {
    return userRepository.findAll().stream().map(UserResponse::from).collect(Collectors.toList());
  }

  @Transactional(readOnly = true)
  public UserResponse getUser(UUID id) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new PayCoreException(HttpStatus.NOT_FOUND, "user_not_found", "User not found"));
    return UserResponse.from(user);
  }

  @Transactional
  public UserResponse createUser(CreateUserRequest request, String currentActor) {
    if (userRepository.findByUsernameIgnoreCase(request.username()).isPresent()) {
      throw new PayCoreException(
          HttpStatus.CONFLICT, "username_exists", "Username is already taken");
    }

    Set<String> roles =
        request.roles() == null || request.roles().isEmpty() ? Set.of("VIEWER") : request.roles();

    if (roles.contains("MERCHANT")
        && (request.sellerId() == null || request.sellerId().isBlank())) {
      throw new PayCoreException(
          HttpStatus.BAD_REQUEST,
          "seller_id_required",
          "Merchant users must be linked to a sellerId");
    }

    String passwordHash = passwordEncoder.encode(request.password());
    User user = new User(request.username(), passwordHash, roles, request.sellerId());
    User saved = userRepository.save(user);

    audit(
        currentActor,
        "USER_CREATED",
        "USER",
        saved.getId().toString(),
        "Created user " + saved.getUsername() + " with roles " + roles);

    return UserResponse.from(saved);
  }

  @Transactional
  public UserResponse updateUser(UUID id, UpdateUserRequest request, String currentActor) {
    User user =
        userRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new PayCoreException(HttpStatus.NOT_FOUND, "user_not_found", "User not found"));

    boolean isSelf = currentActor != null && currentActor.equalsIgnoreCase(user.getId().toString());

    if (isSelf) {
      if (request.enabled() != null && !request.enabled()) {
        throw new PayCoreException(
            HttpStatus.BAD_REQUEST,
            "cannot_modify_self",
            "Admins cannot disable their own account");
      }
      if (request.roles() != null && !request.roles().contains("ADMIN")) {
        throw new PayCoreException(
            HttpStatus.BAD_REQUEST,
            "cannot_modify_self",
            "Admins cannot remove the ADMIN role from themselves");
      }
    }

    if (request.enabled() != null) {
      user.setEnabled(request.enabled());
    }

    if (request.roles() != null && !request.roles().isEmpty()) {
      if (request.roles().contains("MERCHANT") && user.getSellerId() == null) {
        throw new PayCoreException(
            HttpStatus.BAD_REQUEST,
            "seller_id_required",
            "Merchant users must be linked to a sellerId");
      }
      user.setRoles(request.roles());
    }

    User updated = userRepository.save(user);
    audit(
        currentActor,
        "USER_UPDATED",
        "USER",
        updated.getId().toString(),
        "Updated user "
            + updated.getUsername()
            + " roles="
            + updated.getRoles()
            + " enabled="
            + updated.isEnabled());

    return UserResponse.from(updated);
  }

  private void audit(String actor, String action, String entity, String entityId, String details) {
    AuditLog log =
        new AuditLog(
            actor != null ? actor : "SYSTEM",
            action,
            entity,
            entityId,
            details,
            RequestIdFilter.getCurrentRequestId());
    auditLogRepository.save(log);
  }
}
