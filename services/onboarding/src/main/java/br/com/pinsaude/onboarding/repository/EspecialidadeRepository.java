package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.Especialidade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EspecialidadeRepository extends JpaRepository<Especialidade, UUID> {
    List<Especialidade> findAllByAtivoTrueOrderByNomeAsc();
}
