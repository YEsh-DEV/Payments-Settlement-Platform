package io.github.yesh.paycore.identity.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.yesh.paycore.identity.BaseIntegrationTest;
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
      "Identity service OpenAPI contract exposes all mandated core auth and user endpoints")
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
            "/auth/login",
            "/auth/refresh",
            "/auth/logout",
            "/auth/me",
            "/auth/service-token",
            "/.well-known/jwks.json",
            "/v1/users",
            "/v1/users/{id}");

    for (String path : requiredPaths) {
      assertThat(paths.has(path))
          .withFailMessage("OpenAPI schema regression: missing endpoint path %s", path)
          .isTrue();
    }
  }
}
