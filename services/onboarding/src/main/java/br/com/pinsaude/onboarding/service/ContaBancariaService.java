package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.ContaBancaria;
import br.com.pinsaude.onboarding.domain.Empresa;
import br.com.pinsaude.onboarding.dto.ContaBancariaRequest;
import br.com.pinsaude.onboarding.dto.ContaBancariaResponse;
import br.com.pinsaude.onboarding.repository.ContaBancariaRepository;
import br.com.pinsaude.onboarding.repository.EmpresaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ContaBancariaService {

    private final ContaBancariaRepository repository;
    private final EmpresaRepository empresaRepository;

    public ContaBancariaService(ContaBancariaRepository repository, EmpresaRepository empresaRepository) {
        this.repository = repository;
        this.empresaRepository = empresaRepository;
    }

    public List<ContaBancariaResponse> listar(UUID empresaId, String tenantCnpj) {
        verificarTenant(empresaId, tenantCnpj);
        return repository.findByEmpresaIdAndAtivoTrue(empresaId)
                         .stream().map(ContaBancariaResponse::from).toList();
    }

    @Transactional
    public ContaBancariaResponse criar(UUID empresaId, ContaBancariaRequest request, String tenantCnpj) {
        Empresa empresa = verificarTenant(empresaId, tenantCnpj);
        if (request.principal()) {
            repository.desmarcarPrincipal(empresaId);
        }
        ContaBancaria cb = new ContaBancaria();
        cb.setEmpresa(empresa);
        cb.setBanco(request.banco());
        cb.setAgencia(request.agencia());
        cb.setConta(request.conta());
        cb.setTipoConta(request.tipoConta());
        cb.setChavePix(request.chavePix());
        cb.setPrincipal(request.principal());
        return ContaBancariaResponse.from(repository.save(cb));
    }

    @Transactional
    public ContaBancariaResponse atualizar(UUID empresaId, UUID id, ContaBancariaRequest request, String tenantCnpj) {
        verificarTenant(empresaId, tenantCnpj);
        ContaBancaria cb = repository.findByIdAndEmpresaIdAndAtivoTrue(id, empresaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Conta bancária não encontrada: " + id));
        if (request.principal() && !cb.isPrincipal()) {
            repository.desmarcarPrincipal(empresaId);
        }
        cb.setBanco(request.banco());
        cb.setAgencia(request.agencia());
        cb.setConta(request.conta());
        cb.setTipoConta(request.tipoConta());
        cb.setChavePix(request.chavePix());
        cb.setPrincipal(request.principal());
        return ContaBancariaResponse.from(repository.save(cb));
    }

    @Transactional
    public void deletar(UUID empresaId, UUID id, String tenantCnpj) {
        verificarTenant(empresaId, tenantCnpj);
        ContaBancaria cb = repository.findByIdAndEmpresaIdAndAtivoTrue(id, empresaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Conta bancária não encontrada: " + id));
        cb.setAtivo(false);
        repository.save(cb);
    }

    private Empresa verificarTenant(UUID empresaId, String tenantCnpj) {
        return empresaRepository.findByIdAndCnpjAndAtivoTrue(empresaId, tenantCnpj)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Empresa não encontrada: " + empresaId));
    }
}
