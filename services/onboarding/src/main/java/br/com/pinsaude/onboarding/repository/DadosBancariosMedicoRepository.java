package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.DadosBancariosMedico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface DadosBancariosMedicoRepository extends JpaRepository<DadosBancariosMedico, UUID> {

    Optional<DadosBancariosMedico> findByMedicoId(UUID medicoId);
}
