package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.config.SecurityUtils;
import br.com.pinsaude.fiscal.domain.CronogramaReforma;
import br.com.pinsaude.fiscal.dto.CronogramaReformaRequest;
import br.com.pinsaude.fiscal.dto.CronogramaReformaResponse;
import br.com.pinsaude.fiscal.repository.CronogramaReformaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

/**
 * Cronograma da reforma tributária (CBS-10). Catálogo global de alíquotas por ano; a chave
 * IBS/CBS de cada empresa fica em fiscal.parametro_fiscal e lê daqui as alíquotas do ano.
 */
@Service
public class CronogramaReformaService {

    private final CronogramaReformaRepository repo;

    public CronogramaReformaService(CronogramaReformaRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<CronogramaReformaResponse> listar() {
        return repo.findAllByOrderByAnoAsc().stream().map(CronogramaReformaResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CronogramaReformaResponse buscar(int ano) {
        return CronogramaReformaResponse.from(findOrThrow(ano));
    }

    /** Ano do cronograma vigente para uma competência (YYYY-MM); vazio se o ano não está cadastrado. */
    @Transactional(readOnly = true)
    public Optional<CronogramaReforma> paraCompetencia(String competencia) {
        int ano = Integer.parseInt(competencia.substring(0, 4));
        return repo.findById((short) ano);
    }

    @Transactional
    public CronogramaReformaResponse atualizar(int ano, CronogramaReformaRequest req) {
        CronogramaReforma c = findOrThrow(ano);
        if (req.aliquotasDefinitivas()
                && (req.aliqCbs() == null || req.aliqIbsUf() == null || req.aliqIbsMun() == null)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Para marcar as alíquotas de " + ano + " como definitivas, informe CBS, IBS estadual e IBS municipal");
        }
        c.setAliqCbs(req.aliqCbs());
        c.setAliqIbsUf(req.aliqIbsUf());
        c.setAliqIbsMun(req.aliqIbsMun());
        c.setFatorIss(req.fatorIss());
        c.setAliquotasDefinitivas(req.aliquotasDefinitivas());
        if (req.fonte() != null) c.setFonte(req.fonte());
        c.setAtualizadoPor(SecurityUtils.currentUsuario());
        return CronogramaReformaResponse.from(repo.saveAndFlush(c));
    }

    private CronogramaReforma findOrThrow(int ano) {
        if (ano < Short.MIN_VALUE || ano > Short.MAX_VALUE) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Ano fora do cronograma: " + ano);
        }
        return repo.findById((short) ano)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Ano fora do cronograma da reforma: " + ano));
    }
}
