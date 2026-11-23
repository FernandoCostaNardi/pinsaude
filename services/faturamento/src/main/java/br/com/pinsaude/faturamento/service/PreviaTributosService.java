package br.com.pinsaude.faturamento.service;

import br.com.pinsaude.faturamento.domain.Servico;
import br.com.pinsaude.faturamento.domain.TipoTomador;
import br.com.pinsaude.faturamento.domain.Tomador;
import br.com.pinsaude.faturamento.domain.TomadorAliquota;
import br.com.pinsaude.faturamento.dto.PreviewCalculoRequest;
import br.com.pinsaude.faturamento.dto.PreviewCalculoResponse;
import br.com.pinsaude.faturamento.port.MotorFiscalPort;
import br.com.pinsaude.faturamento.port.MotorFiscalPort.MotorFiscalException;
import br.com.pinsaude.faturamento.port.MotorFiscalPort.PedidoCalculo;
import br.com.pinsaude.faturamento.port.MotorFiscalPort.ResultadoCalculo;
import br.com.pinsaude.faturamento.repository.ServicoRepository;
import br.com.pinsaude.faturamento.repository.TomadorAliquotaRepository;
import br.com.pinsaude.faturamento.repository.TomadorRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Prévia dos tributos de uma produção (POST /api/producoes/preview-calculo) — CBS-30.
 *
 * O faturamento não calcula tributo: monta o pedido para o motor fiscal com as mesmas regras das
 * telas de emissão (apps/web/src/fiscal/calculoNota.ts) e só converte a resposta. Assim a prévia é
 * igual à nota emitida, nos dois regimes (LEGADO e IBS_CBS).
 *
 * - Alíquotas ISS/IR/CSLL/PIS/COFINS: a do tomador quando cadastrada, senão a do serviço LC116.
 *   No faturamento são percentuais (5.0000); vão ao motor em fração (0.05).
 * - Classificação CBS/IBS (redução, CST, cClassTrib) vem do serviço LC116 (CBS-12).
 * - Taxa Pin: a do pedido (cadastro do médico) ou 15%; a do motor (15% fixo) é ignorada.
 */
@Service
public class PreviaTributosService {

    static final BigDecimal TAXA_PIN_DEFAULT = new BigDecimal("0.1500");
    static final List<String> TRIBUTOS_COM_ALIQUOTA = List.of("ISS", "IR", "CSLL", "PIS", "COFINS");

    private final TomadorRepository tomadorRepo;
    private final ServicoRepository servicoRepo;
    private final TomadorAliquotaRepository aliquotaRepo;
    private final MotorFiscalPort motor;

    public PreviaTributosService(TomadorRepository tomadorRepo,
                                 ServicoRepository servicoRepo,
                                 TomadorAliquotaRepository aliquotaRepo,
                                 MotorFiscalPort motor) {
        this.tomadorRepo = tomadorRepo;
        this.servicoRepo = servicoRepo;
        this.aliquotaRepo = aliquotaRepo;
        this.motor = motor;
    }

    @Transactional(readOnly = true)
    public PreviewCalculoResponse calcular(PreviewCalculoRequest req) {
        if (req.valorBruto() == null || req.valorBruto() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Valor bruto deve ser maior que zero");
        }
        Tomador tomador = tomadorRepo.findById(req.tomadorId())
            .orElseThrow(() -> new EntityNotFoundException("Tomador não encontrado: " + req.tomadorId()));
        Servico servico = servicoRepo.findById(req.servicoId())
            .orElseThrow(() -> new EntityNotFoundException("Serviço não encontrado: " + req.servicoId()));

        String competencia = req.competencia() != null && !req.competencia().isBlank()
            ? req.competencia() : YearMonth.now().toString();

        PedidoCalculo pedido = montarPedido(req.valorBruto(), competencia, tomador, servico,
            aliquotaRepo.findByTomadorId(tomador.getId()));

        ResultadoCalculo r;
        try {
            r = motor.calcular(tomador.getCnpjIdTenant(), pedido);
        } catch (MotorFiscalException e) {
            throw new ResponseStatusException(HttpStatus.valueOf(e.status()), e.getMessage(), e);
        }

        BigDecimal taxaPinPct = req.taxaPinPct() != null ? req.taxaPinPct() : TAXA_PIN_DEFAULT;
        return converter(r, req.valorBruto(), taxaPinPct);
    }

    static PedidoCalculo montarPedido(long valorBruto, String competencia, Tomador tomador, Servico servico,
                                      List<TomadorAliquota> aliquotasTomador) {
        Map<String, BigDecimal> doTomador = new LinkedHashMap<>();
        for (TomadorAliquota a : aliquotasTomador) {
            if (a.getTipoTributo() != null && a.getValorAliquota() != null) {
                doTomador.put(a.getTipoTributo().toUpperCase(), a.getValorAliquota());
            }
        }
        Map<String, Function<Servico, BigDecimal>> doServico = Map.of(
            "ISS", Servico::getAliquotaIss,
            "IR", Servico::getAliquotaIr,
            "CSLL", Servico::getAliquotaCsll,
            "PIS", Servico::getAliquotaPis,
            "COFINS", Servico::getAliquotaCofins);

        Map<String, BigDecimal> override = new LinkedHashMap<>();
        for (String t : TRIBUTOS_COM_ALIQUOTA) {
            BigDecimal pct = doTomador.containsKey(t) ? doTomador.get(t) : doServico.get(t).apply(servico);
            override.put(t, emFracao(pct));
        }

        BigDecimal reducao = servico.getReducaoIbsCbs();
        return new PedidoCalculo(
            valorBruto,
            competencia,
            tomador.getTipo() != TipoTomador.PACIENTE_PF,
            tomador.isIndicadorRetencaoFederal(),
            tomador.isIndicadorRetencaoIss(),
            false,
            override,
            reducao != null && reducao.signum() > 0 ? reducao : null,
            vazioParaNulo(servico.getCstIbsCbs()),
            vazioParaNulo(servico.getCClassTrib()),
            null); // acerto "por fora" por tomador (CBS-01) ainda sem cadastro: dentro do preço
    }

    static PreviewCalculoResponse converter(ResultadoCalculo r, long valorBruto, BigDecimal taxaPinPct) {
        List<String> retidos = r.tributosRetidos() != null ? r.tributosRetidos() : List.of();
        long iss = retidos.contains("ISS") ? r.valorIss() : 0L;
        long ir = retidos.contains("IR") ? r.valorIr() : 0L;
        long csll = retidos.contains("CSLL") ? r.valorCsll() : 0L;
        long pis = retidos.contains("PIS") ? r.valorPis() : 0L;
        long cofins = retidos.contains("COFINS") ? r.valorCofins() : 0L;

        long taxaPin = BigDecimal.valueOf(valorBruto).multiply(taxaPinPct)
            .setScale(0, RoundingMode.HALF_UP).longValue();
        // O motor devolve resultadoPin com a taxa fixa de 15%; o que sai da cota da Pin é a diferença.
        long tributosDaPin = r.taxaPin() - r.resultadoPin();

        return new PreviewCalculoResponse(
            valorBruto, taxaPin,
            iss, ir, csll, pis, cofins, r.totalRetencoes(),
            valorBruto - taxaPin,
            taxaPin - tributosDaPin,
            r.competencia(), r.regime() != null ? r.regime() : "LEGADO", r.cenario(),
            r.valorIss(), r.valorIr(), r.valorCsll(), r.valorPis(), r.valorCofins(),
            r.valorCbs(), r.valorIbs(), r.totalTributos(),
            r.aliqCbs(), r.aliqIbs(), r.fatorIss(), r.cstIbsCbs(), r.cClassTrib(),
            r.cbsIbsPorFora(), r.baseCalculoIbsCbs(),
            r.valorTotalNota() > 0 ? r.valorTotalNota() : valorBruto,
            retidos);
    }

    private static BigDecimal emFracao(BigDecimal pct) {
        if (pct == null) return BigDecimal.ZERO;
        return pct.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
    }

    private static String vazioParaNulo(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
