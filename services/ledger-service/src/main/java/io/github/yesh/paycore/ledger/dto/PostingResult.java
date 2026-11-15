package io.github.yesh.paycore.ledger.dto;

import io.github.yesh.paycore.ledger.domain.Journal;
import io.github.yesh.paycore.ledger.domain.LedgerEntry;
import java.util.List;

public record PostingResult(Journal journal, List<LedgerEntry> entries) {}
