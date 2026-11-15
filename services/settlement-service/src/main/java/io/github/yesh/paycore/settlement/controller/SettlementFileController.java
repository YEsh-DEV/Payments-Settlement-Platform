package io.github.yesh.paycore.settlement.controller;

import io.github.yesh.paycore.settlement.dto.FileUploadResponse;
import io.github.yesh.paycore.settlement.repository.ProviderSettlementFileRepository;
import io.github.yesh.paycore.settlement.service.StreamingFileParser;
import java.io.IOException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1/files")
public class SettlementFileController {

  private final StreamingFileParser streamingFileParser;
  private final ProviderSettlementFileRepository fileRepository;

  public SettlementFileController(
      StreamingFileParser streamingFileParser, ProviderSettlementFileRepository fileRepository) {
    this.streamingFileParser = streamingFileParser;
    this.fileRepository = fileRepository;
  }

  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<FileUploadResponse> uploadFile(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "provider", defaultValue = "RAZORPAY_SIM") String provider)
      throws IOException {
    FileUploadResponse response =
        streamingFileParser.parseAndIngest(
            file.getOriginalFilename(), provider, file.getInputStream());
    return ResponseEntity.ok(response);
  }

  @GetMapping
  public ResponseEntity<Page<FileUploadResponse>> listFiles(Pageable pageable) {
    return ResponseEntity.ok(
        fileRepository
            .findAllByOrderByIngestedAtDesc(pageable)
            .map(FileUploadResponse::fromEntity));
  }
}
