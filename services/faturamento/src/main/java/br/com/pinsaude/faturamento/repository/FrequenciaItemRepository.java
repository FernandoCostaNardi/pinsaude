package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.FrequenciaItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FrequenciaItemRepository extends JpaRepository<FrequenciaItem, UUID> {

    List<FrequenciaItem> findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(UUID frequenciaId);

    void deleteByFrequenciaId(UUID frequenciaId);
}
