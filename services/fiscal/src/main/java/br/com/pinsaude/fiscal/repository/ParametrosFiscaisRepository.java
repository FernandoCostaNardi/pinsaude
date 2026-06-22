package br.com.pinsaude.fiscal.repository;

import br.com.pinsaude.fiscal.domain.ParametrosFiscais;
import br.com.pinsaude.fiscal.domain.TipoTributo;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ParametrosFiscaisRepository extends JpaRepository<ParametrosFiscais, UUID> {

    /**
     * Retorna o parâmetro mais recente vigente para um tributo numa competência,
     * respeitando competencia_fim quando definido.
     */
    @Query("""
        SELECT p FROM ParametrosFiscais p
        WHERE p.cnpjId = :cnpjId
          AND p.tipoTributo = :tipoTributo
          AND p.competenciaInicio <= :competencia
          AND (p.competenciaFim IS NULL OR p.competenciaFim >= :competencia)
        ORDER BY p.competenciaInicio DESC
        """)
    List<ParametrosFiscais> findVigenteParaTributo(
        @Param("cnpjId") String cnpjId,
        @Param("tipoTributo") TipoTributo tipoTributo,
        @Param("competencia") String competencia,
        Pageable pageable);
}
