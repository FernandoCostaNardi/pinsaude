package br.com.pinsaude.fiscal.repository;

import br.com.pinsaude.fiscal.domain.ParametroFiscal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ParametroFiscalRepository extends JpaRepository<ParametroFiscal, UUID> {

    List<ParametroFiscal> findByCnpjIdOrderByVigenciaInicioDesc(String cnpjId);

    List<ParametroFiscal> findAllByOrderByVigenciaInicioDesc();

    /** Retorna o parâmetro mais recente válido para a competência do fato gerador. */
    Optional<ParametroFiscal> findFirstByCnpjIdAndVigenciaInicioLessThanEqualOrderByVigenciaInicioDesc(
            String cnpjId, LocalDate dataReferencia);

    /** Próximo parâmetro da empresa depois de uma vigência (delimita o período coberto por um parâmetro). */
    Optional<ParametroFiscal> findFirstByCnpjIdAndVigenciaInicioGreaterThanOrderByVigenciaInicioAsc(
            String cnpjId, LocalDate vigencia);

    Optional<ParametroFiscal> findByCnpjIdAndVigenciaInicio(String cnpjId, LocalDate vigencia);
}
