package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.Conciliacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConciliacaoRepository extends JpaRepository<Conciliacao, UUID> {
    boolean existsByLancamentoExtratoId(UUID lancamentoExtratoId);
    Optional<Conciliacao> findByLancamentoExtratoId(UUID lancamentoExtratoId);
    List<Conciliacao> findByLancamentoExtratoIdIn(List<UUID> lancamentoIds);

    @Transactional
    void deleteByLancamentoExtratoId(UUID lancamentoExtratoId);
}
