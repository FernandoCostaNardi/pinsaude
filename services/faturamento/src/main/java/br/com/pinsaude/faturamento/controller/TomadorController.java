package br.com.pinsaude.faturamento.controller;

import br.com.pinsaude.faturamento.dto.MedicoTomadorRequest;
import br.com.pinsaude.faturamento.dto.MedicoTomadorResponse;
import br.com.pinsaude.faturamento.dto.ReceitaFederalResponse;
import br.com.pinsaude.faturamento.dto.TomadorEmpresaRequest;
import br.com.pinsaude.faturamento.dto.TomadorEmpresaResponse;
import br.com.pinsaude.faturamento.dto.TomadorAliquotaRequest;
import br.com.pinsaude.faturamento.dto.TomadorAliquotaResponse;
import br.com.pinsaude.faturamento.dto.TomadorCnaeRequest;
import br.com.pinsaude.faturamento.dto.TomadorCnaeResponse;
import br.com.pinsaude.faturamento.dto.TomadorGrupoFaturamentoRequest;
import br.com.pinsaude.faturamento.dto.TomadorGrupoFaturamentoResponse;
import br.com.pinsaude.faturamento.dto.TomadorModalidadeRequest;
import br.com.pinsaude.faturamento.dto.TomadorModalidadeResponse;
import br.com.pinsaude.faturamento.dto.TomadorHorarioPadraoRequest;
import br.com.pinsaude.faturamento.dto.TomadorHorarioPadraoResponse;
import br.com.pinsaude.faturamento.dto.TomadorOcorrenciaRequest;
import br.com.pinsaude.faturamento.dto.TomadorOcorrenciaResponse;
import br.com.pinsaude.faturamento.dto.TomadorRequest;
import br.com.pinsaude.faturamento.dto.TomadorResponse;
import br.com.pinsaude.faturamento.dto.TomadorGrupoSetorRequest;
import br.com.pinsaude.faturamento.dto.TomadorServicoOperacionalRequest;
import br.com.pinsaude.faturamento.dto.TomadorServicoOperacionalResponse;
import br.com.pinsaude.faturamento.dto.TomadorServicoRequest;
import br.com.pinsaude.faturamento.dto.TomadorServicoResponse;
import br.com.pinsaude.faturamento.service.TomadorService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/tomadores")
public class TomadorController {

    private final TomadorService service;

    public TomadorController(TomadorService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil','medico')")
    public ResponseEntity<List<TomadorResponse>> buscar(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) UUID medicoId,
            @RequestParam(required = false) UUID empresaId) {
        return ResponseEntity.ok(service.buscar(q, medicoId, empresaId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil','medico')")
    public ResponseEntity<TomadorResponse> buscarPorId(@PathVariable UUID id) {
        return ResponseEntity.ok(service.buscarPorId(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorResponse> criar(@Valid @RequestBody TomadorRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criar(req));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorResponse> atualizar(
            @PathVariable UUID id,
            @Valid @RequestBody TomadorRequest req) {
        return ResponseEntity.ok(service.atualizar(id, req));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        service.deletar(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/receita/{cnpj}")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<ReceitaFederalResponse> consultarReceita(@PathVariable String cnpj) {
        Optional<ReceitaFederalResponse> resultado = service.consultarReceita(cnpj);
        return resultado.map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ─── Alíquotas por tomador ────────────────────────────────────────────────

    @GetMapping("/{id}/aliquotas")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil')")
    public ResponseEntity<List<TomadorAliquotaResponse>> listarAliquotas(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listarAliquotas(id));
    }

    @PostMapping("/{id}/aliquotas")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorAliquotaResponse> salvarAliquota(
            @PathVariable UUID id,
            @Valid @RequestBody TomadorAliquotaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.salvarAliquota(id, req));
    }

    @DeleteMapping("/{id}/aliquotas/{aliquotaId}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<Void> removerAliquota(
            @PathVariable UUID id,
            @PathVariable UUID aliquotaId) {
        service.removerAliquota(id, aliquotaId);
        return ResponseEntity.noContent().build();
    }

    // ─── CNAEs por tomador ────────────────────────────────────────────────────

    @GetMapping("/{id}/cnaes")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil','medico')")
    public ResponseEntity<List<TomadorCnaeResponse>> listarCnaes(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listarCnaes(id));
    }

    @PostMapping("/{id}/cnaes")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorCnaeResponse> adicionarCnae(
            @PathVariable UUID id,
            @Valid @RequestBody TomadorCnaeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adicionarCnae(id, req));
    }

    @DeleteMapping("/{id}/cnaes/{cnaeId}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<Void> removerCnae(
            @PathVariable UUID id,
            @PathVariable UUID cnaeId) {
        service.removerCnae(id, cnaeId);
        return ResponseEntity.noContent().build();
    }

    // ─── Serviços por tomador ─────────────────────────────────────────────────

    @GetMapping("/{id}/servicos")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil','medico')")
    public ResponseEntity<List<TomadorServicoResponse>> listarServicos(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listarServicos(id));
    }

    @PostMapping("/{id}/servicos")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorServicoResponse> adicionarServico(
            @PathVariable UUID id,
            @Valid @RequestBody TomadorServicoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adicionarServico(id, req));
    }

    @DeleteMapping("/{id}/servicos/{vinculoId}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<Void> removerServico(
            @PathVariable UUID id,
            @PathVariable UUID vinculoId) {
        service.removerServico(id, vinculoId);
        return ResponseEntity.noContent().build();
    }

    // ─── Grupos de faturamento ────────────────────────────────────────────────

    @GetMapping("/{id}/grupos")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil','medico')")
    public ResponseEntity<List<TomadorGrupoFaturamentoResponse>> listarGrupos(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listarGrupos(id));
    }

    @PostMapping("/{id}/grupos")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorGrupoFaturamentoResponse> criarGrupo(
            @PathVariable UUID id,
            @Valid @RequestBody TomadorGrupoFaturamentoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criarGrupo(id, req));
    }

    @PutMapping("/{id}/grupos/{grupoId}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorGrupoFaturamentoResponse> atualizarGrupo(
            @PathVariable UUID id,
            @PathVariable UUID grupoId,
            @Valid @RequestBody TomadorGrupoFaturamentoRequest req) {
        return ResponseEntity.ok(service.atualizarGrupo(id, grupoId, req));
    }

    @DeleteMapping("/{id}/grupos/{grupoId}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<Void> removerGrupo(
            @PathVariable UUID id,
            @PathVariable UUID grupoId) {
        service.removerGrupo(id, grupoId);
        return ResponseEntity.noContent().build();
    }

    // ─── Vínculo Grupo ↔ Setor (N:N) — permite reutilizar o mesmo setor em vários grupos ──────

    @GetMapping("/{id}/grupos/{grupoId}/setores")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil','medico')")
    public ResponseEntity<List<TomadorServicoOperacionalResponse>> listarSetoresDoGrupo(
            @PathVariable UUID id,
            @PathVariable UUID grupoId) {
        return ResponseEntity.ok(service.listarSetoresDoGrupo(id, grupoId));
    }

    @PostMapping("/{id}/grupos/{grupoId}/setores")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorServicoOperacionalResponse> adicionarSetorAoGrupo(
            @PathVariable UUID id,
            @PathVariable UUID grupoId,
            @Valid @RequestBody TomadorGrupoSetorRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adicionarSetorAoGrupo(id, grupoId, req));
    }

    @DeleteMapping("/{id}/grupos/{grupoId}/setores/{setorId}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<Void> removerSetorDoGrupo(
            @PathVariable UUID id,
            @PathVariable UUID grupoId,
            @PathVariable UUID setorId) {
        service.removerSetorDoGrupo(id, grupoId, setorId);
        return ResponseEntity.noContent().build();
    }

    // ─── Modalidades ──────────────────────────────────────────────────────────

    @GetMapping("/{id}/modalidades")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil','medico')")
    public ResponseEntity<List<TomadorModalidadeResponse>> listarModalidades(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listarModalidades(id));
    }

    @PostMapping("/{id}/modalidades")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorModalidadeResponse> criarModalidade(
            @PathVariable UUID id,
            @Valid @RequestBody TomadorModalidadeRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criarModalidade(id, req));
    }

    @PutMapping("/{id}/modalidades/{modalidadeId}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorModalidadeResponse> atualizarModalidade(
            @PathVariable UUID id,
            @PathVariable UUID modalidadeId,
            @Valid @RequestBody TomadorModalidadeRequest req) {
        return ResponseEntity.ok(service.atualizarModalidade(id, modalidadeId, req));
    }

    @DeleteMapping("/{id}/modalidades/{modalidadeId}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<Void> removerModalidade(
            @PathVariable UUID id,
            @PathVariable UUID modalidadeId) {
        service.removerModalidade(id, modalidadeId);
        return ResponseEntity.noContent().build();
    }

    // ─── Serviços operacionais (setores) ──────────────────────────────────────

    @GetMapping("/{id}/servicos-operacionais")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil','medico')")
    public ResponseEntity<List<TomadorServicoOperacionalResponse>> listarServicosOperacionais(
            @PathVariable UUID id) {
        return ResponseEntity.ok(service.listarServicosOperacionais(id));
    }

    @PostMapping("/{id}/servicos-operacionais")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorServicoOperacionalResponse> criarServicoOperacional(
            @PathVariable UUID id,
            @Valid @RequestBody TomadorServicoOperacionalRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(service.criarServicoOperacional(id, req));
    }

    @PutMapping("/{id}/servicos-operacionais/{servicoOperacionalId}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorServicoOperacionalResponse> atualizarServicoOperacional(
            @PathVariable UUID id,
            @PathVariable UUID servicoOperacionalId,
            @Valid @RequestBody TomadorServicoOperacionalRequest req) {
        return ResponseEntity.ok(service.atualizarServicoOperacional(id, servicoOperacionalId, req));
    }

    @DeleteMapping("/{id}/servicos-operacionais/{servicoOperacionalId}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<Void> removerServicoOperacional(
            @PathVariable UUID id,
            @PathVariable UUID servicoOperacionalId) {
        service.removerServicoOperacional(id, servicoOperacionalId);
        return ResponseEntity.noContent().build();
    }

    // ─── Médicos alocados ao tomador (EPIC-15) ────────────────────────────────

    @GetMapping("/{id}/medicos")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil','medico')")
    public ResponseEntity<List<MedicoTomadorResponse>> listarMedicos(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listarMedicos(id));
    }

    @PostMapping("/{id}/medicos")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<MedicoTomadorResponse> adicionarMedico(
            @PathVariable UUID id,
            @Valid @RequestBody MedicoTomadorRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adicionarMedico(id, req));
    }

    @DeleteMapping("/{id}/medicos/{medicoId}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<Void> removerMedico(
            @PathVariable UUID id,
            @PathVariable UUID medicoId) {
        service.removerMedico(id, medicoId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/empresas")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil','medico')")
    public ResponseEntity<List<TomadorEmpresaResponse>> listarEmpresas(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listarEmpresas(id));
    }

    @PostMapping("/{id}/empresas")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorEmpresaResponse> adicionarEmpresa(
            @PathVariable UUID id,
            @Valid @RequestBody TomadorEmpresaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.adicionarEmpresa(id, req));
    }

    @DeleteMapping("/{id}/empresas/{empresaId}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<Void> removerEmpresa(
            @PathVariable UUID id,
            @PathVariable UUID empresaId) {
        service.removerEmpresa(id, empresaId);
        return ResponseEntity.noContent().build();
    }

    // ─── Ocorrências pré-cadastradas com valor ──────────────────────────────────

    @GetMapping("/{id}/ocorrencias")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil','medico')")
    public ResponseEntity<List<TomadorOcorrenciaResponse>> listarOcorrencias(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listarOcorrencias(id));
    }

    @PostMapping("/{id}/ocorrencias")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorOcorrenciaResponse> criarOcorrencia(
            @PathVariable UUID id,
            @Valid @RequestBody TomadorOcorrenciaRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criarOcorrencia(id, req));
    }

    @PutMapping("/{id}/ocorrencias/{ocorrenciaId}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorOcorrenciaResponse> atualizarOcorrencia(
            @PathVariable UUID id,
            @PathVariable UUID ocorrenciaId,
            @Valid @RequestBody TomadorOcorrenciaRequest req) {
        return ResponseEntity.ok(service.atualizarOcorrencia(id, ocorrenciaId, req));
    }

    @DeleteMapping("/{id}/ocorrencias/{ocorrenciaId}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<Void> removerOcorrencia(
            @PathVariable UUID id,
            @PathVariable UUID ocorrenciaId) {
        service.removerOcorrencia(id, ocorrenciaId);
        return ResponseEntity.noContent().build();
    }

    // ─── Preenchimento rápido de turno ───────────────────────────────────────────

    @GetMapping("/{id}/turnos-padrao")
    @PreAuthorize("hasAnyRole('operacao','gestao','financeiro','contabil','medico')")
    public ResponseEntity<List<TomadorHorarioPadraoResponse>> listarHorariosPadrao(@PathVariable UUID id) {
        return ResponseEntity.ok(service.listarHorariosPadrao(id));
    }

    @PostMapping("/{id}/turnos-padrao")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorHorarioPadraoResponse> criarHorarioPadrao(
            @PathVariable UUID id,
            @Valid @RequestBody TomadorHorarioPadraoRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.criarHorarioPadrao(id, req));
    }

    @PutMapping("/{id}/turnos-padrao/{horarioId}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<TomadorHorarioPadraoResponse> atualizarHorarioPadrao(
            @PathVariable UUID id,
            @PathVariable UUID horarioId,
            @Valid @RequestBody TomadorHorarioPadraoRequest req) {
        return ResponseEntity.ok(service.atualizarHorarioPadrao(id, horarioId, req));
    }

    @DeleteMapping("/{id}/turnos-padrao/{horarioId}")
    @PreAuthorize("hasAnyRole('operacao','gestao')")
    public ResponseEntity<Void> removerHorarioPadrao(
            @PathVariable UUID id,
            @PathVariable UUID horarioId) {
        service.removerHorarioPadrao(id, horarioId);
        return ResponseEntity.noContent().build();
    }
}
