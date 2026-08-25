package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.SetorOperacionalModalidade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SetorOperacionalModalidadeRepository extends JpaRepository<SetorOperacionalModalidade, UUID> {
    List<SetorOperacionalModalidade> findBySetorId(UUID setorId);
    List<SetorOperacionalModalidade> findBySetorIdIn(Collection<UUID> setorIds);
    void deleteBySetorId(UUID setorId);
}
