package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.domain.NotaFiscal;
import br.com.pinsaude.fiscal.domain.RegimeTributario;
import br.com.pinsaude.fiscal.dto.EmitirNfseRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Snapshot da reforma tributária na nota (CBS-41/42), usado pelos dois caminhos de emissão: pelo
 * sistema ({@link NfseService}) e no portal da prefeitura ({@link NfseEmissaoExternaService}).
 * O regime e os valores vêm da regra do motor ({@link MotorFiscalService#tributacaoIbsCbs}); o
 * navegador só manda a classificação do serviço LC116 da produção.
 */
final class ReformaNota {

    private ReformaNota() {}

    /**
     * Grava o regime e, no IBS_CBS, CBS/IBS na nota. No IBS_CBS a NFS-e exige o grupo IBSCBS: sem
     * CST/cClassTrib no serviço → 422. No LEGADO os campos da reforma voltam ao padrão (a nota pode
     * ser reaproveitada de uma tentativa anterior).
     */
    static void aplicar(NotaFiscal nota, EmitirNfseRequest req, TributacaoIbsCbs t) {
        nota.setRegimeTributario(t.regime());
        if (!t.ibsCbs()) {
            limpar(nota);
            return;
        }
        if (req.cstIbsCbs() == null || req.cClassTrib() == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "A competência " + req.competencia() + " já está no regime CBS/IBS e o serviço da produção "
                + "não tem a classificação (CST e cClassTrib). Cadastre em Configuração Fiscal › Serviços "
                + "antes de emitir.");
        }
        // PIS/COFINS não existem no regime novo (o motor já devolve zero; aqui é só proteção).
        nota.setValorPis(0L);
        nota.setValorCofins(0L);
        nota.setFatorIss(t.fatorIss());
        nota.setCstIbsCbs(req.cstIbsCbs());
        nota.setCClassTrib(req.cClassTrib());
        nota.setReducaoIbsCbs(req.reducaoIbsCbs() != null ? req.reducaoIbsCbs() : BigDecimal.ZERO);
        nota.setCbsIbsPorFora(t.porFora());
        nota.setBaseCalculoIbsCbs(t.baseCalculo());
        nota.setAliqCbs(t.aliqCbs());
        nota.setAliqIbs(t.aliqIbs());
        nota.setAliqIbsUf(t.aliqIbsUf());
        nota.setAliqIbsMun(t.aliqIbsMun());
        nota.setValorCbs(t.valorCbs());
        nota.setValorIbs(t.valorIbs());
        nota.setValorIbsUf(t.valorIbsUf());
        nota.setValorIbsMun(t.valorIbsMun());
    }

    /**
     * CBS-42 — valores de CBS/IBS impressos na nota emitida no portal da prefeitura, quando diferem
     * do cálculo do motor. Só vale no IBS_CBS. O IBS é repartido entre UF e município na proporção
     * das alíquotas do ano (o municipal fica com a diferença, como no motor).
     */
    static void corrigirValores(NotaFiscal nota, Long valorCbs, Long valorIbs) {
        if (valorCbs == null && valorIbs == null) return;
        if (nota.getRegimeTributario() != RegimeTributario.IBS_CBS) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "A competência " + nota.getCompetencia() + " não está no regime CBS/IBS: a nota não "
                + "tem CBS nem IBS para corrigir.");
        }
        if (valorCbs != null) nota.setValorCbs(valorCbs);
        if (valorIbs != null) {
            nota.setValorIbs(valorIbs);
            long uf = parteUf(valorIbs, nota.getAliqIbsUf(), nota.getAliqIbsMun());
            nota.setValorIbsUf(uf);
            nota.setValorIbsMun(valorIbs - uf);
        }
    }

    static long parteUf(long valorIbs, BigDecimal aliqUf, BigDecimal aliqMun) {
        BigDecimal uf = aliqUf != null ? aliqUf : BigDecimal.ZERO;
        BigDecimal soma = uf.add(aliqMun != null ? aliqMun : BigDecimal.ZERO);
        if (soma.signum() <= 0) return 0L;
        return BigDecimal.valueOf(valorIbs).multiply(uf)
            .divide(soma, 0, RoundingMode.HALF_UP).longValue();
    }

    private static void limpar(NotaFiscal nota) {
        nota.setCstIbsCbs(null);
        nota.setCClassTrib(null);
        nota.setReducaoIbsCbs(null);
        nota.setCbsIbsPorFora(false);
        nota.setBaseCalculoIbsCbs(null);
        nota.setAliqCbs(null);
        nota.setAliqIbs(null);
        nota.setAliqIbsUf(null);
        nota.setAliqIbsMun(null);
        nota.setValorCbs(0L);
        nota.setValorIbs(0L);
        nota.setValorIbsUf(0L);
        nota.setValorIbsMun(0L);
    }
}
