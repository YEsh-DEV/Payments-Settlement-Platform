package io.github.yesh.paycore.ledger.repository;

import io.github.yesh.paycore.ledger.domain.Refund;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRepository extends JpaRepository<Refund, UUID> {

  Optional<Refund> findByRefundId(String refundId);

  List<Refund> findByPaymentId(String paymentId);
}
