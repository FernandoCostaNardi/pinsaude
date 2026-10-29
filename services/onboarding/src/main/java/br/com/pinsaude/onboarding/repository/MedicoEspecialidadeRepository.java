package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.MedicoEspecialidade;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface MedicoEspecialidadeRepository extends JpaRepository<MedicoEspecialidade, UUID> {
    List<MedicoEspecialidade> findByMedicoIdOrderByOrdemAsc(UUID medicoId);

    @Modifying(flushAutomatically = true)
    @Query("DELETE FROM MedicoEspecialidade me WHERE me.medicoId = :medicoId")
    void deleteAllByMedicoId(@Param("medicoId") UUID medicoId);
}
