package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.TomadorServicoOperacional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TomadorServicoOperacionalRepository extends JpaRepository<TomadorServicoOperacional, UUID> {
    List<TomadorServicoOperacional> findByTomadorIdOrderByNomeAsc(UUID tomadorId);
    List<TomadorServicoOperacional> findByGrupoIdOrderByNomeAsc(UUID grupoId);
    List<TomadorServicoOperacional> findByTomadorIdAndAtivoTrueOrderByNomeAsc(UUID tomadorId);
    List<TomadorServicoOperacional> findByGrupoIdAndAtivoTrueOrderByNomeAsc(UUID grupoId);
}
