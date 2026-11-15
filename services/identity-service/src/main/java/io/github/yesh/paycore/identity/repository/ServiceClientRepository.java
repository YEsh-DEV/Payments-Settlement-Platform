package io.github.yesh.paycore.identity.repository;

import io.github.yesh.paycore.identity.domain.ServiceClient;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ServiceClientRepository extends JpaRepository<ServiceClient, UUID> {
  Optional<ServiceClient> findByClientId(String clientId);
}
