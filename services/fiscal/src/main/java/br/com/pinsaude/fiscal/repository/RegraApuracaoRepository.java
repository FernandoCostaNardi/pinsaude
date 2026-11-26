package br.com.pinsaude.fiscal.repository;

import br.com.pinsaude.fiscal.domain.RegraApuracao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegraApuracaoRepository extends JpaRepository<RegraApuracao, UUID> {

    /** Regras de uma empresa e as globais, mais recentes primeiro. */
    @Query("""
        SELECT r FROM RegraApuracao r
         WHERE r.cnpjId IS NULL OR r.cnpjId = :cnpj
         ORDER BY r.competenciaInicio DESC, r.cnpjId ASC NULLS LAST
        """)
    List<RegraApuracao> listarDaEmpresaEGlobais(@Param("cnpj") String cnpj);

    List<RegraApuracao> findAllByOrderByCompetenciaInicioDesc();

    /** Regra da empresa vigente na competência (AAAA-MM: ordem lexicográfica = cronológica). */
    Optional<RegraApuracao> findFirstByCnpjIdAndCompetenciaInicioLessThanEqualOrderByCompetenciaInicioDesc(
        String cnpjId, String competencia);

    /** Padrão global vigente na competência. */
    Optional<RegraApuracao> findFirstByCnpjIdIsNullAndCompetenciaInicioLessThanEqualOrderByCompetenciaInicioDesc(
        String competencia);

    boolean existsByCnpjIdAndCompetenciaInicio(String cnpjId, String competencia);

    boolean existsByCnpjIdIsNullAndCompetenciaInicio(String competencia);
}
