package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.TomadorOcorrencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TomadorOcorrenciaRepository extends JpaRepository<TomadorOcorrencia, UUID> {
    List<TomadorOcorrencia> findByTomadorIdOrderByNomeAsc(UUID tomadorId);
}
