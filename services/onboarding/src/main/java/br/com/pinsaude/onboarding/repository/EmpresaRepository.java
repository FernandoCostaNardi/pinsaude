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

    Page<Empresa> findAllByAtivoTrue(Pageable pageable);

    Optional<Empresa> findByIdAndAtivoTrue(UUID id);

    boolean existsByCnpj(String cnpj);
}
