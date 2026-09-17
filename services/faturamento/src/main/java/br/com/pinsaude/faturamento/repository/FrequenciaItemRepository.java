package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.FrequenciaItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FrequenciaItemRepository extends JpaRepository<FrequenciaItem, UUID> {

    List<FrequenciaItem> findByFrequenciaIdOrderByDataExecucaoAscCreatedAtAsc(UUID frequenciaId);

    void deleteByFrequenciaId(UUID frequenciaId);

    List<FrequenciaItem> findByFrequenciaIdIn(List<UUID> frequenciaIds);

    boolean existsByModalidadeId(UUID modalidadeId);

    boolean existsByOcorrenciaId(UUID ocorrenciaId);

    // Nunca pode repetir o mesmo dia dentro da mesma frequência (pedido do cliente).
    boolean existsByFrequenciaIdAndDataExecucao(UUID frequenciaId, LocalDate dataExecucao);

    // Mesma checagem, excluindo o próprio item — usado em atualizarItem (editar mantendo a
    // mesma data não pode colidir consigo mesmo).
    boolean existsByFrequenciaIdAndDataExecucaoAndIdNot(UUID frequenciaId, LocalDate dataExecucao, UUID id);
}
