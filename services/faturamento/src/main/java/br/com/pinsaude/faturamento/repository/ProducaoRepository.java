package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.Producao;
import br.com.pinsaude.faturamento.domain.StatusProducao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ProducaoRepository extends JpaRepository<Producao, UUID> {

    List<Producao> findAllByOrderByCreatedAtDesc();

    List<Producao> findByStatusOrderByCreatedAtDesc(StatusProducao status);

    List<Producao> findByMedicoIdOrderByCreatedAtDesc(UUID medicoId);

    List<Producao> findByCompetenciaOrderByCreatedAtDesc(String competencia);

    List<Producao> findByTomadorIdOrderByCreatedAtDesc(UUID tomadorId);

    @Query("""
            SELECT p FROM Producao p JOIN FETCH p.tomador t
            WHERE p.cnpjIdTenant = :tenant
            AND p.status IN :statuses
            AND p.id NOT IN (SELECT c.notaId FROM Conciliacao c)
            """)
    List<Producao> findCandidatasParaMatch(@Param("tenant") String tenant,
                                           @Param("statuses") List<StatusProducao> statuses);

    @Query("SELECT p FROM Producao p JOIN FETCH p.tomador WHERE p.id IN :ids")
    List<Producao> findAllByIdWithTomador(@Param("ids") List<UUID> ids);
}
