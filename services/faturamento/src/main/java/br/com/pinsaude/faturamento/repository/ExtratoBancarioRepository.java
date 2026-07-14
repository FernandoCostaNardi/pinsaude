package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.ExtratoBancario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ExtratoBancarioRepository extends JpaRepository<ExtratoBancario, UUID> {

    boolean existsByNomeArquivoAndPeriodoInicioAndPeriodoFimAndCnpjIdTenant(
            String nomeArquivo, LocalDate periodoInicio, LocalDate periodoFim, String cnpjIdTenant);

    List<ExtratoBancario> findAllByCnpjIdTenantOrderByDataUploadDesc(String cnpjIdTenant);
}
