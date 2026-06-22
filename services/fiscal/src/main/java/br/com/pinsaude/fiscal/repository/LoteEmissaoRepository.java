package br.com.pinsaude.fiscal.repository;

import br.com.pinsaude.fiscal.domain.LoteEmissao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LoteEmissaoRepository extends JpaRepository<LoteEmissao, UUID> {

    boolean existsByCompetenciaAndStatus(String competencia, String status);

    Optional<LoteEmissao> findFirstByCompetenciaAndStatusOrderByIniciadoEmDesc(String competencia, String status);

    List<LoteEmissao> findAllByOrderByIniciadoEmDesc();

    // Incremento atômico via native SQL — evita race condition em processamento paralelo
    @Modifying
    @Query(value = """
        UPDATE fiscal.lotes_emissao
           SET emitidas = emitidas + 1,
               em_processamento = GREATEST(em_processamento - 1, 0)
         WHERE competencia = :comp AND status = 'EM_ANDAMENTO'
        """, nativeQuery = true)
    int incrementarEmitida(@Param("comp") String competencia);

    @Modifying
    @Query(value = """
        UPDATE fiscal.lotes_emissao
           SET falhas = falhas + 1,
               em_processamento = GREATEST(em_processamento - 1, 0)
         WHERE competencia = :comp AND status = 'EM_ANDAMENTO'
        """, nativeQuery = true)
    int incrementarFalha(@Param("comp") String competencia);
}
