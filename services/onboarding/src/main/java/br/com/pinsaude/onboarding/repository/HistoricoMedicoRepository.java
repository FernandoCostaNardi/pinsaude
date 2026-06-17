package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.HistoricoMedico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HistoricoMedicoRepository extends JpaRepository<HistoricoMedico, UUID> {

    List<HistoricoMedico> findByMedicoIdOrderByCreatedAtDesc(UUID medicoId);
}
