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
}
