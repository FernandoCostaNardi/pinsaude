package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.ChecklistConduta;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ChecklistCondutaRepository extends JpaRepository<ChecklistConduta, UUID> {
}
