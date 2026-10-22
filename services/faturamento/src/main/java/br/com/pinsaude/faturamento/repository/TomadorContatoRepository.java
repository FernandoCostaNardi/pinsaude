package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.TomadorContato;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TomadorContatoRepository extends JpaRepository<TomadorContato, UUID> {
    List<TomadorContato> findByTomadorIdOrderByCreatedAtAsc(UUID tomadorId);
    Optional<TomadorContato> findByIdAndTomadorId(UUID id, UUID tomadorId);
}
