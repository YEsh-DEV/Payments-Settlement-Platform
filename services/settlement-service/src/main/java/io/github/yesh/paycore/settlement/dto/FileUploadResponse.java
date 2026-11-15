package io.github.yesh.paycore.settlement.dto;

import io.github.yesh.paycore.settlement.domain.ProviderFileStatus;
import io.github.yesh.paycore.settlement.domain.ProviderSettlementFile;
import java.time.Instant;
import java.util.UUID;

public record FileUploadResponse(
    UUID fileId,
    String filename,
    String fileHash,
    String provider,
    int totalRecords,
    long totalAmountPaise,
    ProviderFileStatus status,
    Instant ingestedAt) {

  public static FileUploadResponse fromEntity(ProviderSettlementFile f) {
    return new FileUploadResponse(
        f.getId(),
        f.getFilename(),
        f.getFileHash(),
        f.getProvider(),
        f.getTotalRecords(),
        f.getTotalAmountPaise(),
        f.getStatus(),
        f.getIngestedAt());
  }
}
