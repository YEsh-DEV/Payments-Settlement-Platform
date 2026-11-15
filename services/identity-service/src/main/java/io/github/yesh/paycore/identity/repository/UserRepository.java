package io.github.yesh.paycore.identity.repository;

import io.github.yesh.paycore.identity.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
  Optional<User> findByUsernameIgnoreCase(String username);

  boolean existsByUsernameIgnoreCase(String username);
}
