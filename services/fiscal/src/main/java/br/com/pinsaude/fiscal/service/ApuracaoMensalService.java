package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.apuracao.AliquotasReformaAno;
import br.com.pinsaude.fiscal.apuracao.CalculadoraApuracao;
import br.com.pinsaude.fiscal.apuracao.MesApuracao;
import br.com.pinsaude.fiscal.apuracao.ResultadoApuracao;
import br.com.pinsaude.fiscal.domain.*;
import br.com.pinsaude.fiscal.dto.ApuracaoMensalResponse;
import br.com.pinsaude.fiscal.dto.ApuracaoMensalResponse.MesComposicao;
import br.com.pinsaude.fiscal.dto.ApuracaoMensalResponse.NotaApurada;
import br.com.pinsaude.fiscal.port.RecebimentosPort;
import br.com.pinsaude.fiscal.port.RecebimentosPort.Recebimento;
import br.com.pinsaude.fiscal.port.RecebimentosPort.RecebimentosIndisponiveisException;
import br.com.pinsaude.fiscal.repository.CronogramaReformaRepository;
import br.com.pinsaude.fiscal.repository.NotaFiscalRepository;
import br.com.pinsaude.fiscal.repository.RegraEquiparacaoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * APU-01 — motor da apuração mensal por CNPJ sobre os dados reais.
 *
 * <p>Não tem fórmula própria: monta os {@link MesApuracao} do período com as notas e os recebimentos
 * e chama a {@link CalculadoraApuracao} da APU-00 com a regra vigente da empresa (ou o padrão global).
 * Enquanto a regra não estiver homologada, o resultado sai com {@code aHomologar = true}.
 *
 * <ul>
 *   <li>Notas: só EMITIDAS da empresa (cancelada/rejeitada não entra).</li>
 *   <li>Competência: a nota entra no mês pela competência gravada nela ou pela data de emissão,
 *       conforme {@code referencia_competencia} da regra.</li>
 *   <li>Caixa: a nota entra no mês do depósito conciliado (Faturamento, {@code /api/conciliacao/recebimentos}),
 *       pelo valor bruto da nota (receita bruta recebida). Produção com mais de um depósito: o primeiro.</li>
 *   <li>Equiparação hospitalar: pelo CNAE da nota nas regras de equiparação da empresa (Configuração
 *       Fiscal). Sem CNAE, sem regra ou com regras divergentes para o CNAE → sem equiparação (presunção
 *       maior, lado conservador), com aviso.</li>
 *   <li>Retenções: os valores de ISS/IR/CSLL/PIS/COFINS gravados na nota (retidos pelo tomador desde a
 *       CBS-21); o IR retido abate o IRPJ. Vão para o mês da nota (competência) e para o mês do depósito
 *       (caixa); cada tributo usa o regime dele.</li>
 *   <li>CBS/IBS (base soma das notas): os valores gravados nas notas, pelo mesmo critério.</li>
 * </ul>
 * O período carregado vai do início do trimestre civil até a competência (cobre IRPJ/CSLL trimestrais).
 */
@Service
public class ApuracaoMensalService {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final RegraApuracaoService regras;
    private final RegimeFiscalService regimeFiscal;
    private final CronogramaReformaRepository cronogramaRepo;
    private final NotaFiscalRepository notaRepo;
    private final RegraEquiparacaoRepository equiparacaoRepo;
    private final RecebimentosPort recebimentos;

    public ApuracaoMensalService(RegraApuracaoService regras, RegimeFiscalService regimeFiscal,
                                 CronogramaReformaRepository cronogramaRepo, NotaFiscalRepository notaRepo,
                                 RegraEquiparacaoRepository equiparacaoRepo, RecebimentosPort recebimentos) {
        this.regras = regras;
        this.regimeFiscal = regimeFiscal;
        this.cronogramaRepo = cronogramaRepo;
        this.notaRepo = notaRepo;
        this.equiparacaoRepo = equiparacaoRepo;
        this.recebimentos = recebimentos;
    }

    /**
     * @param competenciaInformada AAAA-MM
     * @param cnpjInformado        empresa, só para quem não tem cnpj_id no token (gestão); obrigatório nesse caso
     */
    @Transactional(readOnly = true)
    public ApuracaoMensalResponse apurar(String competenciaInformada, String cnpjInformado) {
        String competencia = RegraApuracaoService.validarCompetencia(competenciaInformada);
        String cnpj = RegraApuracaoService.empresaDoToken()
            .orElseGet(() -> RegraApuracaoService.normalizar(cnpjInformado));
        if (cnpj == null || cnpj.length() != 14) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a empresa (cnpj) da apuração");
        }
        YearMonth comp = YearMonth.parse(competencia);
        YearMonth inicio = YearMonth.of(comp.getYear(), ((comp.getMonthValue() - 1) / 3) * 3 + 1);

        RegraApuracao regra = regras.regraVigente(cnpj, competencia);
        RegimeTributario regime = RegimeTributario.valueOf(regimeFiscal.regime(cnpj, competencia).regime());
        ReferenciaCompetencia referencia = regra.getReferenciaCompetencia();

        List<String> avisos = new ArrayList<>();

        // ─── Notas por competência ───────────────────────────────────────────
        List<NotaFiscal> notasCompetencia = referencia == ReferenciaCompetencia.DATA_EMISSAO
            ? notaRepo.findDaEmpresaPorEmissao(cnpj, StatusNota.EMITIDA, inicioDoMes(inicio), inicioDoMes(comp.plusMonths(1)))
            : notaRepo.findDaEmpresaPorCompetencia(cnpj, StatusNota.EMITIDA, inicio.toString(), comp.toString());

        // ─── Notas por caixa (só se algum tributo aplicável usa caixa) ──────────
        boolean usaCaixa = regra.getTributos().stream()
            .anyMatch(t -> t.isAtivo() && t.getAplicaEm().vale(regime)
                && t.getRegimeReconhecimento() == RegimeReconhecimento.CAIXA);
        Map<UUID, LocalDate> recebidoEm = new HashMap<>();
        List<NotaFiscal> notasRecebidas = List.of();
        if (usaCaixa) {
            List<Recebimento> recs;
            try {
                recs = recebimentos.recebidosEntre(inicio.atDay(1), comp.atEndOfMonth());
            } catch (RecebimentosIndisponiveisException e) {
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível ler os recebimentos no Faturamento: a receita por caixa ficaria errada. Tente de novo.");
            }
            for (Recebimento r : recs) {
                recebidoEm.merge(r.producaoId(), r.recebidoEm(), (a, b) -> a.isBefore(b) ? a : b);
            }
            notasRecebidas = recebidoEm.isEmpty() ? List.of()
                : notaRepo.findDaEmpresaPorProducoes(cnpj, StatusNota.EMITIDA, recebidoEm.keySet());
        }

        // ─── Equiparação hospitalar pelo CNAE ─────────────────────────────────
        Map<String, Boolean> equiparacaoPorCnae = equiparacaoPorCnae(cnpj);
        Set<UUID> semCnae = new LinkedHashSet<>();
        Set<String> cnaeSemRegra = new TreeSet<>();
        Set<String> cnaeAmbiguo = new TreeSet<>();
        Function<NotaFiscal, Boolean> equiparada = n -> {
            String cnae = digitos(n.getCnaeCodigo());
            if (cnae == null) {
                semCnae.add(n.getId());
                return false;
            }
            Boolean eq = equiparacaoPorCnae.get(cnae);
            if (eq == null) {
                if (equiparacaoPorCnae.containsKey(cnae)) cnaeAmbiguo.add(n.getCnaeCodigo());
                else cnaeSemRegra.add(n.getCnaeCodigo());
                return false;
            }
            return eq;
        };

        // ─── Acumula por mês ─────────────────────────────────────────────────
        Map<YearMonth, Acumulador> porMes = new TreeMap<>();
        for (YearMonth m = inicio; !m.isAfter(comp); m = m.plusMonths(1)) porMes.put(m, new Acumulador(m));

        Map<UUID, NotaApuradaBuilder> notas = new LinkedHashMap<>();
        for (NotaFiscal n : notasCompetencia) {
            YearMonth mes = referencia == ReferenciaCompetencia.DATA_EMISSAO
                ? YearMonth.from(n.getEmitidaAt().atZoneSameInstant(FUSO))
                : YearMonth.parse(n.getCompetencia());
            Acumulador a = porMes.get(mes);
            if (a == null) continue;
            boolean eq = equiparada.apply(n);
            a.somarCompetencia(n, eq);
            notas.computeIfAbsent(n.getId(), k -> new NotaApuradaBuilder(n, eq)).mesCompetencia = mes.toString();
        }
        for (NotaFiscal n : notasRecebidas) {
            LocalDate data = recebidoEm.get(n.getProducaoId());
            if (data == null) continue;
            Acumulador a = porMes.get(YearMonth.from(data));
            if (a == null) continue;
            boolean eq = equiparada.apply(n);
            a.somarCaixa(n, eq);
            notas.computeIfAbsent(n.getId(), k -> new NotaApuradaBuilder(n, eq)).recebidoEm = data;
        }

        if (!semCnae.isEmpty()) {
            avisos.add(semCnae.size() + " nota(s) sem CNAE: consideradas sem equiparação hospitalar.");
        }
        if (!cnaeSemRegra.isEmpty()) {
            avisos.add("CNAE sem regra de equiparação na Configuração Fiscal (" + String.join(", ", cnaeSemRegra)
                + "): notas consideradas sem equiparação hospitalar.");
        }
        if (!cnaeAmbiguo.isEmpty()) {
            avisos.add("CNAE com regras de equiparação divergentes (" + String.join(", ", cnaeAmbiguo)
                + "): notas consideradas sem equiparação hospitalar.");
        }
        if (usaCaixa) {
            Set<UUID> comNota = notasRecebidas.stream().map(NotaFiscal::getProducaoId).collect(Collectors.toSet());
            long recebidasSemNota = recebidoEm.keySet().stream().filter(p -> !comNota.contains(p)).count();
            // Para a gestão o Faturamento devolve recebimentos de todas as empresas: só avisa quando o
            // token é da própria empresa (aí todo recebimento sem nota emitida é inconsistência).
            if (recebidasSemNota > 0 && RegraApuracaoService.empresaDoToken().isPresent()) {
                avisos.add(recebidasSemNota + " recebimento(s) sem nota emitida da empresa: fora da receita por caixa.");
            }
        }

        List<MesApuracao> meses = porMes.values().stream().map(Acumulador::toMes).toList();
        ResultadoApuracao calc = CalculadoraApuracao.calcular(regra, cnpj, regime, comp, meses,
            aliquotasReforma(comp.getYear()));

        List<String> todosAvisos = new ArrayList<>(calc.avisos());
        todosAvisos.addAll(avisos);
        ResultadoApuracao resultado = new ResultadoApuracao(calc.competencia(), calc.cnpj(), calc.regime(),
            calc.regraId(), calc.regraCompetenciaInicio(), calc.regraGlobal(), calc.homologado(), calc.aHomologar(),
            calc.tributos(), calc.totalDevido(), calc.totalRetencoes(), calc.totalARecolher(), List.copyOf(todosAvisos));

        List<NotaApurada> listaNotas = notas.values().stream()
            .map(NotaApuradaBuilder::build)
            .sorted(Comparator.comparing(NotaApurada::competencia).thenComparing(n -> n.numeroNota() == null ? "" : n.numeroNota()))
            .toList();

        return new ApuracaoMensalResponse(resultado, referencia.name(), usaCaixa,
            porMes.values().stream().map(Acumulador::toComposicao).toList(), listaNotas);
    }

    // ─── Apoio ────────────────────────────────────────────────────────────────

    /** CNAE (dígitos) → equiparado; valor nulo quando a empresa tem regras divergentes para o CNAE. */
    private Map<String, Boolean> equiparacaoPorCnae(String cnpj) {
        Map<String, Boolean> mapa = new HashMap<>();
        for (RegraEquiparacao r : equiparacaoRepo.findByCnpjIdOrderByCnaeAsc(cnpj)) {
            String cnae = digitos(r.getCnae());
            if (cnae == null) continue;
            boolean eq = Boolean.TRUE.equals(r.getIndicadorEquiparacao());
            if (mapa.containsKey(cnae) && !Objects.equals(mapa.get(cnae), eq)) mapa.put(cnae, null);
            else if (!mapa.containsKey(cnae)) mapa.put(cnae, eq);
        }
        return mapa;
    }

    private AliquotasReformaAno aliquotasReforma(int ano) {
        return cronogramaRepo.findById((short) ano).map(c -> new AliquotasReformaAno(c.getAliqCbs(),
                c.getAliqIbsUf() == null || c.getAliqIbsMun() == null ? null : c.getAliqIbsUf().add(c.getAliqIbsMun()),
                c.getFatorIss()))
            .orElse(AliquotasReformaAno.NENHUMA);
    }

    private static OffsetDateTime inicioDoMes(YearMonth m) {
        return m.atDay(1).atStartOfDay(FUSO).toOffsetDateTime();
    }

    static String digitos(String v) {
        if (v == null) return null;
        String d = v.replaceAll("\\D", "");
        return d.isEmpty() ? null : d;
    }

    static Map<TributoApuracao, Long> retencoesDaNota(NotaFiscal n) {
        Map<TributoApuracao, Long> m = new EnumMap<>(TributoApuracao.class);
        m.put(TributoApuracao.ISS, nz(n.getValorIss()));
        m.put(TributoApuracao.IRPJ, nz(n.getValorIr()));
        m.put(TributoApuracao.CSLL, nz(n.getValorCsll()));
        m.put(TributoApuracao.PIS, nz(n.getValorPis()));
        m.put(TributoApuracao.COFINS, nz(n.getValorCofins()));
        return m;
    }

    private static long nz(Long v) {
        return v == null ? 0L : v;
    }

    private static final class Acumulador {
        final YearMonth mes;
        long compEq, compPad, caixaEq, caixaPad, cbs, ibs, cbsCaixa, ibsCaixa;
        int qtdCompetencia, qtdCaixa;
        final Map<TributoApuracao, Long> ret = new EnumMap<>(TributoApuracao.class);
        final Map<TributoApuracao, Long> retCaixa = new EnumMap<>(TributoApuracao.class);

        Acumulador(YearMonth mes) { this.mes = mes; }

        void somarCompetencia(NotaFiscal n, boolean eq) {
            qtdCompetencia++;
            if (eq) compEq += nz(n.getValorBruto()); else compPad += nz(n.getValorBruto());
            retencoesDaNota(n).forEach((t, v) -> ret.merge(t, v, Long::sum));
            cbs += nz(n.getValorCbs());
            ibs += nz(n.getValorIbs());
        }

        void somarCaixa(NotaFiscal n, boolean eq) {
            qtdCaixa++;
            if (eq) caixaEq += nz(n.getValorBruto()); else caixaPad += nz(n.getValorBruto());
            retencoesDaNota(n).forEach((t, v) -> retCaixa.merge(t, v, Long::sum));
            cbsCaixa += nz(n.getValorCbs());
            ibsCaixa += nz(n.getValorIbs());
        }

        MesApuracao toMes() {
            return new MesApuracao(mes, compEq, compPad, caixaEq, caixaPad, ret, cbs, ibs, retCaixa, cbsCaixa, ibsCaixa);
        }

        MesComposicao toComposicao() {
            return new MesComposicao(mes.toString(), qtdCompetencia, compEq, compPad, qtdCaixa, caixaEq, caixaPad,
                nomes(ret), nomes(retCaixa), cbs, ibs, cbsCaixa, ibsCaixa);
        }

        private static Map<String, Long> nomes(Map<TributoApuracao, Long> m) {
            return m.entrySet().stream().filter(e -> e.getValue() != 0)
                .collect(Collectors.toMap(e -> e.getKey().name(), Map.Entry::getValue, Long::sum, TreeMap::new));
        }
    }

    private static final class NotaApuradaBuilder {
        final NotaFiscal n;
        final boolean equiparada;
        String mesCompetencia;
        LocalDate recebidoEm;

        NotaApuradaBuilder(NotaFiscal n, boolean equiparada) {
            this.n = n;
            this.equiparada = equiparada;
        }

        NotaApurada build() {
            Map<TributoApuracao, Long> r = retencoesDaNota(n);
            return new NotaApurada(n.getId(), n.getProducaoId(), n.getNumeroNota(), n.getTomadorNome(),
                n.getCompetencia(),
                n.getEmitidaAt() == null ? null : n.getEmitidaAt().atZoneSameInstant(FUSO).toLocalDate(),
                recebidoEm, mesCompetencia, nz(n.getValorBruto()), n.getCnaeCodigo(), equiparada,
                n.getRegimeTributario() == null ? RegimeTributario.LEGADO.name() : n.getRegimeTributario().name(),
                r.values().stream().mapToLong(Long::longValue).sum(), nz(n.getValorCbs()), nz(n.getValorIbs()));
        }
    }
}
