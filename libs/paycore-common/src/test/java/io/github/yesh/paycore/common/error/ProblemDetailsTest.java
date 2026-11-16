package io.github.yesh.paycore.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ProblemDetailsTest {

  @Test
  @DisplayName("ProblemDetails builder creates valid RFC 9457 object with immutable parameters")
  void testProblemDetailsBuilderAndImmutability() {
    List<ProblemDetails.InvalidParam> params = new ArrayList<>();
    params.add(new ProblemDetails.InvalidParam("amount", "must be positive"));

    ProblemDetails details =
        ProblemDetails.builder()
            .type("https://api.paycore.io/errors/validation-error")
            .title("Validation Error")
            .status(400)
            .detail("Request payload has validation errors")
            .code("invalid_input")
            .requestId("req_12345")
            .timestamp(Instant.now())
            .invalidParams(params)
            .build();

    assertThat(details.status()).isEqualTo(400);
    assertThat(details.code()).isEqualTo("invalid_input");
    assertThat(details.invalidParams()).hasSize(1);
    assertThat(details.invalidParams().get(0).name()).isEqualTo("amount");

    // Modify original list to ensure defensive copy
    params.add(new ProblemDetails.InvalidParam("currency", "must be INR"));
    assertThat(details.invalidParams()).hasSize(1);

    // Ensure returned list is unmodifiable
    List<ProblemDetails.InvalidParam> returned = details.invalidParams();
    assertThatThrownBy(() -> returned.add(new ProblemDetails.InvalidParam("x", "y")))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
