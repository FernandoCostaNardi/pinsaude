package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.RegimeTributario;
import br.com.pinsaude.fiscal.domain.StatusNota;
import br.com.pinsaude.fiscal.dto.TributosMesResponse;
import br.com.pinsaude.fiscal.dto.TributosMesResponse.CbsIbs;
import br.com.pinsaude.fiscal.dto.TributosMesResponse.PorRegime;
import br.com.pinsaude.fiscal.dto.TributosMesResponse.Retencoes;
import br.com.pinsaude.fiscal.repository.NotaFiscalRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * CBS-32 — separa, nas notas emitidas no mês, o que o tomador reteve (IR, CSLL, ISS e, no regime
 * antigo, PIS/COFINS) do que a Pin tem a recolher de CBS/IBS. Base: data de emissão da nota
 * (fuso de São Paulo), só notas EMITIDAS (cancelada/rejeitada não conta).
 */
@Service
public class TributosMesService {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final OffsetDateTime INICIO_SEMPRE = OffsetDateTime.parse("2000-01-01T00:00:00-03:00");
    private static final OffsetDateTime FIM_SEMPRE = OffsetDateTime.parse("2100-01-01T00:00:00-03:00");

    private final NotaFiscalRepository notaRepo;

    public TributosMesService(NotaFiscalRepository notaRepo) {
        this.notaRepo = notaRepo;
    }

    /** @param mes mês da emissão; nulo = todas as notas emitidas */
    @Transactional(readOnly = true)
    public TributosMesResponse calcular(YearMonth mes) {
        OffsetDateTime de = mes == null ? INICIO_SEMPRE : mes.atDay(1).atStartOfDay(FUSO).toOffsetDateTime();
        OffsetDateTime ate = mes == null ? FIM_SEMPRE : mes.plusMonths(1).atDay(1).atStartOfDay(FUSO).toOffsetDateTime();

        List<PorRegime> porRegime = new ArrayList<>();
        for (Object[] l : notaRepo.somarTributosPorRegime(StatusNota.EMITIDA, de, ate)) {
            RegimeTributario regime = l[0] == null ? RegimeTributario.LEGADO : (RegimeTributario) l[0];
            porRegime.add(new PorRegime(regime.name(), (int) n(l[1]), n(l[2]),
                    retencoes(n(l[3]), n(l[4]), n(l[5]), n(l[6]), n(l[7])),
                    cbsIbs(n(l[8]), n(l[9]))));
        }
        porRegime.sort(Comparator.comparing(r -> RegimeTributario.valueOf(r.regime()).ordinal()));

        int qtd = 0;
        long bruto = 0, iss = 0, ir = 0, csll = 0, pis = 0, cofins = 0, cbs = 0, ibs = 0;
        for (PorRegime r : porRegime) {
            qtd += r.quantidadeNotas();
            bruto += r.valorNotas();
            iss += r.retencoes().iss();
            ir += r.retencoes().ir();
            csll += r.retencoes().csll();
            pis += r.retencoes().pis();
            cofins += r.retencoes().cofins();
            cbs += r.cbsIbs().cbs();
            ibs += r.cbsIbs().ibs();
        }
        return new TributosMesResponse(mes == null ? null : mes.toString(), qtd, bruto,
                retencoes(iss, ir, csll, pis, cofins), cbsIbs(cbs, ibs), porRegime);
    }

    private static Retencoes retencoes(long iss, long ir, long csll, long pis, long cofins) {
        return new Retencoes(iss, ir, csll, pis, cofins, iss + ir + csll + pis + cofins);
    }

    private static CbsIbs cbsIbs(long cbs, long ibs) {
        return new CbsIbs(cbs, ibs, cbs + ibs);
    }

    private static long n(Object v) {
        return v == null ? 0L : ((Number) v).longValue();
    }
}
