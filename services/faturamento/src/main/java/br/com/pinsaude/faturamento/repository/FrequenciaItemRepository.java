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

    // Itens já lançados no mesmo dia dentro da mesma frequência — usado para checar conflito de
    // horário entre lançamentos (ver FrequenciaService.validarConflitoHorario). Duas datas podem
    // coexistir desde que os horários não se sobreponham (pedido do cliente).
    List<FrequenciaItem> findByFrequenciaIdAndDataExecucao(UUID frequenciaId, LocalDate dataExecucao);
}
