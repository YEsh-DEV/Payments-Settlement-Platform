package io.github.yesh.paycore.settlement.controller;

import io.github.yesh.paycore.settlement.domain.CaseStatus;
import io.github.yesh.paycore.settlement.dto.*;
import io.github.yesh.paycore.settlement.service.CaseService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1/cases")
public class CaseController {

  private final CaseService caseService;

  public CaseController(CaseService caseService) {
    this.caseService = caseService;
  }

  @GetMapping
  public ResponseEntity<Page<CaseResponse>> listCases(
      @RequestParam(required = false) CaseStatus status, Pageable pageable) {
    return ResponseEntity.ok(caseService.listCases(status, pageable));
  }

  @GetMapping("/{caseId}")
  public ResponseEntity<CaseResponse> getCase(@PathVariable String caseId) {
    return ResponseEntity.ok(caseService.getCase(caseId));
  }

  @PostMapping("/{caseId}/assign")
  public ResponseEntity<CaseResponse> assignCase(
      @PathVariable String caseId,
      @Valid @RequestBody CaseAssignRequest request,
      Principal principal) {
    String actor = principal != null ? principal.getName() : "OPERATOR";
    return ResponseEntity.ok(
        caseService.assignCase(caseId, request.assignedTo(), request.version(), actor));
  }

  @PostMapping("/{caseId}/transition")
  public ResponseEntity<CaseResponse> transitionStatus(
      @PathVariable String caseId,
      @Valid @RequestBody CaseTransitionRequest request,
      Principal principal) {
    String actor = principal != null ? principal.getName() : "OPERATOR";
    return ResponseEntity.ok(
        caseService.transitionStatus(
            caseId, request.newStatus(), request.version(), actor, request.comment()));
  }

  @PostMapping("/{caseId}/notes")
  public ResponseEntity<CaseNoteResponse> addNote(
      @PathVariable String caseId,
      @Valid @RequestBody CaseNoteRequest request,
      Principal principal) {
    String author = principal != null ? principal.getName() : "OPERATOR";
    return ResponseEntity.ok(caseService.addNote(caseId, author, request.content()));
  }

  @GetMapping("/{caseId}/notes")
  public ResponseEntity<List<CaseNoteResponse>> getNotes(@PathVariable String caseId) {
    return ResponseEntity.ok(caseService.getNotes(caseId));
  }
}
