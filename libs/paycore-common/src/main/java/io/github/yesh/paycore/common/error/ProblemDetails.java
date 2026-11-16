package io.github.yesh.paycore.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/** Standard RFC 9457 Problem Details for HTTP APIs. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetails(
    String type,
    String title,
    int status,
    String detail,
    String code,
    String requestId,
    Instant timestamp,
    List<InvalidParam> invalidParams) {

  public record InvalidParam(String name, String reason) {}

  public ProblemDetails {
    invalidParams = invalidParams != null ? List.copyOf(invalidParams) : null;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private String type = "about:blank";
    private String title;
    private int status;
    private String detail;
    private String code;
    private String requestId;
    private Instant timestamp = Instant.now();
    private List<InvalidParam> invalidParams;

    public Builder type(String type) {
      this.type = type;
      return this;
    }

    public Builder title(String title) {
      this.title = title;
      return this;
    }

    public Builder status(int status) {
      this.status = status;
      return this;
    }

    public Builder detail(String detail) {
      this.detail = detail;
      return this;
    }

    public Builder code(String code) {
      this.code = code;
      return this;
    }

    public Builder requestId(String requestId) {
      this.requestId = requestId;
      return this;
    }

    public Builder timestamp(Instant timestamp) {
      this.timestamp = timestamp;
      return this;
    }

    public Builder invalidParams(List<InvalidParam> invalidParams) {
      this.invalidParams = invalidParams != null ? List.copyOf(invalidParams) : null;
      return this;
    }

    public ProblemDetails build() {
      return new ProblemDetails(
          type, title, status, detail, code, requestId, timestamp, invalidParams);
    }
  }
}
