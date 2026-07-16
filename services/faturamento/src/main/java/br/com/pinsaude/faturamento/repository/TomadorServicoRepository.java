package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.TomadorServico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TomadorServicoRepository extends JpaRepository<TomadorServico, UUID> {
    List<TomadorServico> findByTomadorId(UUID tomadorId);
    boolean existsByTomadorIdAndServicoId(UUID tomadorId, UUID servicoId);
}
