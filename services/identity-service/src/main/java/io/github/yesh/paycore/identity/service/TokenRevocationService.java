package io.github.yesh.paycore.identity.service;

import io.github.yesh.paycore.identity.repository.RefreshTokenRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TokenRevocationService {

  private final RefreshTokenRepository refreshTokenRepository;

  public TokenRevocationService(RefreshTokenRepository refreshTokenRepository) {
    this.refreshTokenRepository = refreshTokenRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void revokeFamilyImmediately(UUID familyId) {
    refreshTokenRepository.revokeFamily(familyId, Instant.now());
  }
}
