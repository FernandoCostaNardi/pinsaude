package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.SetorOperacionalOcorrencia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SetorOperacionalOcorrenciaRepository extends JpaRepository<SetorOperacionalOcorrencia, UUID> {
    List<SetorOperacionalOcorrencia> findByOcorrenciaId(UUID ocorrenciaId);
    List<SetorOperacionalOcorrencia> findByOcorrenciaIdIn(Collection<UUID> ocorrenciaIds);
    boolean existsByOcorrenciaId(UUID ocorrenciaId);
    boolean existsBySetorIdAndOcorrenciaId(UUID setorId, UUID ocorrenciaId);
    void deleteByOcorrenciaId(UUID ocorrenciaId);
}
