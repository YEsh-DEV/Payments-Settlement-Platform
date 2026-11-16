package io.github.yesh.paycore.ledger.dto;

public record IntegrityViolation(
    String invariant,
    String entityType,
    String entityId,
    String description,
    long expectedValue,
    long actualValue) {}
