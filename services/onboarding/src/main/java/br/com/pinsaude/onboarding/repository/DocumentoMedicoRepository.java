package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.DocumentoMedico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DocumentoMedicoRepository extends JpaRepository<DocumentoMedico, UUID> {

    List<DocumentoMedico> findByMedicoId(UUID medicoId);
}
