package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.HistoricoTaxaPin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HistoricoTaxaPinRepository extends JpaRepository<HistoricoTaxaPin, UUID> {
    List<HistoricoTaxaPin> findByMedicoIdOrderByAlteradoEmDesc(UUID medicoId);
}
