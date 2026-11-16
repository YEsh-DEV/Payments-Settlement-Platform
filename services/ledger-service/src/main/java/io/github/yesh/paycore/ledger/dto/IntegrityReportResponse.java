package io.github.yesh.paycore.ledger.dto;

import java.time.Instant;
import java.util.List;

public record IntegrityReportResponse(
    boolean healthy,
    String activeStrategy,
    int checkedAccountsCount,
    int checkedJournalsCount,
    long totalDebitsPaise,
    long totalCreditsPaise,
    long deltaPaise,
    List<IntegrityViolation> violations,
    Instant checkedAt) {}
