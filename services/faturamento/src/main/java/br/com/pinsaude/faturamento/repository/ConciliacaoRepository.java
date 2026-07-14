package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.Conciliacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConciliacaoRepository extends JpaRepository<Conciliacao, UUID> {
    boolean existsByLancamentoExtratoId(UUID lancamentoExtratoId);
}
