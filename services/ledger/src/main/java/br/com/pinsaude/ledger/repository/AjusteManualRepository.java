package br.com.pinsaude.ledger.repository;

import br.com.pinsaude.ledger.domain.AjusteManual;
import br.com.pinsaude.ledger.domain.StatusAjuste;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AjusteManualRepository extends JpaRepository<AjusteManual, UUID> {
    List<AjusteManual> findByStatusOrderByCreatedAtDesc(StatusAjuste status);
    List<AjusteManual> findAllByOrderByCreatedAtDesc();
}
