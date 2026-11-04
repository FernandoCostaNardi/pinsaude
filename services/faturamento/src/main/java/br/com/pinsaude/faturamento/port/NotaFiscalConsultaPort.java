package br.com.pinsaude.faturamento.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Consulta ao serviço Fiscal: existe NFS-e para esta produção? Usado antes de excluir uma
 * produção — o faturamento não lê o schema fiscal (serviços isolados por usuário de banco).
 */
public interface NotaFiscalConsultaPort {

    /**
     * Status da nota fiscal da produção (ex.: "EMITIDA", "AGUARDANDO_VALIDACAO"), ou vazio se a
     * produção não tem nota. Lança {@link ConsultaIndisponivelException} se o Fiscal não
     * respondeu — quem chama deve bloquear a operação (falha fechada).
     */
    Optional<String> statusDaNota(UUID producaoId);

    class ConsultaIndisponivelException extends RuntimeException {
        public ConsultaIndisponivelException(String msg, Throwable cause) { super(msg, cause); }
    }
}
