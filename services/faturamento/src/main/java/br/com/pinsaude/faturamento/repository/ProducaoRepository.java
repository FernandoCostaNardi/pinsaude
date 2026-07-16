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

    // Native query com CAST(status AS varchar): evita conflito de :: com parser de parâmetros Spring Data
    // e o "operator does not exist: status_producao_enum = character varying" do Hibernate.
    // JOIN FETCH removido — tomador é lazy-loaded via @Transactional no caller.
    @Query(value = """
            SELECT * FROM faturamento.producoes
            WHERE cnpj_id_tenant = :tenant
              AND CAST(status AS varchar) IN (:statuses)
              AND id NOT IN (SELECT nota_id FROM faturamento.conciliacoes WHERE nota_id IS NOT NULL)
            """, nativeQuery = true)
    List<Producao> findCandidatasParaMatch(@Param("tenant") String tenant,
                                           @Param("statuses") List<String> statuses);

    @Query("SELECT p FROM Producao p JOIN FETCH p.tomador WHERE p.id IN :ids")
    List<Producao> findAllByIdWithTomador(@Param("ids") List<UUID> ids);
}
