package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.Producao;
import br.com.pinsaude.faturamento.domain.StatusProducao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProducaoRepository extends JpaRepository<Producao, UUID> {

    List<Producao> findAllByOrderByCreatedAtDesc();

    List<Producao> findByStatusOrderByCreatedAtDesc(StatusProducao status);

    List<Producao> findByMedicoIdOrderByCreatedAtDesc(UUID medicoId);

    List<Producao> findByCompetenciaOrderByCreatedAtDesc(String competencia);

    List<Producao> findByTomadorIdOrderByCreatedAtDesc(UUID tomadorId);
}
