package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.CronogramaReforma;
import br.com.pinsaude.fiscal.domain.ParametroFiscal;
import br.com.pinsaude.fiscal.domain.RegimeTributario;
import br.com.pinsaude.fiscal.dto.RegimeCompetenciaResponse;
import br.com.pinsaude.fiscal.dto.RegimeCompetenciaResponse.AliquotasAno;
import br.com.pinsaude.fiscal.repository.CronogramaReformaRepository;
import br.com.pinsaude.fiscal.repository.ParametroFiscalRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * CBS-15 — diz em que regime a empresa está numa competência e quais tributos aparecem.
 * Usa a mesma regra do motor fiscal: parâmetro vigente com a chave ligada e homologada → IBS_CBS.
 */
@Service
public class RegimeFiscalService {

    static final List<String> TRIBUTOS_LEGADO = List.of("ISS", "IR", "CSLL", "PIS", "COFINS");
    private static final Pattern COMPETENCIA = Pattern.compile("\\d{4}-\\d{2}");

    private final ParametroFiscalRepository parametroRepo;
    private final CronogramaReformaRepository cronogramaRepo;

    public RegimeFiscalService(ParametroFiscalRepository parametroRepo,
                               CronogramaReformaRepository cronogramaRepo) {
        this.parametroRepo = parametroRepo;
        this.cronogramaRepo = cronogramaRepo;
    }

    @Transactional(readOnly = true)
    public RegimeCompetenciaResponse regime(String cnpjId, String competencia) {
        LocalDate dataRef = parseCompetencia(competencia);
        Optional<ParametroFiscal> vigente = parametroRepo
            .findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(cnpjId, dataRef);

        boolean chaveLigada = vigente.map(ParametroFiscal::isIbsCbsAtivo).orElse(false);
        boolean homologado  = vigente.map(ParametroFiscal::isHomologado).orElse(false);
        String vigenciaParametro = vigente
            .map(p -> YearMonth.from(p.getVigenciaInicio()).toString())
            .orElse(null);

        if (!vigente.map(ParametroFiscal::isRegimeIbsCbsVigente).orElse(false)) {
            return new RegimeCompetenciaResponse(competencia, RegimeTributario.LEGADO.name(),
                TRIBUTOS_LEGADO, chaveLigada, homologado, vigenciaParametro, null);
        }

        int ano = dataRef.getYear();
        CronogramaReforma cron = cronogramaRepo.findById((short) ano).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Cronograma da reforma não cadastrado para " + ano));

        List<String> tributos = new ArrayList<>();
        // ISS some quando o fator do ano chega a zero (2033).
        if (cron.getFatorIss() != null && cron.getFatorIss().signum() > 0) tributos.add("ISS");
        tributos.addAll(List.of("IR", "CSLL", "CBS", "IBS"));

        BigDecimal ibs = cron.getAliqIbsUf() == null || cron.getAliqIbsMun() == null
            ? null : cron.getAliqIbsUf().add(cron.getAliqIbsMun());
        AliquotasAno aliquotas = new AliquotasAno(ano, cron.getAliqCbs(), cron.getAliqIbsUf(),
            cron.getAliqIbsMun(), ibs, cron.getFatorIss(), cron.isAliquotasDefinitivas());

        return new RegimeCompetenciaResponse(competencia, RegimeTributario.IBS_CBS.name(),
            List.copyOf(tributos), chaveLigada, homologado, vigenciaParametro, aliquotas);
    }

    private LocalDate parseCompetencia(String competencia) {
        if (competencia == null || !COMPETENCIA.matcher(competencia).matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Competência inválida: use o formato AAAA-MM");
        }
        try {
            return YearMonth.parse(competencia).atDay(1);
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Competência inválida: use o formato AAAA-MM");
        }
    }
}
