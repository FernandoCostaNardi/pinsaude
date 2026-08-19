package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.MedicoTomadorSetor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface MedicoTomadorSetorRepository extends JpaRepository<MedicoTomadorSetor, UUID> {
    List<MedicoTomadorSetor> findByMedicoTomadorId(UUID medicoTomadorId);
    List<MedicoTomadorSetor> findByMedicoTomadorIdIn(Collection<UUID> medicoTomadorIds);
    boolean existsByMedicoTomadorIdAndSetorId(UUID medicoTomadorId, UUID setorId);
    void deleteByMedicoTomadorIdAndSetorId(UUID medicoTomadorId, UUID setorId);
}
