package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.ParticipacaoProducao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ParticipacaoRepository extends JpaRepository<ParticipacaoProducao, UUID> {

    List<ParticipacaoProducao> findByProducaoId(UUID producaoId);

    List<ParticipacaoProducao> findByProducaoIdIn(List<UUID> producaoIds);

    @Query("SELECT p.producaoId FROM ParticipacaoProducao p WHERE p.medicoId = :medicoId")
    List<UUID> findProducaoIdsByMedicoId(@Param("medicoId") UUID medicoId);
}
