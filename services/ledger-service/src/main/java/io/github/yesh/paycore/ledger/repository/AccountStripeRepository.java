package io.github.yesh.paycore.ledger.repository;

import io.github.yesh.paycore.ledger.domain.AccountStripe;
import io.github.yesh.paycore.ledger.domain.AccountStripeId;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountStripeRepository extends JpaRepository<AccountStripe, AccountStripeId> {

  List<AccountStripe> findByIdAccountId(UUID accountId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "SELECT s FROM AccountStripe s WHERE s.id.accountId = :accountId AND s.id.stripeIndex = :stripeIndex")
  Optional<AccountStripe> findStripeForUpdate(
      @Param("accountId") UUID accountId, @Param("stripeIndex") int stripeIndex);

  @Query(
      "SELECT COALESCE(SUM(s.balancePaise), 0) FROM AccountStripe s WHERE s.id.accountId = :accountId")
  long sumBalanceByAccountId(@Param("accountId") UUID accountId);
}
