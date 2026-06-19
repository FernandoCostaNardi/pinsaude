package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.ParametroFiscal;
import br.com.pinsaude.fiscal.domain.TipoTributo;
import br.com.pinsaude.fiscal.dto.AuditoriaCenario;
import br.com.pinsaude.fiscal.dto.CalculoFiscalRequest;
import br.com.pinsaude.fiscal.dto.CalculoFiscalResponse;
import br.com.pinsaude.fiscal.repository.ParametroFiscalRepository;
import br.com.pinsaude.fiscal.repository.ParametrosFiscaisRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
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
 * Invariante: médico SEMPRE recebe 85% do valorBruto (valorBruto − taxaPin).
 * Tributos são custo fiscal da Pin Saúde, jamais deduzidos do médico.
 */
@Service
public class MotorFiscalService {

    private static final BigDecimal PERC_PIN = new BigDecimal("0.15");

    private final ParametrosFiscaisRepository pfRepo;
    private final ParametroFiscalRepository p1Repo;
    private final AuditoriaFiscalService auditoriaService;

    public MotorFiscalService(ParametrosFiscaisRepository pfRepo,
                               ParametroFiscalRepository p1Repo,
                               AuditoriaFiscalService auditoriaService) {
        this.pfRepo = pfRepo;
        this.p1Repo = p1Repo;
        this.auditoriaService = auditoriaService;
    }

    @Transactional(readOnly = true)
    public CalculoFiscalResponse calcular(String cnpjId, CalculoFiscalRequest req) {
        AliquotasCenario aliquotas = carregarAliquotas(cnpjId, req.competencia());
        String cenario = derivarCenario(req);

        long taxaPin = aplicar(req.valorBruto(), PERC_PIN);
        long valorLiquidoMedico = req.valorBruto() - taxaPin;

        // Calcula valores por tributo (usa IBS/CBS efetivo para federais se ativo)
        long issCalc = aplicar(req.valorBruto(), aliquotas.iss());
        long irCalc, csllCalc, pisCalc, cofinsCalc;

        if (aliquotas.ibsCbsAtivo() && aliquotas.aliqIbsCbsEfetiva() != null) {
            irCalc = aplicar(req.valorBruto(), aliquotas.aliqIbsCbsEfetiva());
            csllCalc = 0; pisCalc = 0; cofinsCalc = 0;
        } else {
            irCalc   = aplicar(req.valorBruto(), aliquotas.ir());
            csllCalc = aplicar(req.valorBruto(), aliquotas.csll());
            pisCalc  = aplicar(req.valorBruto(), aliquotas.pis());
            cofinsCalc = aplicar(req.valorBruto(), aliquotas.cofins());
        }

        long valorIss, valorIr, valorCsll, valorPis, valorCofins, totalRetencoes;

        switch (cenario) {
            case "A" -> {
                // PJ com retenção: ISS destacado + federais retidos pelo tomador
                valorIss    = req.indicadorRetencaoIss() ? issCalc : issCalc;
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

        long totalTributos = valorIss + valorIr + valorCsll + valorPis + valorCofins;
        long resultadoPin  = taxaPin - totalTributos;

        AuditoriaCenario auditoria = auditoriaService.registrar(req.competencia(), cenario, aliquotas);

        return new CalculoFiscalResponse(
            req.valorBruto(), taxaPin,
            valorIss, valorIr, valorCsll, valorPis, valorCofins,
            totalRetencoes, totalTributos,
            valorLiquidoMedico, resultadoPin,
            cenario, req.competencia(), auditoria
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

    private AliquotasCenario carregarAliquotas(String cnpjId, String competencia) {
        // 1. Alíquotas por tributo (EPIC-05.1, tabela parametros_fiscais V2)
        Optional<BigDecimal> iss   = buscarAliquotaPerTributo(cnpjId, TipoTributo.ISS,   competencia);
        Optional<BigDecimal> ir    = buscarAliquotaPerTributo(cnpjId, TipoTributo.IR,    competencia);
        Optional<BigDecimal> csll  = buscarAliquotaPerTributo(cnpjId, TipoTributo.CSLL,  competencia);
        Optional<BigDecimal> pis   = buscarAliquotaPerTributo(cnpjId, TipoTributo.PIS,   competencia);
        Optional<BigDecimal> cofins = buscarAliquotaPerTributo(cnpjId, TipoTributo.COFINS, competencia);

        // 2. Regime geral (TASK-04.5, tabela parametro_fiscal V1) como fallback e fonte de IBS/CBS
        LocalDate dataRef = competenciaToDate(competencia);
        Optional<ParametroFiscal> regimeGeral =
            p1Repo.findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(cnpjId, dataRef);

        if (regimeGeral.isPresent()) {
            ParametroFiscal pf = regimeGeral.get();
            BigDecimal ibsCbsEfetiva = null;
            if (pf.isIbsCbsAtivo() && pf.getAliqIbsCbs() != null && pf.getReducaoIbsCbsSaude() != null) {
                ibsCbsEfetiva = pf.getAliqIbsCbs()
                    .multiply(BigDecimal.ONE.subtract(pf.getReducaoIbsCbsSaude()))
                    .setScale(8, RoundingMode.HALF_UP);
            }
            return new AliquotasCenario(
                iss.orElse(pf.getAliqIss()),
                ir.orElse(pf.getAliqIr()),
                csll.orElse(pf.getAliqCsll()),
                pis.orElse(pf.getAliqPis()),
                cofins.orElse(pf.getAliqCofins()),
                pf.isIbsCbsAtivo(),
                ibsCbsEfetiva
            );
        }

        // 3. Sem nenhum parâmetro configurado: tributos zero (calcula mas não lança)
        return new AliquotasCenario(
            iss.orElse(BigDecimal.ZERO),
            ir.orElse(BigDecimal.ZERO),
            csll.orElse(BigDecimal.ZERO),
            pis.orElse(BigDecimal.ZERO),
            cofins.orElse(BigDecimal.ZERO),
            false, null
        );
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
