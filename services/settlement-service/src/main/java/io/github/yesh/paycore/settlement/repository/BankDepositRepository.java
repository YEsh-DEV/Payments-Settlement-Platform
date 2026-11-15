package io.github.yesh.paycore.settlement.repository;

import io.github.yesh.paycore.settlement.domain.BankDeposit;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BankDepositRepository extends JpaRepository<BankDeposit, UUID> {

  Optional<BankDeposit> findByBankDepositId(String bankDepositId);

  List<BankDeposit> findByStatus(String status);

  List<BankDeposit> findByValueDateBetween(LocalDate from, LocalDate to);

  Page<BankDeposit> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
