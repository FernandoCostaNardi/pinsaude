package br.com.pinsaude.faturamento.tomador;

import br.com.pinsaude.faturamento.domain.TomadorContato;
import br.com.pinsaude.faturamento.dto.TomadorContatoRequest;
import br.com.pinsaude.faturamento.dto.TomadorContatoResponse;
import br.com.pinsaude.faturamento.repository.TomadorContatoRepository;
import br.com.pinsaude.faturamento.repository.TomadorRepository;
import br.com.pinsaude.faturamento.service.TomadorContatoService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TomadorContatoServiceTest {

    @Mock TomadorRepository tomadorRepo;
    @Mock TomadorContatoRepository contatoRepo;

    @InjectMocks TomadorContatoService service;

    private final UUID tomadorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(tomadorRepo.existsById(tomadorId)).thenReturn(true);
        when(contatoRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void criar_comEmailETelefone_normalizaEPersiste() {
        var req = new TomadorContatoRequest("  Financeiro ", " Fernando ", "fin@hospital.com.br",
            "(81) 99999-0000", "  Pessoa boa  ");

        TomadorContatoResponse res = service.criar(tomadorId, req);

        assertThat(res.tomadorId()).isEqualTo(tomadorId);
        assertThat(res.apelido()).isEqualTo("Financeiro");
        assertThat(res.nome()).isEqualTo("Fernando");
        assertThat(res.email()).isEqualTo("fin@hospital.com.br");
        assertThat(res.telefone()).isEqualTo("(81) 99999-0000");
        assertThat(res.observacao()).isEqualTo("Pessoa boa");
    }

    @Test
    void criar_somenteTelefone_camposVaziosViramNull() {
        var req = new TomadorContatoRequest("Compras", "", "  ", "81 3333-4444", "");

        TomadorContatoResponse res = service.criar(tomadorId, req);

        assertThat(res.nome()).isNull();
        assertThat(res.email()).isNull();
        assertThat(res.telefone()).isEqualTo("81 3333-4444");
        assertThat(res.observacao()).isNull();
    }

    @Test
    void criar_somenteEmail_aceita() {
        var req = new TomadorContatoRequest("Compras", null, "compras@x.com", null, null);

        assertThat(service.criar(tomadorId, req).email()).isEqualTo("compras@x.com");
    }

    @Test
    void criar_semEmailNemTelefone_retorna400() {
        var req = new TomadorContatoRequest("Financeiro", "Fernando", " ", null, "obs");

        assertThatThrownBy(() -> service.criar(tomadorId, req))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(contatoRepo, never()).save(any());
    }

    @Test
    void criar_tomadorInexistente_lancaNotFound() {
        UUID outro = UUID.randomUUID();
        when(tomadorRepo.existsById(outro)).thenReturn(false);
        var req = new TomadorContatoRequest("Financeiro", null, "a@b.com", null, null);

        assertThatThrownBy(() -> service.criar(outro, req)).isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void atualizar_contatoDoTomador_sobrescreveCampos() {
        UUID contatoId = UUID.randomUUID();
        TomadorContato existente = new TomadorContato();
        existente.setTomadorId(tomadorId);
        existente.setApelido("Financeiro");
        existente.setEmail("velho@x.com");
        when(contatoRepo.findByIdAndTomadorId(contatoId, tomadorId)).thenReturn(Optional.of(existente));

        var req = new TomadorContatoRequest("Financeiro", "Maria", null, "81 2222-1111", null);
        service.atualizar(tomadorId, contatoId, req);

        var captor = ArgumentCaptor.forClass(TomadorContato.class);
        verify(contatoRepo).save(captor.capture());
        assertThat(captor.getValue().getNome()).isEqualTo("Maria");
        assertThat(captor.getValue().getEmail()).isNull();
        assertThat(captor.getValue().getTelefone()).isEqualTo("81 2222-1111");
    }

    @Test
    void remover_contatoDeOutroTomador_lancaNotFound() {
        UUID contatoId = UUID.randomUUID();
        when(contatoRepo.findByIdAndTomadorId(contatoId, tomadorId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.remover(tomadorId, contatoId))
            .isInstanceOf(EntityNotFoundException.class);
        verify(contatoRepo, never()).delete(any());
    }
}
