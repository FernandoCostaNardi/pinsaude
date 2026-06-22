package br.com.pinsaude.fiscal.repository;

import br.com.pinsaude.fiscal.domain.NotaFiscal;
import br.com.pinsaude.fiscal.domain.StatusNota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotaFiscalRepository extends JpaRepository<NotaFiscal, UUID> {

    Optional<NotaFiscal> findByProducaoId(UUID producaoId);

    boolean existsByProducaoId(UUID producaoId);

    boolean existsByMedicoIdAndStatus(UUID medicoId, StatusNota status);

    List<NotaFiscal> findAllByStatus(StatusNota status);

    List<NotaFiscal> findAllByOrderByCreatedAtDesc();

    // Queries para processamento em lote (EPIC-05.7)
    List<NotaFiscal> findByCompetenciaAndStatusIn(String competencia, Collection<StatusNota> statuses);

    @Query("SELECT COUNT(n) FROM NotaFiscal n WHERE n.competencia = :comp AND n.status = :status")
    int countByCompetenciaAndStatus(@Param("comp") String competencia, @Param("status") StatusNota status);
}
