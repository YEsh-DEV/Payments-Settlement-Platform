package io.github.yesh.paycore.ledger.repository;

import io.github.yesh.paycore.ledger.domain.Payment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

  Optional<Payment> findByPaymentId(String paymentId);
}
