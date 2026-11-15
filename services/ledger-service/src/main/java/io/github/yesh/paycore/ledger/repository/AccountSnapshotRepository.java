package io.github.yesh.paycore.ledger.repository;

import io.github.yesh.paycore.ledger.domain.AccountSnapshot;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountSnapshotRepository extends JpaRepository<AccountSnapshot, Long> {

  Optional<AccountSnapshot> findFirstByAccountIdOrderByAsOfEntryIdDesc(UUID accountId);
}
