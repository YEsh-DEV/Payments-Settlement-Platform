package io.github.yesh.paycore.settlement.dto;

public record SimulationResult(
    String batchId,
    String filename,
    String csvContent,
    int manifestCount,
    int totalLines,
    long totalAmountPaise) {}
