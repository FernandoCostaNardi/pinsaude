package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.Especialidade;
import br.com.pinsaude.onboarding.domain.Medico;
import br.com.pinsaude.onboarding.domain.MedicoCrm;
import br.com.pinsaude.onboarding.domain.MedicoEspecialidade;
import br.com.pinsaude.onboarding.dto.CrmRequest;
import br.com.pinsaude.onboarding.dto.CrmResponse;
import br.com.pinsaude.onboarding.dto.EspecialidadeResponse;
import br.com.pinsaude.onboarding.repository.EspecialidadeRepository;
import br.com.pinsaude.onboarding.repository.MedicoCrmRepository;
import br.com.pinsaude.onboarding.repository.MedicoEspecialidadeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CRMs (vários por médico, um por UF) e especialidades (uma ou mais por médico).
 * medicos.crm/crm_uf espelham o CRM principal e medicos.especialidade espelha a primeira
 * especialidade — Portal, listagens, frequências e produção leem esses campos.
 */
@Service
public class RegistroProfissionalService {

    private final MedicoCrmRepository crmRepo;
    private final MedicoEspecialidadeRepository medicoEspecialidadeRepo;
    private final EspecialidadeRepository especialidadeRepo;

    public RegistroProfissionalService(
            MedicoCrmRepository crmRepo,
            MedicoEspecialidadeRepository medicoEspecialidadeRepo,
            EspecialidadeRepository especialidadeRepo) {
        this.crmRepo = crmRepo;
        this.medicoEspecialidadeRepo = medicoEspecialidadeRepo;
        this.especialidadeRepo = especialidadeRepo;
    }

    // ---- CRMs ----

    /**
     * Normaliza (número sem espaços, UF maiúscula), garante uma UF por médico e exatamente um
     * principal (o primeiro da lista quando nenhum vier marcado) e recusa CRM/UF que já pertence
     * a outro médico. Não grava nada — chamar antes de salvar o médico.
     */
    public List<CrmRequest> validarCrms(List<CrmRequest> crms, UUID medicoAtualId) {
        if (crms == null || crms.isEmpty()) return List.of();

        Set<String> ufs = new HashSet<>();
        List<CrmRequest> normalizados = new ArrayList<>();
        boolean principalDefinido = false;
        for (CrmRequest c : crms) {
            String numero = c.numero().trim();
            String uf = c.uf().trim().toUpperCase();
            if (!ufs.add(uf)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Só é possível ter um CRM por UF (UF repetida: " + uf + ")");
            }
            boolean principal = c.principal() && !principalDefinido;
            principalDefinido |= principal;
            normalizados.add(new CrmRequest(numero, uf, principal));
        }
        if (!principalDefinido) {
            CrmRequest primeiro = normalizados.get(0);
            normalizados.set(0, new CrmRequest(primeiro.numero(), primeiro.uf(), true));
        }

        for (CrmRequest c : normalizados) {
            crmRepo.findByNumeroAndUf(c.numero(), c.uf())
                .filter(existente -> !existente.getMedicoId().equals(medicoAtualId))
                .ifPresent(existente -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Já existe um médico com o CRM " + c.numero() + "/" + c.uf());
                });
        }
        return normalizados;
    }

    /** Substitui os CRMs do médico pela lista (já validada) e atualiza o espelho em medicos. */
    public void gravarCrms(Medico medico, List<CrmRequest> crmsValidados) {
        crmRepo.deleteAllByMedicoId(medico.getId());
        medico.setCrm(null);
        medico.setCrmUf(null);
        for (CrmRequest c : crmsValidados) {
            crmRepo.save(new MedicoCrm(medico.getId(), c.numero(), c.uf(), c.principal()));
            if (c.principal()) {
                medico.setCrm(c.numero());
                medico.setCrmUf(c.uf());
            }
        }
    }

    /**
     * Usado pela candidatura pública, que só conhece um CRM: troca o principal e mantém os
     * demais CRMs do médico (exceto um da mesma UF, que é substituído).
     */
    public void definirCrmPrincipal(Medico medico, String numero, String uf) {
        List<CrmRequest> lista = new ArrayList<>();
        lista.add(new CrmRequest(numero, uf, true));
        for (MedicoCrm c : crmRepo.findByMedicoIdOrderByPrincipalDescUfAsc(medico.getId())) {
            if (!c.isPrincipal() && !c.getUf().equalsIgnoreCase(uf)) {
                lista.add(new CrmRequest(c.getNumero(), c.getUf(), false));
            }
        }
        gravarCrms(medico, validarCrms(lista, medico.getId()));
    }

    public List<CrmResponse> listarCrms(UUID medicoId) {
        return crmRepo.findByMedicoIdOrderByPrincipalDescUfAsc(medicoId).stream()
            .map(CrmResponse::from)
            .toList();
    }

    // ---- Especialidades ----

    public List<EspecialidadeResponse> listarCatalogo() {
        return especialidadeRepo.findAllByAtivoTrueOrderByNomeAsc().stream()
            .map(EspecialidadeResponse::from)
            .toList();
    }

    /** Substitui as especialidades do médico (na ordem recebida) e atualiza o espelho em medicos. */
    public void gravarEspecialidades(Medico medico, List<UUID> especialidadeIds) {
        List<UUID> ids = especialidadeIds == null ? List.of()
            : new ArrayList<>(new LinkedHashSet<>(especialidadeIds));
        Map<UUID, Especialidade> porId = especialidadeRepo.findAllById(ids).stream()
            .collect(Collectors.toMap(Especialidade::getId, Function.identity()));
        for (UUID id : ids) {
            if (!porId.containsKey(id)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Especialidade não encontrada: " + id);
            }
        }

        medicoEspecialidadeRepo.deleteAllByMedicoId(medico.getId());
        short ordem = 1;
        for (UUID id : ids) {
            medicoEspecialidadeRepo.save(new MedicoEspecialidade(medico.getId(), id, ordem++));
        }
        medico.setEspecialidade(ids.isEmpty() ? null : porId.get(ids.get(0)).getNome());
    }

    public List<EspecialidadeResponse> listarEspecialidades(UUID medicoId) {
        List<MedicoEspecialidade> vinculos = medicoEspecialidadeRepo.findByMedicoIdOrderByOrdemAsc(medicoId);
        Map<UUID, Especialidade> porId = especialidadeRepo.findAllById(
                vinculos.stream().map(MedicoEspecialidade::getEspecialidadeId).toList()).stream()
            .collect(Collectors.toMap(Especialidade::getId, Function.identity()));
        return vinculos.stream()
            .map(v -> porId.get(v.getEspecialidadeId()))
            .filter(java.util.Objects::nonNull)
            .map(EspecialidadeResponse::from)
            .toList();
    }
}
