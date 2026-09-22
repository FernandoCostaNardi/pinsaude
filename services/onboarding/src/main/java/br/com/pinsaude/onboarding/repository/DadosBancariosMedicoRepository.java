package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.DadosBancariosMedico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DadosBancariosMedicoRepository extends JpaRepository<DadosBancariosMedico, UUID> {

    List<DadosBancariosMedico> findByMedicoIdOrderByCreatedAtAsc(UUID medicoId);

    Optional<DadosBancariosMedico> findByIdAndMedicoId(UUID id, UUID medicoId);
}
