package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.ConviteMedico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConviteMedicoRepository extends JpaRepository<ConviteMedico, UUID> {
    Optional<ConviteMedico> findTopByMedicoIdOrderByCreatedAtDesc(UUID medicoId);
    Optional<ConviteMedico> findByToken(String token);
}
