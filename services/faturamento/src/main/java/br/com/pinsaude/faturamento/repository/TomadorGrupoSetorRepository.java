package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.TomadorGrupoSetor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface TomadorGrupoSetorRepository extends JpaRepository<TomadorGrupoSetor, UUID> {
    List<TomadorGrupoSetor> findByGrupoId(UUID grupoId);
    List<TomadorGrupoSetor> findByGrupoIdIn(Collection<UUID> grupoIds);
    boolean existsByGrupoIdAndSetorId(UUID grupoId, UUID setorId);
    void deleteByGrupoIdAndSetorId(UUID grupoId, UUID setorId);
}
