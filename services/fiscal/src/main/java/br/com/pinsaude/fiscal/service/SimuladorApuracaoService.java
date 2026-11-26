package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.apuracao.AliquotasReformaAno;
import br.com.pinsaude.fiscal.apuracao.CalculadoraApuracao;
import br.com.pinsaude.fiscal.apuracao.MesApuracao;
import br.com.pinsaude.fiscal.apuracao.ResultadoApuracao;
import br.com.pinsaude.fiscal.domain.CronogramaReforma;
import br.com.pinsaude.fiscal.domain.RegimeTributario;
import br.com.pinsaude.fiscal.domain.RegraApuracao;
import br.com.pinsaude.fiscal.dto.SimulacaoApuracaoRequest;
import br.com.pinsaude.fiscal.repository.CronogramaReformaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.YearMonth;
import java.util.List;

/**
 * APU-00 — simulador da apuração: aplica a regra (vigente ou escolhida) a números informados,
 * para a contabilidade conferir as fórmulas antes de homologar. Não grava nada.
 */
@Service
public class SimuladorApuracaoService {

    private final RegraApuracaoService regras;
    private final RegimeFiscalService regimeFiscal;
    private final CronogramaReformaRepository cronogramaRepo;

    public SimuladorApuracaoService(RegraApuracaoService regras, RegimeFiscalService regimeFiscal,
                                    CronogramaReformaRepository cronogramaRepo) {
        this.regras = regras;
        this.regimeFiscal = regimeFiscal;
        this.cronogramaRepo = cronogramaRepo;
    }

    @Transactional(readOnly = true)
    public ResultadoApuracao simular(SimulacaoApuracaoRequest req) {
        String competencia = RegraApuracaoService.validarCompetencia(req.competencia());
        String cnpj = RegraApuracaoService.empresaDoToken()
            .orElseGet(() -> RegraApuracaoService.normalizar(req.cnpj()));

        RegraApuracao regra = req.regraId() != null
            ? regras.regraPorId(req.regraId())
            : regras.regraVigente(cnpj, competencia);

        RegimeTributario regime = req.regime() != null ? req.regime()
            : cnpj == null ? RegimeTributario.LEGADO
            : RegimeTributario.valueOf(regimeFiscal.regime(cnpj, competencia).regime());

        YearMonth comp = YearMonth.parse(competencia);
        List<MesApuracao> meses = req.meses().stream().map(m -> {
            YearMonth ym = YearMonth.parse(RegraApuracaoService.validarCompetencia(m.competencia()));
            if (ym.isAfter(comp) || ym.getYear() != comp.getYear()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mês " + ym + " fora do período: informe meses de " + comp.getYear() + " até " + comp);
            }
            return new MesApuracao(ym, m.receitaCompetenciaEquiparada(), m.receitaCompetenciaPadrao(),
                m.receitaCaixaEquiparada(), m.receitaCaixaPadrao(), m.retencoes(), m.cbsNotas(), m.ibsNotas());
        }).toList();

        return CalculadoraApuracao.calcular(regra, cnpj, regime, comp, meses, aliquotasReforma(comp.getYear()));
    }

    private AliquotasReformaAno aliquotasReforma(int ano) {
        return cronogramaRepo.findById((short) ano).map(SimuladorApuracaoService::toAliquotas)
            .orElse(AliquotasReformaAno.NENHUMA);
    }

    private static AliquotasReformaAno toAliquotas(CronogramaReforma c) {
        var ibs = c.getAliqIbsUf() == null || c.getAliqIbsMun() == null ? null : c.getAliqIbsUf().add(c.getAliqIbsMun());
        return new AliquotasReformaAno(c.getAliqCbs(), ibs, c.getFatorIss());
    }
}
