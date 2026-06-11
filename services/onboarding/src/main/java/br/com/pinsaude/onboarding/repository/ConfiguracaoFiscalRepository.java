package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.ConfiguracaoFiscal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ConfiguracaoFiscalRepository extends JpaRepository<ConfiguracaoFiscal, UUID> {
    Optional<ConfiguracaoFiscal> findByEmpresaId(UUID empresaId);
}
