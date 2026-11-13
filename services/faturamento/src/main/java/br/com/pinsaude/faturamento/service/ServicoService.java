package br.com.pinsaude.faturamento.service;

import br.com.pinsaude.faturamento.domain.Servico;
import br.com.pinsaude.faturamento.dto.ServicoClassificacaoRequest;
import br.com.pinsaude.faturamento.dto.ServicoResponse;
import br.com.pinsaude.faturamento.repository.ServicoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class ServicoService {

    private final ServicoRepository repo;

    public ServicoService(ServicoRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<ServicoResponse> listar() {
        return repo.findAll().stream()
            .sorted(Comparator.comparing(Servico::getCodigoLc116))
            .map(ServicoResponse::from)
            .toList();
    }

    /** CBS-12 — grava NBS, CST, cClassTrib e redução de IBS/CBS do serviço. */
    @Transactional
    public ServicoResponse atualizarClassificacao(UUID id, ServicoClassificacaoRequest req) {
        Servico s = repo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço não encontrado"));
        s.setNbs(req.nbs());
        s.setCstIbsCbs(req.cstIbsCbs());
        s.setCClassTrib(req.cClassTrib());
        s.setReducaoIbsCbs(req.reducaoIbsCbs());
        return ServicoResponse.from(repo.save(s));
    }
}
