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
import java.util.ArrayList;
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
        // Tributos que o tomador retém na fonte (CBS-21): as telas marcam RETIDO/PRESTADORA por aqui.
        List<String> retidos = new ArrayList<>();

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
                if (req.indicadorRetencaoIss()) retidos.add("ISS");
                retidos.addAll(List.of("IR", "CSLL", "PIS", "COFINS"));
            }
            case "B" -> {
                // PJ sem retenção federal: todos calculados, Pin paga os federais por guia.
                // A retenção de ISS (substituição tributária municipal) independe da federal.
                valorIss = issCalc; valorIr = irCalc; valorCsll = csllCalc;
                valorPis = pisCalc; valorCofins = cofinsCalc;
                totalRetencoes = req.indicadorRetencaoIss() ? issCalc : 0;
                if (req.indicadorRetencaoIss()) retidos.add("ISS");
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
                retidos.add("IR");
            }
            default -> throw new IllegalStateException("Cenário não mapeado: " + cenario);
        }

        // CBS/IBS: débito próprio da Pin em todos os cenários (retenção só existe para tomador
        // público, fora de escopo), por isso nunca entram em totalRetencoes.
        boolean porFora = Boolean.TRUE.equals(req.cbsIbsPorFora());
        TributacaoIbsCbs ibsCbs = ibsCbs(aliquotas, bruto, porFora);
        // No LEGADO a resposta sempre trouxe a base igual ao bruto.
        long baseIbsCbs = ibsCbs.ibsCbs() ? ibsCbs.baseCalculo() : bruto;
        long valorCbs = ibsCbs.valorCbs();
        long valorIbs = ibsCbs.valorIbs();
        long valorTotalNota = porFora ? bruto + valorCbs + valorIbs : bruto;

        long totalTributos = valorIss + valorIr + valorCsll + valorPis + valorCofins + valorCbs + valorIbs;
        // Por fora, CBS/IBS são pagos pelo tomador por cima do bruto e não saem da cota da Pin.
        long tributosDaPin = porFora ? totalTributos - valorCbs - valorIbs : totalTributos;
        long resultadoPin  = taxaPin - tributosDaPin;

        // Só tributos com valor: PIS/COFINS zerados no regime novo não aparecem como retidos.
        List<String> retidosComValor = new ArrayList<>();
        for (String t : retidos) {
            if (valorDo(t, valorIss, valorIr, valorCsll, valorPis, valorCofins) > 0) retidosComValor.add(t);
        }

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
            porFora, baseIbsCbs, valorTotalNota,
            retidosComValor
        );
    }

    /**
     * CBS-41 — regime e CBS/IBS de uma nota pela mesma regra do motor, sem os tributos antigos.
     * A emissão usa isto para gravar o snapshot da reforma na nota a partir do parâmetro fiscal
     * da empresa (o regime não vem do navegador). Ano sem cronograma ou com alíquotas nulas → 422.
     */
    @Transactional(readOnly = true)
    public TributacaoIbsCbs tributacaoIbsCbs(String cnpjId, String competencia, long valorBruto,
                                             BigDecimal reducaoIbsCbs, Boolean cbsIbsPorFora) {
        var req = new CalculoFiscalRequest(valorBruto, competencia, true, false, false, false,
            null, reducaoIbsCbs, null, null, cbsIbsPorFora);
        return ibsCbs(carregarAliquotas(cnpjId, req), valorBruto, Boolean.TRUE.equals(cbsIbsPorFora));
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    /**
     * CBS/IBS: débito próprio da Pin em todos os cenários (retenção só existe para tomador público,
     * fora de escopo). Dentro do preço, base = bruto / (1 + CBS + IBS); por fora, base = bruto.
     * O IBS total é calculado sobre a soma das alíquotas e o municipal fica com a diferença, para
     * as partes somarem sempre o total (mesmo arredondamento das telas).
     */
    private TributacaoIbsCbs ibsCbs(AliquotasCenario aliquotas, long bruto, boolean porFora) {
        if (!aliquotas.ibsCbsAtivo()) {
            return TributacaoIbsCbs.legado(aliquotas.fatorIss());
        }
        BigDecimal somaAliq = aliquotas.aliqCbs().add(aliquotas.aliqIbs());
        long base = bruto;
        if (!porFora && somaAliq.signum() > 0) {
            base = BigDecimal.valueOf(bruto)
                .divide(BigDecimal.ONE.add(somaAliq), 0, RoundingMode.HALF_UP)
                .longValue();
        }
        long valorCbs = aplicar(base, aliquotas.aliqCbs());
        long valorIbs = aplicar(base, aliquotas.aliqIbs());
        long valorIbsUf = Math.min(aplicar(base, aliquotas.aliqIbsUf()), valorIbs);
        return new TributacaoIbsCbs(
            RegimeTributario.IBS_CBS, porFora, base,
            aliquotas.aliqCbs(), aliquotas.aliqIbs(), aliquotas.aliqIbsUf(), aliquotas.aliqIbsMun(),
            aliquotas.fatorIss(),
            valorCbs, valorIbs, valorIbsUf, valorIbs - valorIbsUf
        );
    }

    private static long valorDo(String tributo, long iss, long ir, long csll, long pis, long cofins) {
        return switch (tributo) {
            case "ISS" -> iss;
            case "IR" -> ir;
            case "CSLL" -> csll;
            case "PIS" -> pis;
            case "COFINS" -> cofins;
            default -> 0L;
        };
    }

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
        boolean regimeNovo = regimeGeral.map(ParametroFiscal::isRegimeIbsCbsVigente).orElse(false);
        if (!regimeNovo) {
            return new AliquotasCenario(
                aliqIss, aliqIr, aliqCsll,
                pis.orElse(regimeGeral.map(ParametroFiscal::getAliqPis).orElse(BigDecimal.ZERO)),
                cofins.orElse(regimeGeral.map(ParametroFiscal::getAliqCofins).orElse(BigDecimal.ZERO)),
                RegimeTributario.LEGADO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE,
                BigDecimal.ZERO, BigDecimal.ZERO
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
        BigDecimal aliqIbsUf = cron.getAliqIbsUf().multiply(fatorReducao).setScale(8, RoundingMode.HALF_UP);
        BigDecimal aliqIbsMun = aliqIbs.subtract(aliqIbsUf);

        return new AliquotasCenario(
            aliqIss, aliqIr, aliqCsll,
            BigDecimal.ZERO, BigDecimal.ZERO,   // PIS/COFINS extintos
            RegimeTributario.IBS_CBS, aliqCbs, aliqIbs, cron.getFatorIss(),
            aliqIbsUf, aliqIbsMun
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
