package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.CronogramaReforma;
import br.com.pinsaude.fiscal.domain.ParametroFiscal;
import br.com.pinsaude.fiscal.domain.RegimeTributario;
import br.com.pinsaude.fiscal.domain.TipoTributo;
import br.com.pinsaude.fiscal.dto.AuditoriaCenario;
import br.com.pinsaude.fiscal.dto.CalculoFiscalRequest;
import br.com.pinsaude.fiscal.dto.CalculoFiscalResponse;
import br.com.pinsaude.fiscal.repository.CronogramaReformaRepository;
import br.com.pinsaude.fiscal.repository.ParametroFiscalRepository;
import br.com.pinsaude.fiscal.repository.ParametrosFiscaisRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Motor de cálculo fiscal por competência.
 * Stateless e thread-safe: não mantém estado de instância mutável.
 *
 * Quatro cenários:
 *   A — Tomador PJ com retenção federal (IR/CSLL/PIS/COFINS retidos pelo tomador)
 *   B — Tomador PJ sem retenção (Pin paga tributos por guia própria)
 *   C — Tomador PF com equiparação hospitalar (nota com tributos zerados)
 *   D — Tomador PF sem equiparação (IR na fonte pelo tomador PF)
 *
 * Regime por competência (CBS-13): o parâmetro fiscal vigente com ibs_cbs_ativo e homologado
 * liga o regime IBS_CBS. Nele PIS/COFINS somem, CBS e IBS aparecem (alíquota do ano no
 * cronograma × (1 − redução do serviço)), ISS é multiplicado pelo fator do ano e IR/CSLL seguem.
 *
 * Invariante: médico SEMPRE recebe 85% do valorBruto (valorBruto − taxaPin).
 * Tributos são custo fiscal da Pin Saúde, jamais deduzidos do médico.
 */
@Service
public class MotorFiscalService {

    private static final BigDecimal PERC_PIN = new BigDecimal("0.15");

    private final ParametrosFiscaisRepository pfRepo;
    private final ParametroFiscalRepository p1Repo;
    private final CronogramaReformaRepository cronogramaRepo;
    private final AuditoriaFiscalService auditoriaService;

    public MotorFiscalService(ParametrosFiscaisRepository pfRepo,
                               ParametroFiscalRepository p1Repo,
                               CronogramaReformaRepository cronogramaRepo,
                               AuditoriaFiscalService auditoriaService) {
        this.pfRepo = pfRepo;
        this.p1Repo = p1Repo;
        this.cronogramaRepo = cronogramaRepo;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public CalculoFiscalResponse calcular(String cnpjId, CalculoFiscalRequest req) {
        AliquotasCenario aliquotas = carregarAliquotas(cnpjId, req);
        String cenario = derivarCenario(req);
        long bruto = req.valorBruto();

        long taxaPin = aplicar(bruto, PERC_PIN);
        long valorLiquidoMedico = bruto - taxaPin;

        // ISS: no regime IBS_CBS, multiplicado pelo fator do ano (extinção gradual 2029–2033).
        BigDecimal aliqIssAno = aliquotas.iss() == null ? null : aliquotas.iss().multiply(aliquotas.fatorIss());
        long issCalc    = aplicar(bruto, aliqIssAno);
        // IR e CSLL continuam com a reforma. PIS/COFINS já vêm zerados no regime IBS_CBS.
        long irCalc     = aplicar(bruto, aliquotas.ir());
        long csllCalc   = aplicar(bruto, aliquotas.csll());
        long pisCalc    = aplicar(bruto, aliquotas.pis());
        long cofinsCalc = aplicar(bruto, aliquotas.cofins());

        long valorIss, valorIr, valorCsll, valorPis, valorCofins, totalRetencoes;

        switch (cenario) {
            case "A" -> {
                // PJ com retenção: ISS destacado + federais retidos pelo tomador
                valorIss    = issCalc;
                valorIr     = irCalc;
                valorCsll   = csllCalc;
                valorPis    = pisCalc;
                valorCofins = cofinsCalc;
                long retFederal = irCalc + csllCalc + pisCalc + cofinsCalc;
                long retIss = req.indicadorRetencaoIss() ? issCalc : 0;
                totalRetencoes = retFederal + retIss;
            }
            case "B" -> {
                // PJ sem retenção: todos calculados, Pin paga por guia
                valorIss = issCalc; valorIr = irCalc; valorCsll = csllCalc;
                valorPis = pisCalc; valorCofins = cofinsCalc;
                totalRetencoes = 0;
            }
            case "C" -> {
                // PF equiparado: nota com tributos zerados, Pin apura internamente
                valorIss = 0; valorIr = 0; valorCsll = 0; valorPis = 0; valorCofins = 0;
                totalRetencoes = 0;
            }
            case "D" -> {
                // PF sem equiparação: IR na fonte pelo tomador PF; ISS/CSLL/PIS/COFINS não se aplicam
                valorIss = 0; valorIr = irCalc; valorCsll = 0; valorPis = 0; valorCofins = 0;
                totalRetencoes = irCalc;
            }
            default -> throw new IllegalStateException("Cenário não mapeado: " + cenario);
        }

        // CBS/IBS: débito próprio da Pin em todos os cenários (retenção só existe para tomador
        // público, fora de escopo), por isso nunca entram em totalRetencoes.
        boolean porFora = Boolean.TRUE.equals(req.cbsIbsPorFora());
        long baseIbsCbs = bruto;
        long valorCbs = 0, valorIbs = 0;
        if (aliquotas.ibsCbsAtivo()) {
            BigDecimal somaAliq = aliquotas.aliqCbs().add(aliquotas.aliqIbs());
            if (!porFora && somaAliq.signum() > 0) {
                // Dentro do preço: o bruto já contém CBS/IBS → base = bruto / (1 + alíquotas)
                baseIbsCbs = BigDecimal.valueOf(bruto)
                    .divide(BigDecimal.ONE.add(somaAliq), 0, RoundingMode.HALF_UP)
                    .longValue();
            }
            valorCbs = aplicar(baseIbsCbs, aliquotas.aliqCbs());
            valorIbs = aplicar(baseIbsCbs, aliquotas.aliqIbs());
        }
        long valorTotalNota = porFora ? bruto + valorCbs + valorIbs : bruto;

        long totalTributos = valorIss + valorIr + valorCsll + valorPis + valorCofins + valorCbs + valorIbs;
        // Por fora, CBS/IBS são pagos pelo tomador por cima do bruto e não saem da cota da Pin.
        long tributosDaPin = porFora ? totalTributos - valorCbs - valorIbs : totalTributos;
        long resultadoPin  = taxaPin - tributosDaPin;

        AuditoriaCenario auditoria = auditoriaService.registrar(req.competencia(), cenario, aliquotas);

        return new CalculoFiscalResponse(
            bruto, taxaPin,
            valorIss, valorIr, valorCsll, valorPis, valorCofins,
            totalRetencoes, totalTributos,
            valorLiquidoMedico, resultadoPin,
            cenario, req.competencia(), auditoria,
            aliquotas.regime().name(),
            valorCbs, valorIbs,
            aliquotas.aliqCbs(), aliquotas.aliqIbs(), aliquotas.fatorIss(),
            aliquotas.ibsCbsAtivo() ? req.cstIbsCbs() : null,
            aliquotas.ibsCbsAtivo() ? req.cClassTrib() : null,
            porFora, baseIbsCbs, valorTotalNota
        );
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String derivarCenario(CalculoFiscalRequest req) {
        if (req.tomadorPj()) {
            return req.indicadorRetencaoFederal() ? "A" : "B";
        }
        return req.equiparacaoHospitalar() ? "C" : "D";
    }

    private long aplicar(long base, BigDecimal aliquota) {
        if (aliquota == null || aliquota.compareTo(BigDecimal.ZERO) == 0) return 0;
        return BigDecimal.valueOf(base)
            .multiply(aliquota)
            .setScale(0, RoundingMode.HALF_UP)
            .longValue();
    }

    private AliquotasCenario carregarAliquotas(String cnpjId, CalculoFiscalRequest req) {
        String competencia = req.competencia();
        Map<String, BigDecimal> overrides = req.aliquotasOverride();
        // 1. Override do tomador tem prioridade; depois busca da empresa; depois fallback V1
        Optional<BigDecimal> iss   = override(overrides, "ISS")
            .or(() -> buscarAliquotaPerTributo(cnpjId, TipoTributo.ISS,    competencia));
        Optional<BigDecimal> ir    = override(overrides, "IR")
            .or(() -> buscarAliquotaPerTributo(cnpjId, TipoTributo.IR,     competencia));
        Optional<BigDecimal> csll  = override(overrides, "CSLL")
            .or(() -> buscarAliquotaPerTributo(cnpjId, TipoTributo.CSLL,   competencia));
        Optional<BigDecimal> pis   = override(overrides, "PIS")
            .or(() -> buscarAliquotaPerTributo(cnpjId, TipoTributo.PIS,    competencia));
        Optional<BigDecimal> cofins = override(overrides, "COFINS")
            .or(() -> buscarAliquotaPerTributo(cnpjId, TipoTributo.COFINS, competencia));

        // 2. Regime geral (parametro_fiscal V1): fallback das alíquotas e chave do regime
        LocalDate dataRef = competenciaToDate(competencia);
        Optional<ParametroFiscal> regimeGeral =
            p1Repo.findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(cnpjId, dataRef);

        BigDecimal aliqIss    = iss.orElse(regimeGeral.map(ParametroFiscal::getAliqIss).orElse(BigDecimal.ZERO));
        BigDecimal aliqIr     = ir.orElse(regimeGeral.map(ParametroFiscal::getAliqIr).orElse(BigDecimal.ZERO));
        BigDecimal aliqCsll   = csll.orElse(regimeGeral.map(ParametroFiscal::getAliqCsll).orElse(BigDecimal.ZERO));

        // A chave só vale ligada e homologada pela contabilidade (CBS-10).
        boolean regimeNovo = regimeGeral.map(pf -> pf.isIbsCbsAtivo() && pf.isHomologado()).orElse(false);
        if (!regimeNovo) {
            return new AliquotasCenario(
                aliqIss, aliqIr, aliqCsll,
                pis.orElse(regimeGeral.map(ParametroFiscal::getAliqPis).orElse(BigDecimal.ZERO)),
                cofins.orElse(regimeGeral.map(ParametroFiscal::getAliqCofins).orElse(BigDecimal.ZERO)),
                RegimeTributario.LEGADO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE
            );
        }

        // 3. Regime IBS_CBS: alíquotas do ano no cronograma × (1 − redução do serviço).
        short ano = (short) dataRef.getYear();
        CronogramaReforma cron = cronogramaRepo.findById(ano).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Cronograma da reforma não cadastrado para " + ano));
        if (cron.getAliqCbs() == null || cron.getAliqIbsUf() == null || cron.getAliqIbsMun() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Alíquotas de CBS/IBS de " + ano + " ainda não foram fixadas no cronograma da reforma");
        }
        BigDecimal reducao = req.reducaoIbsCbs() != null ? req.reducaoIbsCbs() : BigDecimal.ZERO;
        BigDecimal fatorReducao = BigDecimal.ONE.subtract(reducao);
        BigDecimal aliqCbs = cron.getAliqCbs().multiply(fatorReducao).setScale(8, RoundingMode.HALF_UP);
        BigDecimal aliqIbs = cron.getAliqIbsUf().add(cron.getAliqIbsMun())
            .multiply(fatorReducao).setScale(8, RoundingMode.HALF_UP);

        return new AliquotasCenario(
            aliqIss, aliqIr, aliqCsll,
            BigDecimal.ZERO, BigDecimal.ZERO,   // PIS/COFINS extintos
            RegimeTributario.IBS_CBS, aliqCbs, aliqIbs, cron.getFatorIss()
        );
    }

    private Optional<BigDecimal> override(Map<String, BigDecimal> overrides, String tipo) {
        if (overrides == null) return Optional.empty();
        BigDecimal val = overrides.get(tipo);
        return val != null ? Optional.of(val) : Optional.empty();
    }

    private Optional<BigDecimal> buscarAliquotaPerTributo(String cnpjId, TipoTributo tipo, String comp) {
        List<br.com.pinsaude.fiscal.domain.ParametrosFiscais> lista =
            pfRepo.findVigenteParaTributo(cnpjId, tipo, comp, PageRequest.of(0, 1));
        return lista.isEmpty() ? Optional.empty() : Optional.of(lista.get(0).getValorAliquota());
    }

    private LocalDate competenciaToDate(String competencia) {
        String[] parts = competencia.split("-");
        return LocalDate.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), 1);
    }
}
