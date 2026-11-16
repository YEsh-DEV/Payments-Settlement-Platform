package io.github.yesh.paycore.settlement.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
public class MakerCheckerDatabaseConstraintTest {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName(
      "Database Constraint: chk_adjustments_maker_checker physically prevents self-approval in SQL")
  void testDatabaseCheckConstraintBlocksSelfApproval() {
    String suffix = UUID.randomUUID().toString().substring(0, 8);
    String runId = "RUN_CHK_" + suffix;
    String caseId = "CASE_CHK_" + suffix;
    String adjId = "ADJ_CHK_" + suffix;

    // 0. Seed valid parent reconciliation run and case
    jdbcTemplate.update(
        "INSERT INTO reconciliation_runs (run_id, status) VALUES (?, 'COMPLETED')", runId);

    jdbcTemplate.update(
        "INSERT INTO cases (case_id, run_id, type, severity, status, variance_paise, sla_deadline) "
            + "VALUES (?, ?, 'FEE_VARIANCE', 'LOW', 'OPEN', 500, NOW() + INTERVAL '1 hour')",
        caseId,
        runId);

    // 1. Valid insertion: proposed_by is set, approved_by is NULL -> succeeds
    jdbcTemplate.update(
        "INSERT INTO adjustments (adjustment_id, case_id, template, debit_account, credit_account, amount_paise, proposed_by, approved_by, status) "
            + "VALUES (?, ?, 'FEE_VARIANCE', 'PROVIDER_FEE_EXPENSE', 'PROVIDER_RECEIVABLE', 500, 'analyst_alice', NULL, 'PROPOSED')",
        adjId,
        caseId);

    // 2. Valid update: approved_by differs from proposed_by -> succeeds
    int updatedRows =
        jdbcTemplate.update(
            "UPDATE adjustments SET approved_by = 'admin_bob', status = 'APPROVED' WHERE adjustment_id = ?",
            adjId);
    assertThat(updatedRows).isEqualTo(1);

    // 3. Invalid violation: attempting to update approved_by to identical proposer 'analyst_alice'
    // -> FAILS in PostgreSQL
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE adjustments SET approved_by = 'analyst_alice' WHERE adjustment_id = ?",
                    adjId))
        .isInstanceOf(DataIntegrityViolationException.class)
        .hasMessageContaining("chk_adjustments_maker_checker");
  }
}
