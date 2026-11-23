package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.TomadorAliquota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TomadorAliquotaRepository extends JpaRepository<TomadorAliquota, UUID> {
    List<TomadorAliquota> findByTomadorId(UUID tomadorId);
    List<TomadorAliquota> findByTomadorIdIn(Collection<UUID> tomadorIds);
    Optional<TomadorAliquota> findByTomadorIdAndTipoTributo(UUID tomadorId, String tipoTributo);
    void deleteByTomadorId(UUID tomadorId);
}
