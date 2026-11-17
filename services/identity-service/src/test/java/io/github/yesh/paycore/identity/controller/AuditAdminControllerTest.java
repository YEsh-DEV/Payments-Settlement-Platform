package io.github.yesh.paycore.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.yesh.paycore.identity.domain.AuditLog;
import io.github.yesh.paycore.identity.service.AuditService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AuditAdminControllerTest {

  private AuditService auditService;
  private AuditAdminController controller;

  @BeforeEach
  void setUp() {
    auditService = mock(AuditService.class);
    controller = new AuditAdminController(auditService);
  }

  @Test
  @DisplayName("AuditAdminController returns paginated audit records via AuditService")
  void testListAuditLogs() {
    AuditLog log =
        new AuditLog(
            "admin", "LOGIN_SUCCESS", "USER", "123", "{\"ip\":\"127.0.0.1\"}", "req_test123");
    Page<AuditLog> page = new PageImpl<>(List.of(log));

    when(auditService.listAuditLogs(any(PageRequest.class))).thenReturn(page);

    ResponseEntity<Page<AuditLog>> response = controller.listAuditLogs(PageRequest.of(0, 10));

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getContent()).hasSize(1);
    assertThat(response.getBody().getContent().get(0).getActor()).isEqualTo("admin");
  }
}
