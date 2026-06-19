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
            "AUDITORIA_FISCAL competencia={} cenario={} iss={} ir={} csll={} pis={} cofins={} ibs_cbs={}",
            competencia, cenario,
            aliquotas.iss(), aliquotas.ir(), aliquotas.csll(),
            aliquotas.pis(), aliquotas.cofins(),
            aliquotas.ibsCbsAtivo()
        );
        return new AuditoriaCenario(
            competencia,
            cenario,
            aliquotas.iss(),
            aliquotas.ir(),
            aliquotas.csll(),
            aliquotas.pis(),
            aliquotas.cofins(),
            aliquotas.ibsCbsAtivo(),
            aliquotas.aliqIbsCbsEfetiva()
        );
    }
}
