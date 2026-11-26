package br.com.pinsaude.fiscal.repository;

import br.com.pinsaude.fiscal.domain.NotaFiscal;
import br.com.pinsaude.fiscal.domain.RegimeTributario;
import br.com.pinsaude.fiscal.domain.StatusNota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotaFiscalRepository extends JpaRepository<NotaFiscal, UUID> {

    Optional<NotaFiscal> findByProducaoId(UUID producaoId);

    boolean existsByProducaoId(UUID producaoId);

    boolean existsByMedicoIdAndStatus(UUID medicoId, StatusNota status);

    List<NotaFiscal> findAllByStatus(StatusNota status);

    List<NotaFiscal> findAllByOrderByCreatedAtDesc();

    // Queries para processamento em lote (EPIC-05.7)
    List<NotaFiscal> findByCompetenciaAndStatusIn(String competencia, Collection<StatusNota> statuses);

    @Query("SELECT COUNT(n) FROM NotaFiscal n WHERE n.competencia = :comp AND n.status = :status")
    int countByCompetenciaAndStatus(@Param("comp") String competencia, @Param("status") StatusNota status);

    /** CBS-14: há nota (não cancelada/rejeitada) da empresa entre as competências [de, ate). */
    @Query("""
        SELECT COUNT(n) > 0 FROM NotaFiscal n
         WHERE n.cnpjIdTenant = :cnpj
           AND n.competencia >= :de AND n.competencia < :ate
           AND n.status NOT IN :ignorar
        """)
    boolean existeNotaNoPeriodo(@Param("cnpj") String cnpj, @Param("de") String de,
                                @Param("ate") String ate, @Param("ignorar") Collection<StatusNota> ignorar);

    /** CBS-14: idem, só notas calculadas num regime específico. */
    @Query("""
        SELECT COUNT(n) > 0 FROM NotaFiscal n
         WHERE n.cnpjIdTenant = :cnpj
           AND n.competencia >= :de AND n.competencia < :ate
           AND n.status NOT IN :ignorar
           AND n.regimeTributario = :regime
        """)
    boolean existeNotaNoPeriodoNoRegime(@Param("cnpj") String cnpj, @Param("de") String de,
                                        @Param("ate") String ate, @Param("ignorar") Collection<StatusNota> ignorar,
                                        @Param("regime") RegimeTributario regime);

    /**
     * CBS-32: soma dos tributos das notas EMITIDAS no intervalo [de, ate) da data de emissão, por regime.
     * Cada linha: regime, quantidade, bruto, ISS, IR, CSLL, PIS, COFINS, CBS, IBS (centavos).
     * Isolamento por empresa pelo RLS (gestão vê todas).
     */
    @Query("""
        SELECT n.regimeTributario, COUNT(n), SUM(n.valorBruto),
               SUM(n.valorIss), SUM(n.valorIr), SUM(n.valorCsll), SUM(n.valorPis), SUM(n.valorCofins),
               SUM(n.valorCbs), SUM(n.valorIbs)
          FROM NotaFiscal n
         WHERE n.status = :status
           AND n.emitidaAt >= :de AND n.emitidaAt < :ate
         GROUP BY n.regimeTributario
        """)
    List<Object[]> somarTributosPorRegime(@Param("status") StatusNota status,
                                         @Param("de") java.time.OffsetDateTime de,
                                         @Param("ate") java.time.OffsetDateTime ate);

    /** APU-01: notas da empresa num status com competência (AAAA-MM) entre {@code de} e {@code ate}, inclusive. */
    @Query("""
        SELECT n FROM NotaFiscal n
         WHERE n.cnpjIdTenant = :cnpj AND n.status = :status
           AND n.competencia >= :de AND n.competencia <= :ate
        """)
    List<NotaFiscal> findDaEmpresaPorCompetencia(@Param("cnpj") String cnpj, @Param("status") StatusNota status,
                                                 @Param("de") String de, @Param("ate") String ate);

    /** APU-01: notas da empresa num status emitidas no intervalo [de, ate). */
    @Query("""
        SELECT n FROM NotaFiscal n
         WHERE n.cnpjIdTenant = :cnpj AND n.status = :status
           AND n.emitidaAt >= :de AND n.emitidaAt < :ate
        """)
    List<NotaFiscal> findDaEmpresaPorEmissao(@Param("cnpj") String cnpj, @Param("status") StatusNota status,
                                             @Param("de") java.time.OffsetDateTime de,
                                             @Param("ate") java.time.OffsetDateTime ate);

    /** APU-01: notas da empresa num status das produções informadas (recebidas). */
    @Query("""
        SELECT n FROM NotaFiscal n
         WHERE n.cnpjIdTenant = :cnpj AND n.status = :status AND n.producaoId IN :producoes
        """)
    List<NotaFiscal> findDaEmpresaPorProducoes(@Param("cnpj") String cnpj, @Param("status") StatusNota status,
                                               @Param("producoes") Collection<UUID> producoes);
}
