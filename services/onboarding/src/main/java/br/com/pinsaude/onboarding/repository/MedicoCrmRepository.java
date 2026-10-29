package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.MedicoCrm;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicoCrmRepository extends JpaRepository<MedicoCrm, UUID> {
    List<MedicoCrm> findByMedicoIdOrderByPrincipalDescUfAsc(UUID medicoId);

    Optional<MedicoCrm> findByNumeroAndUf(String numero, String uf);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM MedicoCrm c WHERE c.medicoId = :medicoId")
    void deleteAllByMedicoId(@Param("medicoId") UUID medicoId);
}
