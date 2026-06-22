package br.com.pinsaude.fiscal.repository;

import br.com.pinsaude.fiscal.domain.HistoricoRegraEquiparacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface HistoricoRegraEquiparacaoRepository extends JpaRepository<HistoricoRegraEquiparacao, UUID> {

    List<HistoricoRegraEquiparacao> findByRegraIdOrderByAlteradoEmDesc(UUID regraId);
}
