package io.github.yesh.paycore.identity.controller;

import io.github.yesh.paycore.identity.dto.CreateUserRequest;
import io.github.yesh.paycore.identity.dto.UpdateUserRequest;
import io.github.yesh.paycore.identity.dto.UserResponse;
import io.github.yesh.paycore.identity.service.UserService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/admin/users", "/v1/users"})
@PreAuthorize("hasRole('ADMIN')")
public class UserAdminController {

  private final UserService userService;

  public UserAdminController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping
  public ResponseEntity<List<UserResponse>> listUsers() {
    return ResponseEntity.ok(userService.listUsers());
  }

  @GetMapping("/{id}")
  public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
    return ResponseEntity.ok(userService.getUser(id));
  }

  @PostMapping
  public ResponseEntity<UserResponse> createUser(
      @Valid @RequestBody CreateUserRequest request, Authentication authentication) {
    String actor = authentication != null ? authentication.getName() : "ADMIN";
    UserResponse created = userService.createUser(request, actor);
    return ResponseEntity.status(HttpStatus.CREATED).body(created);
  }

  @PutMapping("/{id}")
  public ResponseEntity<UserResponse> updateUser(
      @PathVariable UUID id,
      @RequestBody UpdateUserRequest request,
      Authentication authentication) {
    String actor = authentication != null ? authentication.getName() : "ADMIN";
    UserResponse updated = userService.updateUser(id, request, actor);
    return ResponseEntity.ok(updated);
  }
}
