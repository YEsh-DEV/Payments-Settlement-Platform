package io.github.yesh.paycore.identity.repository;

import io.github.yesh.paycore.identity.domain.RefreshToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  java.util.List<RefreshToken> findByUserId(UUID userId);

  java.util.List<RefreshToken> findByFamilyId(UUID familyId);

  @Modifying
  @Query(
      "UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.familyId = :familyId AND r.revokedAt IS NULL")
  void revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);
}
