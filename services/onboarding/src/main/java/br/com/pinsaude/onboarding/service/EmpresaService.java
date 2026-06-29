package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.Empresa;
import br.com.pinsaude.onboarding.dto.EmpresaPageResponse;
import br.com.pinsaude.onboarding.dto.EmpresaRequest;
import br.com.pinsaude.onboarding.dto.EmpresaResponse;
import br.com.pinsaude.onboarding.repository.EmpresaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class EmpresaService {

    private final EmpresaRepository repository;

    public EmpresaService(EmpresaRepository repository) {
        this.repository = repository;
    }

    public EmpresaPageResponse listar(int page, int size) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return EmpresaPageResponse.from(
            repository.findAllByAtivoTrue(pageable).map(EmpresaResponse::from)
        );
    }

    public EmpresaResponse buscarPorId(UUID id) {
        return repository.findByIdAndAtivoTrue(id)
            .map(EmpresaResponse::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Empresa não encontrada: " + id));
    }

    @Transactional
    public EmpresaResponse criar(EmpresaRequest request) {
        if (repository.existsByCnpj(request.cnpj())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Já existe uma empresa com este CNPJ");
        }
        var empresa = new Empresa();
        empresa.setCnpj(request.cnpj());
        empresa.setRazaoSocial(request.razaoSocial());
        empresa.setInscricaoMunicipal(request.inscricaoMunicipal());
        empresa.setMunicipio(request.municipio());
        empresa.setCodigoMunicipioIbge(request.codigoMunicipioIbge());
        empresa.setRegimeTributario(request.regimeTributario());
        empresa.setLogradouro(request.logradouro());
        empresa.setBairro(request.bairro());
        empresa.setUf(request.uf());
        empresa.setCep(request.cep());
        empresa.setTelefone(request.telefone());
        empresa.setEmailContato(request.emailContato());
        return EmpresaResponse.from(repository.save(empresa));
    }

    @Transactional
    public EmpresaResponse atualizar(UUID id, EmpresaRequest request) {
        Empresa empresa = repository.findByIdAndAtivoTrue(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Empresa não encontrada: " + id));

        empresa.setRazaoSocial(request.razaoSocial());
        empresa.setInscricaoMunicipal(request.inscricaoMunicipal());
        empresa.setMunicipio(request.municipio());
        empresa.setCodigoMunicipioIbge(request.codigoMunicipioIbge());
        empresa.setRegimeTributario(request.regimeTributario());
        empresa.setLogradouro(request.logradouro());
        empresa.setBairro(request.bairro());
        empresa.setUf(request.uf());
        empresa.setCep(request.cep());
        empresa.setTelefone(request.telefone());
        empresa.setEmailContato(request.emailContato());
        return EmpresaResponse.from(repository.save(empresa));
    }

    @Transactional
    public void deletar(UUID id) {
        Empresa empresa = repository.findByIdAndAtivoTrue(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Empresa não encontrada: " + id));
        empresa.setAtivo(false);
        repository.save(empresa);
    }
}
