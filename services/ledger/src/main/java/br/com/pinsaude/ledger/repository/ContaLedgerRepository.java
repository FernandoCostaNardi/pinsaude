package br.com.pinsaude.ledger.repository;

import br.com.pinsaude.ledger.domain.ContaLedger;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ContaLedgerRepository extends JpaRepository<ContaLedger, UUID> {
    Optional<ContaLedger> findByCodigo(String codigo);
}
