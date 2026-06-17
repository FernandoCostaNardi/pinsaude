package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.ContratoAssinatura;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ContratoAssinaturaRepository extends JpaRepository<ContratoAssinatura, UUID> {
    Optional<ContratoAssinatura> findTopByMedicoIdOrderByCreatedAtDesc(UUID medicoId);
    Optional<ContratoAssinatura> findByDocumentoKey(String documentoKey);
}
