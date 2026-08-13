package br.com.pinsaude.faturamento.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

// horaInicio/horaFim substituem o antigo campo horasTrabalhadas (PINSAUDE-13.25): para
// modalidade DIARISTA o médico digita a hora de entrada e saída do dia, e o backend deriva
// a quantidade de horas (ver FrequenciaService.calcularHorasTrabalhadas) — nunca aceito
// diretamente do cliente. PLANTONISTA não usa nenhum dos dois campos.
//
// modalidadeId/ocorrenciaId (PINSAUDE-13.26): deixaram de ser obrigatórios aqui — quando a
// frequência tem modalidade fixa (escolhida na criação), o service ignora estes dois campos e
// usa sempre os valores da frequência. Só frequências legadas sem modalidade fixa (dado
// anterior a esta mudança) continuam exigindo modalidadeId aqui, validado no service.
public record FrequenciaItemRequest(
    UUID modalidadeId,
    @NotNull LocalDate dataExecucao,
    @Size(max = 120) String ocorrencia,
    LocalTime horaInicio,
    LocalTime horaFim,
    UUID ocorrenciaId
) {}
