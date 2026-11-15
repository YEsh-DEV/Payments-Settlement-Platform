package io.github.yesh.paycore.identity.dto;

import java.util.Set;

public record UpdateUserRequest(Set<String> roles, Boolean enabled) {}
