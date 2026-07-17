package br.com.pinsaude.ledger.repository;

import br.com.pinsaude.ledger.domain.PartidaLedger;
import br.com.pinsaude.ledger.domain.TipoPartida;
import br.com.pinsaude.ledger.dto.ExtratoLinha;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PartidaLedgerRepository extends JpaRepository<PartidaLedger, UUID> {

    /**
     * Saldo do médico = SUM(créditos) - SUM(débitos) sobre a conta de repasse do médico.
     * Representa quanto a Pin ainda deve ao médico (saldo da conta "Repasses a Médicos a Pagar").
     */
    @Query("""
        SELECT COALESCE(SUM(CASE WHEN p.tipo = :credito THEN p.valorCentavos ELSE -p.valorCentavos END), 0)
        FROM PartidaLedger p
        WHERE p.lancamento.medicoId = :medicoId
          AND p.conta.codigo = :contaCodigo
        """)
    long calcularSaldoCentavos(@Param("medicoId") UUID medicoId,
                               @Param("contaCodigo") String contaCodigo,
                               @Param("credito") TipoPartida credito);

    /**
     * Extrato bruto (sem saldo running): efeito líquido de cada lançamento do médico sobre a
     * conta de repasse, em ordem cronológica. O saldo running é acumulado na camada de serviço.
     */
    @Query("""
        SELECT new br.com.pinsaude.ledger.dto.ExtratoLinha(
            l.id, l.dataLancamento, l.competencia, l.tipoOrigem, l.descricao,
            COALESCE(SUM(CASE WHEN p.tipo = :credito THEN p.valorCentavos ELSE -p.valorCentavos END), 0))
        FROM PartidaLedger p
        JOIN p.lancamento l
        WHERE l.medicoId = :medicoId
          AND p.conta.codigo = :contaCodigo
        GROUP BY l.id, l.dataLancamento, l.competencia, l.tipoOrigem, l.descricao, l.createdAt
        ORDER BY l.dataLancamento ASC, l.createdAt ASC
        """)
    List<ExtratoLinha> extratoBruto(@Param("medicoId") UUID medicoId,
                                    @Param("contaCodigo") String contaCodigo,
                                    @Param("credito") TipoPartida credito);

    /**
     * Total (valor bruto) por lançamento = soma dos créditos (= soma dos débitos, pois é balanceado).
     * Usado para exibir o valor total na listagem sem N+1.
     */
    @Query("""
        SELECT p.lancamento.id, COALESCE(SUM(p.valorCentavos), 0)
        FROM PartidaLedger p
        WHERE p.lancamento.id IN :lancamentoIds
          AND p.tipo = :credito
        GROUP BY p.lancamento.id
        """)
    List<Object[]> totalPorLancamento(@Param("lancamentoIds") List<UUID> lancamentoIds,
                                      @Param("credito") TipoPartida credito);
}
