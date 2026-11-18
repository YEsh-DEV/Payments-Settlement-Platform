package io.github.yesh.paycore.identity.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.yesh.paycore.identity.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
public class JsonBodyLimitSecurityTest extends BaseIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Test
  @DisplayName("E5: General JSON request with body exceeding 2 MB returns 413 Payload Too Large")
  void testOversizedJsonPayloadReturns413() throws Exception {
    byte[] oversizedPayload = new byte[2 * 1024 * 1024 + 1024]; // 2 MB + 1 KB

    mockMvc
        .perform(
            post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(oversizedPayload))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.status").value(413))
        .andExpect(jsonPath("$.code").value("payload_too_large"))
        .andExpect(
            jsonPath("$.detail").value("Request body exceeds the maximum permitted limit of 2 MB"));
  }

  @Test
  @DisplayName("E5: Normal JSON payload within 2 MB does not trigger 413 limit")
  void testNormalJsonPayloadWithinLimit() throws Exception {
    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"alice\",\"password\":\"wrong\"}"))
        .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotEqualTo(413));
  }

  @Test
  @DisplayName(
      "E5: Chunked stream request exceeding 2 MB is caught by stream inspection and returns 413")
  void testChunkedStreamExceeding2MbReturns413() throws Exception {
    byte[] oversizedPayload = new byte[2 * 1024 * 1024 + 1024];

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .header(org.springframework.http.HttpHeaders.TRANSFER_ENCODING, "chunked")
                .content(oversizedPayload))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.status").value(413))
        .andExpect(jsonPath("$.code").value("payload_too_large"));
  }
}
