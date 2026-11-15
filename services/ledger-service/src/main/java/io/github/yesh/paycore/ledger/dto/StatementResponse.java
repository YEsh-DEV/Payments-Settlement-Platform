package io.github.yesh.paycore.ledger.dto;

import java.util.List;

public record StatementResponse(
    String accountCode, List<LedgerEntryDto> entries, Long nextCursor, boolean hasMore) {}
