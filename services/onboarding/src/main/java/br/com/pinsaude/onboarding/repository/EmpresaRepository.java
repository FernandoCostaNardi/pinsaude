package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.Empresa;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmpresaRepository extends JpaRepository<Empresa, UUID> {

    Page<Empresa> findByCnpjAndAtivoTrue(String cnpj, Pageable pageable);

    Optional<Empresa> findByIdAndCnpjAndAtivoTrue(UUID id, String cnpj);

    boolean existsByCnpj(String cnpj);
}
