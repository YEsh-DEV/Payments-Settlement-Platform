package io.github.yesh.paycore.settlement.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.yesh.paycore.common.error.BaseExceptionHandler;
import io.github.yesh.paycore.settlement.exception.MakerCheckerViolationException;
import io.github.yesh.paycore.settlement.service.AdjustmentService;
import java.security.Principal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

public class AdjustmentControllerMvcTest {

  private AdjustmentService adjustmentService;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    adjustmentService = Mockito.mock(AdjustmentService.class);
    AdjustmentController controller = new AdjustmentController(adjustmentService);
    mockMvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new BaseExceptionHandler())
            .build();
  }

  @Test
  @DisplayName(
      "Maker-Checker: self-approval returns clean 422 with maker_checker_violation code and no stack trace")
  void testSelfApprovalReturnsClean422() throws Exception {
    Principal alicePrincipal = () -> "analyst_alice";
    when(adjustmentService.approveAdjustment("ADJ_1", "analyst_alice"))
        .thenThrow(
            new MakerCheckerViolationException(
                "Maker-checker violation: Proposer analyst_alice cannot approve their own adjustment"));

    mockMvc
        .perform(post("/v1/adjustments/ADJ_1/approve").principal(alicePrincipal))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value(422))
        .andExpect(jsonPath("$.code").value("maker_checker_violation"))
        .andExpect(jsonPath("$.title").value("Unprocessable Entity"))
        .andExpect(
            jsonPath("$.detail")
                .value(
                    "Maker-checker violation: Proposer analyst_alice cannot approve their own adjustment"))
        .andExpect(jsonPath("$.stackTrace").doesNotExist())
        .andExpect(jsonPath("$.trace").doesNotExist());
  }
}
