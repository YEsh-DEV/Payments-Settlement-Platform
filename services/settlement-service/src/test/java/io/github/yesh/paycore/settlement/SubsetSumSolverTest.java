package io.github.yesh.paycore.settlement;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.yesh.paycore.settlement.reconciliation.SubsetSumSolver;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SubsetSumSolverTest {

  @Test
  @DisplayName("Should find exact subset matching target amount")
  void testExactSubsetMatch() {
    List<SubsetSumSolver.BatchItem> items =
        List.of(
            new SubsetSumSolver.BatchItem("B1", 100000L),
            new SubsetSumSolver.BatchItem("B2", 150000L),
            new SubsetSumSolver.BatchItem("B3", 200000L),
            new SubsetSumSolver.BatchItem("B4", 250000L));

    // B1 (100k) + B3 (200k) = 300k
    SubsetSumSolver.SolverResult result = SubsetSumSolver.solve(items, 300000L, 0L, 5000L, 10000);

    assertThat(result.status()).isEqualTo(SubsetSumSolver.SolverStatus.EXACT_MATCH);
    assertThat(result.solutions()).hasSize(1);
    List<String> matchedIds =
        result.solutions().get(0).stream().map(SubsetSumSolver.BatchItem::batchId).toList();
    assertThat(matchedIds).containsExactlyInAnyOrder("B1", "B3");
  }

  @Test
  @DisplayName("Should detect ambiguous matches when multiple distinct subsets sum to target")
  void testAmbiguousSubsetMatch() {
    List<SubsetSumSolver.BatchItem> items =
        List.of(
            new SubsetSumSolver.BatchItem("B1", 100000L),
            new SubsetSumSolver.BatchItem("B2", 200000L),
            new SubsetSumSolver.BatchItem("B3", 300000L));

    // Both {B1, B2} and {B3} equal 300k
    SubsetSumSolver.SolverResult result = SubsetSumSolver.solve(items, 300000L, 0L, 5000L, 10000);

    assertThat(result.status()).isEqualTo(SubsetSumSolver.SolverStatus.AMBIGUOUS_MATCH);
    assertThat(result.solutions()).hasSizeGreaterThanOrEqualTo(2);
  }

  @Test
  @DisplayName("Should return NO_MATCH when no subset matches target amount")
  void testNoMatch() {
    List<SubsetSumSolver.BatchItem> items =
        List.of(
            new SubsetSumSolver.BatchItem("B1", 100000L),
            new SubsetSumSolver.BatchItem("B2", 200000L));

    SubsetSumSolver.SolverResult result = SubsetSumSolver.solve(items, 999999L, 0L, 5000L, 10000);

    assertThat(result.status()).isEqualTo(SubsetSumSolver.SolverStatus.NO_MATCH);
    assertThat(result.solutions()).isEmpty();
  }

  @Test
  @DisplayName("Should return TIMEOUT when state cap is exceeded")
  void testStateCapHitReturnsTimeout() {
    List<SubsetSumSolver.BatchItem> items =
        java.util.stream.IntStream.range(1, 25)
            .mapToObj(i -> new SubsetSumSolver.BatchItem("B" + i, (long) i * 1000L))
            .toList();

    // Total sum is 300k, target 150k is reachable, but state cap is only 10
    SubsetSumSolver.SolverResult result = SubsetSumSolver.solve(items, 150000L, 0L, 5000L, 10);

    assertThat(result.status()).isEqualTo(SubsetSumSolver.SolverStatus.TIMEOUT);
  }

  @Test
  @DisplayName("Should return TIMEOUT when execution deadline is exceeded")
  void testTimeoutDeadlineReturnsTimeout() {
    List<SubsetSumSolver.BatchItem> items =
        java.util.stream.IntStream.range(1, 30)
            .mapToObj(i -> new SubsetSumSolver.BatchItem("B" + i, (long) i * 1000L))
            .toList();

    // 0ms timeout forces immediate timeout
    SubsetSumSolver.SolverResult result = SubsetSumSolver.solve(items, 150000L, 0L, 0L, 100000);

    assertThat(result.status()).isEqualTo(SubsetSumSolver.SolverStatus.TIMEOUT);
  }

  @Test
  @DisplayName("Should match with reserve holdback tolerance")
  void testReserveHoldbackToleranceMatch() {
    List<SubsetSumSolver.BatchItem> items =
        List.of(
            new SubsetSumSolver.BatchItem("B1", 100000L),
            new SubsetSumSolver.BatchItem("B2", 200000L));

    // Batch B1 is 100,000 paise; bank deposited 95,000 paise (5,000 paise reserve holdback)
    // Tolerance is 5,000 paise
    SubsetSumSolver.SolverResult result = SubsetSumSolver.solve(items, 95000L, 5000L, 5000L, 10000);

    assertThat(result.status()).isEqualTo(SubsetSumSolver.SolverStatus.EXACT_MATCH);
    assertThat(result.solutions()).hasSize(1);
    assertThat(result.solutions().get(0).get(0).batchId()).isEqualTo("B1");
  }

  @Test
  @DisplayName("Should bound search space to at most 40 batches")
  void testItemsBoundedTo40() {
    // 45 items of 1,000 paise each; target is 42,000 paise
    // If bounded to 40 items, total sum of 40 items is 40,000 paise, so 42,000 paise cannot be matched
    List<SubsetSumSolver.BatchItem> items =
        java.util.stream.IntStream.range(0, 45)
            .mapToObj(i -> new SubsetSumSolver.BatchItem("B" + i, 1000L))
            .toList();

    SubsetSumSolver.SolverResult result = SubsetSumSolver.solve(items, 42000L, 0L, 5000L, 100000);

    assertThat(result.status()).isEqualTo(SubsetSumSolver.SolverStatus.NO_MATCH);
  }
}
