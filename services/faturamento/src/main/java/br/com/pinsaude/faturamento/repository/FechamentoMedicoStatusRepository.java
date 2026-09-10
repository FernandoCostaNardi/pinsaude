package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.FechamentoMedicoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FechamentoMedicoStatusRepository extends JpaRepository<FechamentoMedicoStatus, UUID> {

    List<FechamentoMedicoStatus> findByTomadorIdAndCompetencia(UUID tomadorId, String competencia);

    Optional<FechamentoMedicoStatus> findByTomadorIdAndMedicoIdAndCompetencia(
        UUID tomadorId, UUID medicoId, String competencia);
}
