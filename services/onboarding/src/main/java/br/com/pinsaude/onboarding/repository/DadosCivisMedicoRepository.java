package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.DadosCivisMedico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DadosCivisMedicoRepository extends JpaRepository<DadosCivisMedico, UUID> {
}
