package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.FrequenciaMedica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FrequenciaMedicaRepository extends JpaRepository<FrequenciaMedica, UUID> {

    List<FrequenciaMedica> findAllByOrderByCompetenciaDescCreatedAtDesc();

    List<FrequenciaMedica> findByMedicoIdOrderByCompetenciaDescCreatedAtDesc(UUID medicoId);

    List<FrequenciaMedica> findByTomadorIdOrderByCompetenciaDescCreatedAtDesc(UUID tomadorId);

    List<FrequenciaMedica> findByServicoOperacionalIdOrderByCompetenciaDescCreatedAtDesc(UUID servicoOperacionalId);

    Optional<FrequenciaMedica> findByMedicoIdAndServicoOperacionalIdAndCompetencia(
            UUID medicoId, UUID servicoOperacionalId, String competencia);

    boolean existsByMedicoIdAndServicoOperacionalIdAndCompetencia(
            UUID medicoId, UUID servicoOperacionalId, String competencia);
}
