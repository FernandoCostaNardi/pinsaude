package br.com.pinsaude.fiscal.repository;

import br.com.pinsaude.fiscal.domain.NotaFiscal;
import br.com.pinsaude.fiscal.domain.StatusNota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotaFiscalRepository extends JpaRepository<NotaFiscal, UUID> {

    Optional<NotaFiscal> findByProducaoId(UUID producaoId);

    boolean existsByProducaoId(UUID producaoId);

    boolean existsByMedicoIdAndStatus(UUID medicoId, StatusNota status);

    List<NotaFiscal> findAllByStatus(StatusNota status);

    List<NotaFiscal> findAllByOrderByCreatedAtDesc();
}
