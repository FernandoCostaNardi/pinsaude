package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.TomadorModalidade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TomadorModalidadeRepository extends JpaRepository<TomadorModalidade, UUID> {
    List<TomadorModalidade> findByTomadorIdOrderByOrdemAscNomeAsc(UUID tomadorId);
    List<TomadorModalidade> findByTomadorIdAndAtivoTrueOrderByOrdemAscNomeAsc(UUID tomadorId);
}
