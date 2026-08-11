package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.TomadorHorarioPadrao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TomadorHorarioPadraoRepository extends JpaRepository<TomadorHorarioPadrao, UUID> {
    List<TomadorHorarioPadrao> findByTomadorIdOrderByOrdemAsc(UUID tomadorId);
}
