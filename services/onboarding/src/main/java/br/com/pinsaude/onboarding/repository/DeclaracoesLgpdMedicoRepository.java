package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.DeclaracoesLgpdMedico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DeclaracoesLgpdMedicoRepository extends JpaRepository<DeclaracoesLgpdMedico, UUID> {
}
