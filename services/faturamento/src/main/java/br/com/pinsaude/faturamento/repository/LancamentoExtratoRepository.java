package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.LancamentoExtrato;
import br.com.pinsaude.faturamento.domain.StatusConciliacao;
import br.com.pinsaude.faturamento.domain.TipoLancamentoExtrato;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LancamentoExtratoRepository extends JpaRepository<LancamentoExtrato, UUID> {

    List<LancamentoExtrato> findByExtratoIdOrderByDataLancamentoDesc(UUID extratoId);

    List<LancamentoExtrato> findByExtratoIdAndStatusConciliacaoOrderByDataLancamentoDesc(
            UUID extratoId, StatusConciliacao statusConciliacao);

    boolean existsByExtratoIdAndIdentificadorExterno(UUID extratoId, String identificadorExterno);

    List<LancamentoExtrato> findByExtratoIdAndTipoAndStatusConciliacao(
            UUID extratoId, TipoLancamentoExtrato tipo, StatusConciliacao statusConciliacao);
}
