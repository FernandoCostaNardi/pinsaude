package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.Medico;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface MedicoRepository extends JpaRepository<Medico, UUID> {

    boolean existsByCrmAndCrmUf(String crm, String crmUf);

    @Query(value = "SELECT * FROM onboarding.medicos WHERE status != CAST(:status AS onboarding.status_medico_enum) ORDER BY created_at DESC",
           countQuery = "SELECT COUNT(*) FROM onboarding.medicos WHERE status != CAST(:status AS onboarding.status_medico_enum)",
           nativeQuery = true)
    Page<Medico> findAllByStatusNot(@Param("status") String status, Pageable pageable);

    @Query(value = "SELECT * FROM onboarding.medicos WHERE status = CAST(:status AS onboarding.status_medico_enum) ORDER BY created_at DESC",
           countQuery = "SELECT COUNT(*) FROM onboarding.medicos WHERE status = CAST(:status AS onboarding.status_medico_enum)",
           nativeQuery = true)
    Page<Medico> findAllByStatus(@Param("status") String status, Pageable pageable);

    @Query(value = "SELECT * FROM onboarding.medicos WHERE id = :id AND status != CAST(:status AS onboarding.status_medico_enum)",
           nativeQuery = true)
    Optional<Medico> findByIdAndStatusNot(@Param("id") UUID id, @Param("status") String status);
}
