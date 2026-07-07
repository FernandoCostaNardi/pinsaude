package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.TomadorAliquota;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TomadorAliquotaRepository extends JpaRepository<TomadorAliquota, UUID> {
    List<TomadorAliquota> findByTomadorId(UUID tomadorId);
    Optional<TomadorAliquota> findByTomadorIdAndTipoTributo(UUID tomadorId, String tipoTributo);
    void deleteByTomadorId(UUID tomadorId);
}
