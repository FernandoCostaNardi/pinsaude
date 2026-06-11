package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.ContaBancaria;
import br.com.pinsaude.onboarding.domain.Empresa;
import br.com.pinsaude.onboarding.domain.RegimeTributario;
import br.com.pinsaude.onboarding.domain.TipoConta;
import br.com.pinsaude.onboarding.dto.ContaBancariaRequest;
import br.com.pinsaude.onboarding.dto.ContaBancariaResponse;
import br.com.pinsaude.onboarding.repository.ContaBancariaRepository;
import br.com.pinsaude.onboarding.repository.EmpresaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContaBancariaServiceTest {

    @Mock ContaBancariaRepository repository;
    @Mock EmpresaRepository empresaRepository;
    @InjectMocks ContaBancariaService service;

    private static final UUID EMPRESA_ID = UUID.randomUUID();
    private static final UUID CONTA_ID   = UUID.randomUUID();

    private Empresa empresa() {
        Empresa e = new Empresa();
        e.setId(EMPRESA_ID);
        e.setCnpj("11.222.333/0001-81");
        e.setRazaoSocial("Clínica Teste");
        e.setInscricaoMunicipal("IM-001");
        e.setRegimeTributario(RegimeTributario.SIMPLES_NACIONAL);
        e.setAtivo(true);
        return e;
    }

    private ContaBancaria contaAtiva() {
        ContaBancaria cb = new ContaBancaria();
        cb.setEmpresa(empresa());
        cb.setBanco("Itaú");
        cb.setAgencia("1234");
        cb.setConta("56789-0");
        cb.setTipoConta(TipoConta.CORRENTE);
        cb.setPrincipal(false);
        cb.setAtivo(true);
        return cb;
    }

    private ContaBancariaRequest requestValido() {
        return new ContaBancariaRequest("Itaú", "1234", "56789-0", TipoConta.CORRENTE, null, false);
    }

    @Test
    void listar_retornaContasDaEmpresa() {
        when(empresaRepository.findByIdAndAtivoTrue(EMPRESA_ID))
            .thenReturn(Optional.of(empresa()));
        when(repository.findByEmpresaIdAndAtivoTrue(EMPRESA_ID))
            .thenReturn(List.of(contaAtiva()));

        List<ContaBancariaResponse> result = service.listar(EMPRESA_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).banco()).isEqualTo("Itaú");
    }

    @Test
    void listar_empresaInexistente_lancaNotFound() {
        when(empresaRepository.findByIdAndAtivoTrue(EMPRESA_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listar(EMPRESA_ID))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void criar_salvaNaRepo_retornaResponse() {
        when(empresaRepository.findByIdAndAtivoTrue(EMPRESA_ID))
            .thenReturn(Optional.of(empresa()));
        when(repository.save(any(ContaBancaria.class))).thenAnswer(inv -> inv.getArgument(0));

        ContaBancariaResponse resp = service.criar(EMPRESA_ID, requestValido());

        assertThat(resp.banco()).isEqualTo("Itaú");
        assertThat(resp.principal()).isFalse();
        verify(repository).save(any(ContaBancaria.class));
        verify(repository, never()).desmarcarPrincipal(any());
    }

    @Test
    void criar_comPrincipal_desmarcaOutrasContas() {
        when(empresaRepository.findByIdAndAtivoTrue(EMPRESA_ID))
            .thenReturn(Optional.of(empresa()));
        when(repository.save(any(ContaBancaria.class))).thenAnswer(inv -> inv.getArgument(0));

        ContaBancariaRequest req = new ContaBancariaRequest("Nubank", "0001", "12345-6", TipoConta.CORRENTE, null, true);
        service.criar(EMPRESA_ID, req);

        verify(repository).desmarcarPrincipal(EMPRESA_ID);
        ArgumentCaptor<ContaBancaria> captor = ArgumentCaptor.forClass(ContaBancaria.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isPrincipal()).isTrue();
    }

    @Test
    void atualizar_contaExistente_atualizaCampos() {
        ContaBancaria cb = contaAtiva();
        when(empresaRepository.findByIdAndAtivoTrue(EMPRESA_ID))
            .thenReturn(Optional.of(empresa()));
        when(repository.findByIdAndEmpresaIdAndAtivoTrue(CONTA_ID, EMPRESA_ID))
            .thenReturn(Optional.of(cb));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ContaBancariaRequest req = new ContaBancariaRequest("Bradesco", "9999", "11111-1", TipoConta.POUPANCA, "pix@test.com", false);
        ContaBancariaResponse resp = service.atualizar(EMPRESA_ID, CONTA_ID, req);

        assertThat(resp.banco()).isEqualTo("Bradesco");
        assertThat(resp.tipoConta()).isEqualTo(TipoConta.POUPANCA);
        assertThat(resp.chavePix()).isEqualTo("pix@test.com");
    }

    @Test
    void atualizar_contaInexistente_lancaNotFound() {
        when(empresaRepository.findByIdAndAtivoTrue(EMPRESA_ID))
            .thenReturn(Optional.of(empresa()));
        when(repository.findByIdAndEmpresaIdAndAtivoTrue(CONTA_ID, EMPRESA_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.atualizar(EMPRESA_ID, CONTA_ID, requestValido()))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void deletar_fazSoftDelete() {
        ContaBancaria cb = contaAtiva();
        when(empresaRepository.findByIdAndAtivoTrue(EMPRESA_ID))
            .thenReturn(Optional.of(empresa()));
        when(repository.findByIdAndEmpresaIdAndAtivoTrue(CONTA_ID, EMPRESA_ID))
            .thenReturn(Optional.of(cb));

        service.deletar(EMPRESA_ID, CONTA_ID);

        ArgumentCaptor<ContaBancaria> captor = ArgumentCaptor.forClass(ContaBancaria.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().isAtivo()).isFalse();
    }

    @Test
    void deletar_contaInexistente_lancaNotFound() {
        when(empresaRepository.findByIdAndAtivoTrue(EMPRESA_ID))
            .thenReturn(Optional.of(empresa()));
        when(repository.findByIdAndEmpresaIdAndAtivoTrue(CONTA_ID, EMPRESA_ID))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deletar(EMPRESA_ID, CONTA_ID))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND));
    }
}
