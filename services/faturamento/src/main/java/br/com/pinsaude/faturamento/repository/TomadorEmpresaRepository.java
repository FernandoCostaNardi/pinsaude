package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.TomadorEmpresa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

public interface TomadorEmpresaRepository extends JpaRepository<TomadorEmpresa, UUID> {

    List<TomadorEmpresa> findByTomadorId(UUID tomadorId);

    boolean existsByTomadorIdAndEmpresaId(UUID tomadorId, UUID empresaId);

    @Transactional
    void deleteByTomadorIdAndEmpresaId(UUID tomadorId, UUID empresaId);

    @Query("SELECT te.tomadorId FROM TomadorEmpresa te WHERE te.empresaId = :empresaId")
    List<UUID> findTomadorIdsByEmpresaId(@Param("empresaId") UUID empresaId);
}
