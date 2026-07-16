package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.LancamentoExtrato;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface LancamentoExtratoRepository extends JpaRepository<LancamentoExtrato, UUID> {

    List<LancamentoExtrato> findByExtratoIdOrderByDataLancamentoDesc(UUID extratoId);

    // Native query com cast explícito: Hibernate envia enum como varchar; PostgreSQL exige cast para tipo user-defined
    @Query(value = """
            SELECT * FROM faturamento.lancamentos_extrato
            WHERE extrato_id = :extratoId
              AND status_conciliacao = CAST(:status AS faturamento.status_conciliacao_enum)
            ORDER BY data_lancamento DESC
            """, nativeQuery = true)
    List<LancamentoExtrato> findByExtratoIdAndStatusConciliacaoOrderByDataLancamentoDesc(
            @Param("extratoId") UUID extratoId,
            @Param("status") String status);

    boolean existsByExtratoIdAndIdentificadorExterno(UUID extratoId, String identificadorExterno);

    // Native query com cast explícito para os dois campos enum
    @Query(value = """
            SELECT * FROM faturamento.lancamentos_extrato
            WHERE extrato_id = :extratoId
              AND tipo = CAST(:tipo AS faturamento.tipo_lancamento_enum)
              AND status_conciliacao = CAST(:status AS faturamento.status_conciliacao_enum)
            """, nativeQuery = true)
    List<LancamentoExtrato> findByExtratoIdAndTipoAndStatusConciliacao(
            @Param("extratoId") UUID extratoId,
            @Param("tipo") String tipo,
            @Param("status") String status);
}
