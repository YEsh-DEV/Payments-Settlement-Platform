package io.github.yesh.paycore.ledger.strategy;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PostingStrategyRouter {

  private final Map<StrategyType, PostingStrategy> strategyMap;
  private volatile StrategyType currentStrategy;

  public PostingStrategyRouter(
      List<PostingStrategy> strategies,
      @Value("${paycore.ledger.strategy:S0_PESSIMISTIC}") String initialStrategy) {
    this.strategyMap =
        strategies.stream().collect(Collectors.toMap(PostingStrategy::getType, s -> s));
    this.currentStrategy = parseStrategy(initialStrategy);
  }

  public PostingStrategy getActiveStrategy() {
    return strategyMap.getOrDefault(currentStrategy, strategyMap.get(StrategyType.S0_PESSIMISTIC));
  }

  public PostingStrategy getStrategy(StrategyType type) {
    return strategyMap.getOrDefault(type, strategyMap.get(StrategyType.S0_PESSIMISTIC));
  }

  public void setStrategy(StrategyType type) {
    this.currentStrategy = type;
  }

  public StrategyType getCurrentStrategy() {
    return currentStrategy;
  }

  private StrategyType parseStrategy(String strategy) {
    try {
      return StrategyType.valueOf(strategy.toUpperCase());
    } catch (Exception e) {
      return StrategyType.S0_PESSIMISTIC;
    }
  }
}
