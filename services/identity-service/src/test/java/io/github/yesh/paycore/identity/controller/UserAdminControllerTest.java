package io.github.yesh.paycore.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.identity.dto.CreateUserRequest;
import io.github.yesh.paycore.identity.dto.UpdateUserRequest;
import io.github.yesh.paycore.identity.dto.UserResponse;
import io.github.yesh.paycore.identity.service.UserService;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

public class UserAdminControllerTest {

  private UserService userService;
  private UserAdminController controller;

  @BeforeEach
  void setUp() {
    userService = mock(UserService.class);
    controller = new UserAdminController(userService);
  }

  @Test
  @DisplayName("F-I-07: Admin creates new user successfully")
  void testCreateUserSuccess() {
    CreateUserRequest request =
        new CreateUserRequest("charlie", "P@ssword123", Set.of("OPERATOR"), null);
    UserResponse created =
        new UserResponse(
            UUID.randomUUID(), "charlie", Set.of("OPERATOR"), null, true, Instant.now());
    Authentication auth = new UsernamePasswordAuthenticationToken("admin_alice", null);

    when(userService.createUser(request, "admin_alice")).thenReturn(created);

    ResponseEntity<UserResponse> response = controller.createUser(request, auth);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(response.getBody()).isEqualTo(created);
  }

  @Test
  @DisplayName("F-I-07: Admin creation rejected on duplicate username")
  void testCreateUserDuplicateFails() {
    CreateUserRequest request =
        new CreateUserRequest("existing_user", "P@ssword123", Set.of("OPERATOR"), null);
    Authentication auth = new UsernamePasswordAuthenticationToken("admin_alice", null);

    when(userService.createUser(request, "admin_alice"))
        .thenThrow(
            new PayCoreException(
                HttpStatus.CONFLICT, "username_exists", "Username is already taken"));

    assertThatThrownBy(() -> controller.createUser(request, auth))
        .isInstanceOf(PayCoreException.class)
        .hasMessageContaining("Username is already taken");
  }

  @Test
  @DisplayName("F-I-07: Admin lists users")
  void testListUsers() {
    UserResponse u1 =
        new UserResponse(UUID.randomUUID(), "alice", Set.of("ADMIN"), null, true, Instant.now());
    when(userService.listUsers()).thenReturn(List.of(u1));

    ResponseEntity<List<UserResponse>> response = controller.listUsers();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsExactly(u1);
  }

  @Test
  @DisplayName("F-I-07: Admin self-disable rejected")
  void testSelfDisableFails() {
    UUID adminId = UUID.randomUUID();
    UpdateUserRequest request = new UpdateUserRequest(null, false);
    Authentication auth = new UsernamePasswordAuthenticationToken("admin_alice", null);

    when(userService.updateUser(adminId, request, "admin_alice"))
        .thenThrow(
            new PayCoreException(
                HttpStatus.BAD_REQUEST,
                "cannot_disable_self",
                "Admin cannot disable their own account"));

    assertThatThrownBy(() -> controller.updateUser(adminId, request, auth))
        .isInstanceOf(PayCoreException.class)
        .hasMessageContaining("cannot disable their own account");
  }
}
