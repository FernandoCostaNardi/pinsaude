package br.com.pinsaude.fiscal.repository;

import br.com.pinsaude.fiscal.domain.RegraEquiparacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RegraEquiparacaoRepository extends JpaRepository<RegraEquiparacao, UUID> {

    List<RegraEquiparacao> findByCnpjIdOrderByCnaeAsc(String cnpjId);

    Optional<RegraEquiparacao> findByCnpjIdAndCnaeAndCodigoLc116(
        String cnpjId, String cnae, String codigoLc116);
}
