package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.TomadorGrupoFaturamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TomadorGrupoFaturamentoRepository extends JpaRepository<TomadorGrupoFaturamento, UUID> {
    List<TomadorGrupoFaturamento> findByTomadorIdOrderByOrdemAscNomeAsc(UUID tomadorId);
    List<TomadorGrupoFaturamento> findByTomadorIdAndAtivoTrueOrderByOrdemAscNomeAsc(UUID tomadorId);
}
