package br.com.pinsaude.faturamento.service;

import br.com.pinsaude.faturamento.domain.TomadorContato;
import br.com.pinsaude.faturamento.dto.TomadorContatoRequest;
import br.com.pinsaude.faturamento.dto.TomadorContatoResponse;
import br.com.pinsaude.faturamento.repository.TomadorContatoRepository;
import br.com.pinsaude.faturamento.repository.TomadorRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

// Contatos do tomador (V51). Separado do TomadorService para não inchar ainda mais o cadastro
// principal — o TomadorService só lê os contatos para montar o TomadorResponse.
@Service
public class TomadorContatoService {

    private final TomadorRepository tomadorRepo;
    private final TomadorContatoRepository contatoRepo;

    public TomadorContatoService(TomadorRepository tomadorRepo, TomadorContatoRepository contatoRepo) {
        this.tomadorRepo = tomadorRepo;
        this.contatoRepo = contatoRepo;
    }

    public List<TomadorContatoResponse> listar(UUID tomadorId) {
        exigirTomador(tomadorId);
        return contatoRepo.findByTomadorIdOrderByCreatedAtAsc(tomadorId).stream()
            .map(TomadorContatoResponse::from)
            .toList();
    }

    @Transactional
    public TomadorContatoResponse criar(UUID tomadorId, TomadorContatoRequest req) {
        exigirTomador(tomadorId);
        TomadorContato c = new TomadorContato();
        c.setTomadorId(tomadorId);
        aplicar(c, req);
        return TomadorContatoResponse.from(contatoRepo.save(c));
    }

    @Transactional
    public TomadorContatoResponse atualizar(UUID tomadorId, UUID contatoId, TomadorContatoRequest req) {
        TomadorContato c = findOrThrow(tomadorId, contatoId);
        aplicar(c, req);
        return TomadorContatoResponse.from(contatoRepo.save(c));
    }

    @Transactional
    public void remover(UUID tomadorId, UUID contatoId) {
        contatoRepo.delete(findOrThrow(tomadorId, contatoId));
    }

    private void aplicar(TomadorContato c, TomadorContatoRequest req) {
        String email = blankToNull(req.email());
        String telefone = blankToNull(req.telefone());
        if (email == null && telefone == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Informe o e-mail ou o telefone do contato");
        }
        c.setApelido(req.apelido().trim());
        c.setNome(blankToNull(req.nome()));
        c.setEmail(email);
        c.setTelefone(telefone);
        c.setObservacao(blankToNull(req.observacao()));
    }

    private void exigirTomador(UUID tomadorId) {
        if (!tomadorRepo.existsById(tomadorId)) {
            throw new EntityNotFoundException("Tomador não encontrado: " + tomadorId);
        }
    }

    private TomadorContato findOrThrow(UUID tomadorId, UUID contatoId) {
        return contatoRepo.findByIdAndTomadorId(contatoId, tomadorId)
            .orElseThrow(() -> new EntityNotFoundException("Contato não encontrado: " + contatoId));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
