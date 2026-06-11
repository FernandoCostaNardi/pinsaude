package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.AliquotaCompetencia;
import br.com.pinsaude.onboarding.domain.ConfiguracaoFiscal;
import br.com.pinsaude.onboarding.dto.AliquotaCompetenciaRequest;
import br.com.pinsaude.onboarding.dto.AliquotaCompetenciaResponse;
import br.com.pinsaude.onboarding.dto.ConfiguracaoFiscalRequest;
import br.com.pinsaude.onboarding.dto.ConfiguracaoFiscalResponse;
import br.com.pinsaude.onboarding.repository.AliquotaCompetenciaRepository;
import br.com.pinsaude.onboarding.repository.ConfiguracaoFiscalRepository;
import br.com.pinsaude.onboarding.repository.EmpresaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
public class ConfiguracaoFiscalService {

    private final ConfiguracaoFiscalRepository configuracaoRepo;
    private final AliquotaCompetenciaRepository aliquotaRepo;
    private final EmpresaRepository empresaRepository;

    public ConfiguracaoFiscalService(
            ConfiguracaoFiscalRepository configuracaoRepo,
            AliquotaCompetenciaRepository aliquotaRepo,
            EmpresaRepository empresaRepository) {
        this.configuracaoRepo  = configuracaoRepo;
        this.aliquotaRepo      = aliquotaRepo;
        this.empresaRepository = empresaRepository;
    }

    public ConfiguracaoFiscalResponse buscar(UUID empresaId) {
        verificarEmpresa(empresaId);
        ConfiguracaoFiscal config = configuracaoRepo.findByEmpresaId(empresaId)
            .orElseGet(() -> configVazia(empresaId));
        List<AliquotaCompetenciaResponse> historico = aliquotaRepo
            .findByEmpresaIdOrderByCompetenciaDesc(empresaId)
            .stream().map(AliquotaCompetenciaResponse::from).toList();
        return ConfiguracaoFiscalResponse.from(config, historico);
    }

    @Transactional
    public ConfiguracaoFiscalResponse salvar(UUID empresaId, ConfiguracaoFiscalRequest request, String usuario) {
        verificarEmpresa(empresaId);

        ConfiguracaoFiscal config = configuracaoRepo.findByEmpresaId(empresaId)
            .orElseGet(() -> {
                ConfiguracaoFiscal c = new ConfiguracaoFiscal();
                c.setEmpresaId(empresaId);
                return c;
            });
        config.setCnaeCodigo(request.cnaeCodigo());
        config.setCnaeDescricao(request.cnaeDescricao());
        config.setCodigoLc116(request.codigoLc116());
        config.setIndicadorEquiparacaoHospitalar(request.indicadorEquiparacaoHospitalar());
        config.setVencimentoCertificadoA1(request.vencimentoCertificadoA1());
        config.setUpdatedBy(usuario);
        configuracaoRepo.save(config);

        AliquotaCompetenciaRequest ar = request.aliquota();
        String mesAtual = YearMonth.now().toString();
        if (ar.competencia().compareTo(mesAtual) < 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Não é permitido alterar alíquotas de competências passadas. Competência mínima: " + mesAtual);
        }

        AliquotaCompetencia aliquota = aliquotaRepo
            .findByEmpresaIdAndCompetencia(empresaId, ar.competencia())
            .orElseGet(() -> {
                AliquotaCompetencia a = new AliquotaCompetencia();
                a.setEmpresaId(empresaId);
                a.setCompetencia(ar.competencia());
                a.setCreatedBy(usuario);
                return a;
            });
        aliquota.setAliquotaIss(ar.iss());
        aliquota.setAliquotaIr(ar.ir());
        aliquota.setAliquotaCsll(ar.csll());
        aliquota.setAliquotaPis(ar.pis());
        aliquota.setAliquotaCofins(ar.cofins());
        aliquota.setRegimePresuncao(ar.regimePresuncao());
        aliquotaRepo.save(aliquota);

        return buscar(empresaId);
    }

    private void verificarEmpresa(UUID empresaId) {
        empresaRepository.findByIdAndAtivoTrue(empresaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Empresa não encontrada: " + empresaId));
    }

    private ConfiguracaoFiscal configVazia(UUID empresaId) {
        ConfiguracaoFiscal c = new ConfiguracaoFiscal();
        c.setEmpresaId(empresaId);
        return c;
    }
}
