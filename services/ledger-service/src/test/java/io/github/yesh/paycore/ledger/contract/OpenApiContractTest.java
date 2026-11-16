package io.github.yesh.paycore.ledger.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class OpenApiContractTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @Test
  @DisplayName("Ledger service OpenAPI contract exposes all mandated core endpoints")
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
            "/v1/sellers",
            "/v1/payments",
            "/v1/payments/{paymentId}/refunds",
            "/v1/holds",
            "/v1/holds/{holdId}/release",
            "/v1/payouts",
            "/v1/payouts/{payoutId}/confirm",
            "/v1/payouts/{payoutId}/fail",
            "/v1/provider-deposits",
            "/v1/adjustments",
            "/v1/accounts/{code}",
            "/v1/accounts/{code}/statement",
            "/v1/integrity");

    for (String path : requiredPaths) {
      assertThat(paths.has(path))
          .withFailMessage("OpenAPI schema regression: missing endpoint path %s", path)
          .isTrue();
    }
  }
}
