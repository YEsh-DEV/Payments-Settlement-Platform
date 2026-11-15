package io.github.yesh.paycore.settlement.dto;

import java.time.Instant;

public record BatchCutoffRequest(
    Instant cutoffTime, Long minPayoutAmountPaise, Long maxPayoutAmountPaise) {}
