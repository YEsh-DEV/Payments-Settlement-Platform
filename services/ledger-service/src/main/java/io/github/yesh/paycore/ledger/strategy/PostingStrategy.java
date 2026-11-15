package io.github.yesh.paycore.ledger.strategy;

import io.github.yesh.paycore.ledger.dto.JournalRequest;
import io.github.yesh.paycore.ledger.dto.PostingResult;

public interface PostingStrategy {

  StrategyType getType();

  PostingResult post(JournalRequest request);
}
