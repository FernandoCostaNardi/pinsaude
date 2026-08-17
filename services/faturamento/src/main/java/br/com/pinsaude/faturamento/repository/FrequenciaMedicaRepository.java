package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.FrequenciaMedica;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FrequenciaMedicaRepository extends JpaRepository<FrequenciaMedica, UUID> {

    List<FrequenciaMedica> findAllByOrderByCompetenciaDescCreatedAtDesc();

    List<FrequenciaMedica> findByMedicoIdOrderByCompetenciaDescCreatedAtDesc(UUID medicoId);

    List<FrequenciaMedica> findByTomadorIdOrderByCompetenciaDescCreatedAtDesc(UUID tomadorId);

    List<FrequenciaMedica> findByServicoOperacionalIdOrderByCompetenciaDescCreatedAtDesc(UUID servicoOperacionalId);

    // Unicidade só se aplica a Diarista (modalidade fixa na frequência) — ver V34. Plantonista
    // não tem checagem de duplicidade: o médico pode abrir quantas frequências ("folhas")
    // precisar pro mesmo médico+setor+competência (ex.: uma pra semana, outra pro fim de semana).
    Optional<FrequenciaMedica> findByMedicoIdAndServicoOperacionalIdAndCompetenciaAndTipoMedicoAndModalidadeId(
            UUID medicoId, UUID servicoOperacionalId, String competencia, String tipoMedico, UUID modalidadeId);

    boolean existsByMedicoIdAndServicoOperacionalIdAndCompetenciaAndTipoMedicoAndModalidadeId(
            UUID medicoId, UUID servicoOperacionalId, String competencia, String tipoMedico, UUID modalidadeId);

    List<FrequenciaMedica> findByTomadorIdAndCompetencia(UUID tomadorId, String competencia);

    // Checagens de dependência usadas antes de excluir cadastros (setor/modalidade/ocorrência)
    // referenciados por frequências já lançadas — ver GlobalExceptionHandler: sem essa checagem
    // prévia, o DELETE falha com violação de FK e é mal reportado como "Registro duplicado".
    boolean existsByServicoOperacionalId(UUID servicoOperacionalId);

    boolean existsByServicoOperacionalIdIn(List<UUID> servicoOperacionalIds);

    boolean existsByModalidadeId(UUID modalidadeId);

    boolean existsByOcorrenciaId(UUID ocorrenciaId);
}
