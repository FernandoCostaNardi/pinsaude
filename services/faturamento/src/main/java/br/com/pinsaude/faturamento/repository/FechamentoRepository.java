package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.Fechamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FechamentoRepository extends JpaRepository<Fechamento, UUID> {

    Optional<Fechamento> findByTomadorIdAndCompetencia(UUID tomadorId, String competencia);

    List<Fechamento> findByTomadorIdOrderByCompetenciaDesc(UUID tomadorId);

    boolean existsByTomadorIdAndCompetencia(UUID tomadorId, String competencia);
}
