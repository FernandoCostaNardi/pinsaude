package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.TomadorCnae;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TomadorCnaeRepository extends JpaRepository<TomadorCnae, UUID> {
    List<TomadorCnae> findByTomadorId(UUID tomadorId);
    Optional<TomadorCnae> findByTomadorIdAndCodigoCnae(UUID tomadorId, String codigoCnae);
    boolean existsByTomadorIdAndCodigoCnae(UUID tomadorId, String codigoCnae);
}
