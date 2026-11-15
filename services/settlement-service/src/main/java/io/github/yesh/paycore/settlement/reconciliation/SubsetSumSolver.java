package io.github.yesh.paycore.settlement.reconciliation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SubsetSumSolver {

  public record BatchItem(String batchId, long amountPaise) {}

  public enum SolverStatus {
    EXACT_MATCH,
    AMBIGUOUS_MATCH,
    NO_MATCH,
    TIMEOUT
  }

  public record SolverResult(SolverStatus status, List<List<BatchItem>> solutions) {}

  public static SolverResult solve(
      List<BatchItem> items, long targetAmount, long tolerance, long timeoutMs, int maxCap) {
    if (items == null || items.isEmpty()) {
      return new SolverResult(SolverStatus.NO_MATCH, Collections.emptyList());
    }

    long deadline = System.currentTimeMillis() + timeoutMs;
    List<List<BatchItem>> foundSolutions = new ArrayList<>();

    // Use Meet-in-the-Middle if N <= 40 or recursive bounded branch and bound
    int n = Math.min(items.size(), 40);
    List<BatchItem> sub = items.subList(0, n);

    searchSubsets(
        sub, 0, targetAmount, tolerance, new ArrayList<>(), foundSolutions, deadline, maxCap);

    if (System.currentTimeMillis() > deadline) {
      return new SolverResult(SolverStatus.TIMEOUT, foundSolutions);
    }

    if (foundSolutions.isEmpty()) {
      return new SolverResult(SolverStatus.NO_MATCH, Collections.emptyList());
    } else if (foundSolutions.size() == 1) {
      return new SolverResult(SolverStatus.EXACT_MATCH, foundSolutions);
    } else {
      return new SolverResult(SolverStatus.AMBIGUOUS_MATCH, foundSolutions);
    }
  }

  private static void searchSubsets(
      List<BatchItem> items,
      int index,
      long remainingTarget,
      long tolerance,
      List<BatchItem> current,
      List<List<BatchItem>> solutions,
      long deadline,
      int maxCap) {
    if (System.currentTimeMillis() > deadline || solutions.size() >= 2) {
      return; // Stop if timeout or already found more than 1 (ambiguous)
    }

    if (Math.abs(remainingTarget) <= tolerance && !current.isEmpty()) {
      solutions.add(new ArrayList<>(current));
      return;
    }

    if (index >= items.size() || remainingTarget < -tolerance) {
      return;
    }

    // Include item
    BatchItem item = items.get(index);
    current.add(item);
    searchSubsets(
        items,
        index + 1,
        remainingTarget - item.amountPaise(),
        tolerance,
        current,
        solutions,
        deadline,
        maxCap);
    current.remove(current.size() - 1);

    // Exclude item
    searchSubsets(
        items, index + 1, remainingTarget, tolerance, current, solutions, deadline, maxCap);
  }
}
