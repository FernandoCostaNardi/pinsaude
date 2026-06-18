package br.com.pinsaude.faturamento.repository;

import br.com.pinsaude.faturamento.domain.Tomador;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TomadorRepository extends JpaRepository<Tomador, UUID> {

    List<Tomador> findByRazaoSocialNomeContainingIgnoreCase(String q);
}
