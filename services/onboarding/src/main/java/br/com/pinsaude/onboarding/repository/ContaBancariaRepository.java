package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.ContaBancaria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContaBancariaRepository extends JpaRepository<ContaBancaria, UUID> {

    List<ContaBancaria> findByEmpresaIdAndAtivoTrue(UUID empresaId);

    Optional<ContaBancaria> findByIdAndEmpresaIdAndAtivoTrue(UUID id, UUID empresaId);

    @Modifying
    @Query("UPDATE ContaBancaria c SET c.principal = false WHERE c.empresa.id = :empresaId AND c.ativo = true")
    void desmarcarPrincipal(@Param("empresaId") UUID empresaId);
}
