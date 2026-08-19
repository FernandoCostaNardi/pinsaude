package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.MedicoTomador;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MedicoTomadorRepository extends JpaRepository<MedicoTomador, UUID> {

    List<MedicoTomador> findByTomadorId(UUID tomadorId);

    List<MedicoTomador> findByMedicoId(UUID medicoId);

    boolean existsByTomadorIdAndMedicoId(UUID tomadorId, UUID medicoId);

    Optional<MedicoTomador> findByTomadorIdAndMedicoId(UUID tomadorId, UUID medicoId);

    @Transactional
    void deleteByTomadorIdAndMedicoId(UUID tomadorId, UUID medicoId);

    @Query("SELECT mt.tomadorId FROM MedicoTomador mt WHERE mt.medicoId = :medicoId")
    List<UUID> findTomadorIdsByMedicoId(@Param("medicoId") UUID medicoId);
}
