package io.github.yesh.paycore.settlement.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.settlement.BaseIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class OpenApiContractTest extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  @DisplayName(
      "Settlement service OpenAPI contract exposes all mandated settlement, recon, and case endpoints")
  void verifyOpenApiContractSchema() throws Exception {
    MvcResult result = mockMvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();

    String content = result.getResponse().getContentAsString();
    assertThat(content).isNotBlank();

    JsonNode root = objectMapper.readTree(content);
    assertThat(root.has("openapi")).isTrue();
    assertThat(root.has("paths")).isTrue();

    JsonNode paths = root.get("paths");

    List<String> requiredPaths =
        List.of(
            "/v1/batches/run",
            "/v1/batches/{batchId}",
            "/v1/recon/runs",
            "/v1/recon/runs/{runId}",
            "/v1/recon/runs/{runId}/matches",
            "/v1/cases",
            "/v1/cases/{caseId}",
            "/v1/adjustments/propose",
            "/v1/adjustments/{adjustmentId}/approve",
            "/v1/files/upload",
            "/v1/files");

    for (String path : requiredPaths) {
      assertThat(paths.has(path))
          .withFailMessage("OpenAPI schema regression: missing endpoint path %s", path)
          .isTrue();
    }
  }
}
