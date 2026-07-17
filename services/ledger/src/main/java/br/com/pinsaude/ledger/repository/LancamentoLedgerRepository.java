package br.com.pinsaude.ledger.repository;

import br.com.pinsaude.ledger.domain.LancamentoLedger;
import br.com.pinsaude.ledger.domain.TipoOrigem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface LancamentoLedgerRepository extends JpaRepository<LancamentoLedger, UUID> {

    boolean existsByCorrelationId(String correlationId);

    Optional<LancamentoLedger> findByCorrelationId(String correlationId);

    /**
     * Listagem paginada com filtros opcionais (médico, tipo de origem e intervalo de datas).
     * Colunas VARCHAR (não enum nativo) permitem comparação direta em JPQL — sem CAST.
     */
    @Query("""
        SELECT l FROM LancamentoLedger l
        WHERE (:medicoId IS NULL OR l.medicoId = :medicoId)
          AND (:tipoOrigem IS NULL OR l.tipoOrigem = :tipoOrigem)
          AND (:dataInicio IS NULL OR l.dataLancamento >= :dataInicio)
          AND (:dataFim IS NULL OR l.dataLancamento <= :dataFim)
        """)
    Page<LancamentoLedger> buscar(@Param("medicoId") UUID medicoId,
                                  @Param("tipoOrigem") TipoOrigem tipoOrigem,
                                  @Param("dataInicio") LocalDate dataInicio,
                                  @Param("dataFim") LocalDate dataFim,
                                  Pageable pageable);

    /** Detalhe com todas as partidas e suas contas (evita N+1 na tela de detalhe). */
    @Query("""
        SELECT DISTINCT l FROM LancamentoLedger l
        LEFT JOIN FETCH l.partidas p
        LEFT JOIN FETCH p.conta
        WHERE l.id = :id
        """)
    Optional<LancamentoLedger> findByIdComPartidas(@Param("id") UUID id);
}
