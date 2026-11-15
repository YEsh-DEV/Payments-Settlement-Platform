package io.github.yesh.paycore.ledger.repository;

import io.github.yesh.paycore.ledger.domain.AccountStripe;
import io.github.yesh.paycore.ledger.domain.AccountStripeId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountStripeRepository extends JpaRepository<AccountStripe, AccountStripeId> {

  List<AccountStripe> findByIdAccountId(UUID accountId);
}
