package br.com.pinsaude.fiscal.service;

import br.com.pinsaude.fiscal.dto.AuditoriaCenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditoriaFiscalService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaFiscalService.class);

    public AuditoriaCenario registrar(String competencia, String cenario, AliquotasCenario aliquotas) {
        log.info(
            "AUDITORIA_FISCAL competencia={} cenario={} regime={} iss={} fator_iss={} ir={} csll={} pis={} cofins={} cbs={} ibs={}",
            competencia, cenario, aliquotas.regime(),
            aliquotas.iss(), aliquotas.fatorIss(), aliquotas.ir(), aliquotas.csll(),
            aliquotas.pis(), aliquotas.cofins(), aliquotas.aliqCbs(), aliquotas.aliqIbs()
        );
        boolean novo = aliquotas.ibsCbsAtivo();
        return new AuditoriaCenario(
            competencia,
            cenario,
            aliquotas.iss(),
            aliquotas.ir(),
            aliquotas.csll(),
            aliquotas.pis(),
            aliquotas.cofins(),
            novo,
            novo ? aliquotas.aliqCbs().add(aliquotas.aliqIbs()) : null,
            aliquotas.regime().name(),
            aliquotas.aliqCbs(),
            aliquotas.aliqIbs(),
            aliquotas.fatorIss()
        );
    }
}
