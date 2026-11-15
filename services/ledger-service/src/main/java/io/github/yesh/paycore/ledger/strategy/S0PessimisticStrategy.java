package io.github.yesh.paycore.ledger.strategy;

import io.github.yesh.paycore.ledger.dto.JournalRequest;
import io.github.yesh.paycore.ledger.dto.PostingResult;
import io.github.yesh.paycore.ledger.engine.PostingEngine;
import org.springframework.stereotype.Component;

@Component
public class S0PessimisticStrategy implements PostingStrategy {

  private final PostingEngine postingEngine;

  public S0PessimisticStrategy(PostingEngine postingEngine) {
    this.postingEngine = postingEngine;
  }

  @Override
  public StrategyType getType() {
    return StrategyType.S0_PESSIMISTIC;
  }

  @Override
  public PostingResult post(JournalRequest request) {
    return postingEngine.post(request);
  }
}
