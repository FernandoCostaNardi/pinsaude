package br.com.pinsaude.fiscal.repository;

import br.com.pinsaude.fiscal.domain.CronogramaReforma;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CronogramaReformaRepository extends JpaRepository<CronogramaReforma, Short> {

    List<CronogramaReforma> findAllByOrderByAnoAsc();
}
