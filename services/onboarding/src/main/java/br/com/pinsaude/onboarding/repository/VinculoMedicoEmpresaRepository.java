package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.VinculoMedicoEmpresa;
import br.com.pinsaude.onboarding.domain.VinculoMedicoEmpresaId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VinculoMedicoEmpresaRepository extends JpaRepository<VinculoMedicoEmpresa, VinculoMedicoEmpresaId> {

    List<VinculoMedicoEmpresa> findByIdMedicoId(UUID medicoId);

    boolean existsByIdMedicoIdAndIdEmpresaId(UUID medicoId, UUID empresaId);
}
