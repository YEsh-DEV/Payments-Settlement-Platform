package io.github.yesh.paycore.ledger.repository;

import io.github.yesh.paycore.ledger.domain.Seller;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SellerRepository extends JpaRepository<Seller, UUID> {

  Optional<Seller> findBySellerId(String sellerId);
}
