package io.github.yesh.paycore.settlement.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.yesh.paycore.common.error.PayCoreException;
import io.github.yesh.paycore.settlement.domain.CaseSeverity;
import io.github.yesh.paycore.settlement.domain.CaseStatus;
import io.github.yesh.paycore.settlement.domain.CaseType;
import io.github.yesh.paycore.settlement.dto.CaseAssignRequest;
import io.github.yesh.paycore.settlement.dto.CaseResponse;
import io.github.yesh.paycore.settlement.service.CaseService;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class CaseControllerTest {

  private CaseService caseService;
  private CaseController controller;

  @BeforeEach
  void setUp() {
    caseService = mock(CaseService.class);
    controller = new CaseController(caseService);
  }

  @Test
  @DisplayName("F-S-05: List discrepancy cases returns paginated cases")
  void testListCases() {
    CaseResponse c1 =
        new CaseResponse(
            UUID.randomUUID(),
            "CASE_1",
            "RUN_1",
            "REC_1",
            101L,
            null,
            CaseType.FEE_VARIANCE,
            CaseSeverity.MEDIUM,
            CaseStatus.OPEN,
            "analyst_1",
            null,
            null,
            null,
            150L,
            Instant.now().plusSeconds(3600),
            0L,
            Instant.now(),
            null);
    Page<CaseResponse> page = new PageImpl<>(List.of(c1));
    when(caseService.listCases(eq(null), any())).thenReturn(page);

    ResponseEntity<Page<CaseResponse>> response = controller.listCases(null, PageRequest.of(0, 10));
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().getContent()).containsExactly(c1);
  }

  @Test
  @DisplayName("F-S-05: Get case returns case details")
  void testGetCase() {
    CaseResponse c1 =
        new CaseResponse(
            UUID.randomUUID(),
            "CASE_1",
            "RUN_1",
            "REC_1",
            101L,
            null,
            CaseType.MISSING_IN_PROVIDER,
            CaseSeverity.HIGH,
            CaseStatus.OPEN,
            null,
            null,
            null,
            null,
            5000L,
            Instant.now().plusSeconds(7200),
            0L,
            Instant.now(),
            null);
    when(caseService.getCase("CASE_1")).thenReturn(c1);

    ResponseEntity<CaseResponse> response = controller.getCase("CASE_1");
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isEqualTo(c1);
  }

  @Test
  @DisplayName("F-S-05: Get non-existent case throws 404")
  void testGetNonExistentCaseThrows() {
    when(caseService.getCase("UNKNOWN"))
        .thenThrow(
            new PayCoreException(HttpStatus.NOT_FOUND, "case_not_found", "Case UNKNOWN not found"));

    assertThatThrownBy(() -> controller.getCase("UNKNOWN"))
        .isInstanceOf(PayCoreException.class)
        .hasMessageContaining("not found");
  }

  @Test
  @DisplayName("F-S-05: Assign case updates assignee and version")
  void testAssignCase() {
    Principal principal = () -> "admin_bob";
    CaseResponse updated =
        new CaseResponse(
            UUID.randomUUID(),
            "CASE_1",
            "RUN_1",
            "REC_1",
            101L,
            null,
            CaseType.MISSING_IN_PROVIDER,
            CaseSeverity.HIGH,
            CaseStatus.OPEN,
            "analyst_charlie",
            null,
            null,
            null,
            5000L,
            Instant.now().plusSeconds(7200),
            1L,
            Instant.now(),
            null);

    when(caseService.assignCase("CASE_1", "analyst_charlie", 0L, "admin_bob")).thenReturn(updated);

    ResponseEntity<CaseResponse> response =
        controller.assignCase("CASE_1", new CaseAssignRequest("analyst_charlie", 0L), principal);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody().assignedTo()).isEqualTo("analyst_charlie");
    assertThat(response.getBody().version()).isEqualTo(1L);
  }
}
