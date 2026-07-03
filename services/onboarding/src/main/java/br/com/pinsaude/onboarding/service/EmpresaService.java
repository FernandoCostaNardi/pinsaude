package br.com.pinsaude.onboarding.service;

import br.com.pinsaude.onboarding.domain.*;
import br.com.pinsaude.onboarding.dto.*;
import br.com.pinsaude.onboarding.repository.DocumentoEmpresaRepository;
import br.com.pinsaude.onboarding.repository.EmpresaRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.util.List;
import java.util.UUID;

@Service
public class EmpresaService {

    private final EmpresaRepository repository;
    private final DocumentoEmpresaRepository documentoRepo;
    private final StorageService storageService;

    public EmpresaService(EmpresaRepository repository,
                          DocumentoEmpresaRepository documentoRepo,
                          StorageService storageService) {
        this.repository = repository;
        this.documentoRepo = documentoRepo;
        this.storageService = storageService;
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

    // ─── Documentos ──────────────────────────────────────────────────────────

    public List<DocumentoEmpresaResponse> listarDocumentos(UUID empresaId) {
        findOrThrow(empresaId);
        return documentoRepo.findListagemPrincipal(empresaId).stream()
            .map(DocumentoEmpresaResponse::from)
            .toList();
    }

    public List<DocumentoEmpresaResponse> listarHistoricoContratoSocial(UUID empresaId) {
        findOrThrow(empresaId);
        return documentoRepo.findByEmpresaIdAndTipoOrderByCreatedAtDesc(
                empresaId, TipoDocumentoEmpresa.CONTRATO_SOCIAL)
            .stream()
            .map(DocumentoEmpresaResponse::from)
            .toList();
    }

    @Transactional
    public DocumentoEmpresaResponse uploadDocumento(UUID empresaId, TipoDocumentoEmpresa tipo, MultipartFile arquivo) {
        findOrThrow(empresaId);
        if (arquivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Arquivo não pode estar vazio");
        }

        // Contrato Social: arquiva a versão anterior antes de salvar a nova
        if (tipo == TipoDocumentoEmpresa.CONTRATO_SOCIAL) {
            documentoRepo.arquivarVersoesPorTipo(empresaId, TipoDocumentoEmpresa.CONTRATO_SOCIAL);
        }

        String caminho = storageService.upload(empresaId, tipo.name(), arquivo);

        var doc = new DocumentoEmpresa();
        doc.setEmpresaId(empresaId);
        doc.setTipo(tipo);
        doc.setNomeArquivo(arquivo.getOriginalFilename());
        doc.setCaminhoStorage(caminho);
        doc.setStatusValidacao(StatusValidacaoDocumento.PENDENTE);
        doc.setVersaoAtual(true);
        doc = documentoRepo.save(doc);

        return DocumentoEmpresaResponse.from(doc);
    }

    @Transactional
    public DocumentoEmpresaResponse validarDocumento(UUID empresaId, UUID docId, ValidarDocumentoRequest req) {
        findOrThrow(empresaId);
        DocumentoEmpresa doc = documentoRepo.findById(docId)
            .filter(d -> empresaId.equals(d.getEmpresaId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Documento não encontrado: " + docId));
        if (StatusValidacaoDocumento.REPROVADO == req.statusValidacao()
                && (req.motivoReprovacao() == null || req.motivoReprovacao().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Motivo de reprovação é obrigatório ao reprovar um documento");
        }
        boolean reprovado = StatusValidacaoDocumento.REPROVADO == req.statusValidacao();
        doc.setStatusValidacao(req.statusValidacao());
        doc.setMotivoReprovacao(reprovado ? req.motivoReprovacao() : null);
        return DocumentoEmpresaResponse.from(documentoRepo.save(doc));
    }

    @Transactional
    public void deletarDocumento(UUID empresaId, UUID docId) {
        findOrThrow(empresaId);
        DocumentoEmpresa doc = documentoRepo.findById(docId)
            .filter(d -> empresaId.equals(d.getEmpresaId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Documento não encontrado: " + docId));
        storageService.delete(doc.getCaminhoStorage());
        documentoRepo.delete(doc);
    }

    public ResponseEntity<StreamingResponseBody> downloadDocumento(UUID empresaId, UUID docId) {
        findOrThrow(empresaId);
        DocumentoEmpresa doc = documentoRepo.findById(docId)
            .filter(d -> empresaId.equals(d.getEmpresaId()))
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Documento não encontrado: " + docId));

        var stream = storageService.getObjectStream(doc.getCaminhoStorage());
        StreamingResponseBody body = out -> {
            try (stream) { stream.transferTo(out); }
        };
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(resolveContentType(doc.getNomeArquivo())))
            .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + doc.getNomeArquivo() + "\"")
            .body(body);
    }

    private Empresa findOrThrow(UUID id) {
        return repository.findByIdAndAtivoTrue(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                "Empresa não encontrada: " + id));
    }

    private String resolveContentType(String filename) {
        if (filename == null) return "application/octet-stream";
        String f = filename.toLowerCase();
        if (f.endsWith(".pdf"))  return "application/pdf";
        if (f.endsWith(".jpg") || f.endsWith(".jpeg")) return "image/jpeg";
        if (f.endsWith(".png"))  return "image/png";
        return "application/octet-stream";
    }
}
