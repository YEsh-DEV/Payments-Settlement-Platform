package io.github.yesh.paycore.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.identity.domain.User;
import io.github.yesh.paycore.identity.dto.CreateUserRequest;
import io.github.yesh.paycore.identity.dto.UpdateUserRequest;
import io.github.yesh.paycore.identity.dto.UserResponse;
import io.github.yesh.paycore.identity.repository.AuditLogRepository;
import io.github.yesh.paycore.identity.repository.UserRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private AuditLogRepository auditLogRepository;

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService =
        new UserService(userRepository, auditLogRepository, new BCryptPasswordEncoder(12));
  }

  @Test
  @DisplayName("Create user hashes password and persists roles")
  void testCreateUserSuccess() {
    CreateUserRequest request =
        new CreateUserRequest("testuser", "Password123!", Set.of("OPERATOR"), null);
    when(userRepository.findByUsernameIgnoreCase("testuser")).thenReturn(Optional.empty());
    when(userRepository.save(any(User.class)))
        .thenAnswer(
            inv -> {
              User u = inv.getArgument(0);
              u.setId(UUID.randomUUID());
              return u;
            });

    UserResponse response = userService.createUser(request, "admin_user");

    assertThat(response.username()).isEqualTo("testuser");
    assertThat(response.roles()).containsExactly("OPERATOR");
    verify(userRepository).save(any(User.class));
    verify(auditLogRepository).save(any());
  }

  @Test
  @DisplayName("Create duplicate username throws 409 CONFLICT")
  void testCreateDuplicateUsernameThrowsConflict() {
    CreateUserRequest request =
        new CreateUserRequest("existing", "Password123!", Set.of("OPERATOR"), null);
    when(userRepository.findByUsernameIgnoreCase("existing")).thenReturn(Optional.of(new User()));

    assertThatThrownBy(() -> userService.createUser(request, "admin_user"))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.CONFLICT);
  }

  @Test
  @DisplayName("Creating merchant user without sellerId throws 400 BAD REQUEST")
  void testMerchantWithoutSellerIdThrows() {
    CreateUserRequest request =
        new CreateUserRequest("merchant1", "Password123!", Set.of("MERCHANT"), null);
    when(userRepository.findByUsernameIgnoreCase("merchant1")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> userService.createUser(request, "admin_user"))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("Admin cannot disable their own account")
  void testAdminCannotDisableSelf() {
    UUID adminId = UUID.randomUUID();
    User admin = new User("admin", "hash", Set.of("ADMIN"), null);
    admin.setId(adminId);

    when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

    UpdateUserRequest request = new UpdateUserRequest(null, false);

    assertThatThrownBy(() -> userService.updateUser(adminId, request, adminId.toString()))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("Admin cannot remove ADMIN role from self")
  void testAdminCannotDemoteSelf() {
    UUID adminId = UUID.randomUUID();
    User admin = new User("admin", "hash", Set.of("ADMIN"), null);
    admin.setId(adminId);

    when(userRepository.findById(adminId)).thenReturn(Optional.of(admin));

    UpdateUserRequest request = new UpdateUserRequest(Set.of("VIEWER"), null);

    assertThatThrownBy(() -> userService.updateUser(adminId, request, adminId.toString()))
        .isInstanceOf(PayCoreException.class)
        .hasFieldOrPropertyWithValue("status", HttpStatus.BAD_REQUEST);
  }

  @Test
  @DisplayName("Admin can update other users' roles and status")
  void testAdminCanUpdateOtherUser() {
    UUID otherId = UUID.randomUUID();
    User other = new User("other", "hash", Set.of("VIEWER"), null);
    other.setId(otherId);

    when(userRepository.findById(otherId)).thenReturn(Optional.of(other));
    when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

    UpdateUserRequest request = new UpdateUserRequest(Set.of("OPERATOR"), false);
    UserResponse response = userService.updateUser(otherId, request, UUID.randomUUID().toString());

    assertThat(response.enabled()).isFalse();
    assertThat(response.roles()).containsExactly("OPERATOR");
    verify(auditLogRepository).save(any());
  }
}
