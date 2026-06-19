package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.Servico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ServicoRepository extends JpaRepository<Servico, UUID> {
}
