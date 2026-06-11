package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.AliquotaCompetencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AliquotaCompetenciaRepository extends JpaRepository<AliquotaCompetencia, UUID> {
    List<AliquotaCompetencia> findByEmpresaIdOrderByCompetenciaDesc(UUID empresaId);
    Optional<AliquotaCompetencia> findByEmpresaIdAndCompetencia(UUID empresaId, String competencia);
}
