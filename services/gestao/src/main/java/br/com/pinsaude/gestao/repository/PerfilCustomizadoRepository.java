package br.com.pinsaude.gestao.repository;

import br.com.pinsaude.gestao.domain.PerfilCustomizado;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PerfilCustomizadoRepository extends JpaRepository<PerfilCustomizado, UUID> {

    Optional<PerfilCustomizado> findByKeycloakRoleName(String keycloakRoleName);

    boolean existsByNomeIgnoreCaseAndIdNot(String nome, UUID id);

    boolean existsByNomeIgnoreCase(String nome);
}
