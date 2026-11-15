package io.github.yesh.paycore.settlement.repository;

import io.github.yesh.paycore.settlement.domain.CaseNote;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CaseNoteRepository extends JpaRepository<CaseNote, Long> {

  List<CaseNote> findByCaseIdOrderByCreatedAtAsc(String caseId);
}
