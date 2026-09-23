package br.com.pinsaude.gestao.service;

import br.com.pinsaude.gestao.domain.PerfilCustomizado;
import br.com.pinsaude.gestao.dto.PerfilCustomizadoRequest;
import br.com.pinsaude.gestao.dto.PerfilCustomizadoResponse;
import br.com.pinsaude.gestao.repository.PerfilCustomizadoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * CRUD de perfis de acesso customizados (PERFIL-04). Cada perfil vira uma realm role
 * composta no Keycloak (perfil_custom_&lt;uuid&gt;), cujos filhos são as roles perm_*
 * escolhidas — ver ADR-004 (docs/adr/004-permissoes-granulares.md) para o catálogo fechado.
 */
@Service
public class PerfilService {

    /** Catálogo fechado definido em PERFIL-01/ADR-004 — nunca aceitar permissão fora daqui. */
    private static final Set<String> CATALOGO_PERMISSOES = Set.of(
        "perm_medicos", "perm_empresas", "perm_tomadores", "perm_producao",
        "perm_frequencias", "perm_fechamentos", "perm_fiscal", "perm_notas",
        "perm_notas_lote", "perm_conciliacao", "perm_caixa", "perm_ledger",
        "perm_gestao", "perm_usuarios", "perm_repasses"
    );

    private final PerfilCustomizadoRepository repo;
    private final KeycloakAdminService keycloak;

    public PerfilService(PerfilCustomizadoRepository repo, KeycloakAdminService keycloak) {
        this.repo = repo;
        this.keycloak = keycloak;
    }

    @Transactional(readOnly = true)
    public List<PerfilCustomizadoResponse> listar() {
        return repo.findAll().stream()
            .map(PerfilCustomizadoResponse::from)
            .toList();
    }

    /**
     * Grava a linha primeiro (dentro da transação, reversível), só chama o Keycloak por
     * último — se a criação da role ou a montagem dos composites falhar, a transação faz
     * rollback e não sobra nenhum perfil "fantasma" sem role correspondente no Keycloak.
     */
    @Transactional
    public PerfilCustomizadoResponse criar(PerfilCustomizadoRequest req, String criadoPor) {
        validar(req, null);
        String[] permissoes = req.permissoes().toArray(new String[0]);
        PerfilCustomizado perfil = new PerfilCustomizado(req.nome().trim(), permissoes, criadoPor);
        perfil = repo.saveAndFlush(perfil);

        keycloak.createRole(perfil.getKeycloakRoleName(), "Perfil customizado: " + perfil.getNome());
        keycloak.replaceRoleComposites(perfil.getKeycloakRoleName(), req.permissoes());

        return PerfilCustomizadoResponse.from(perfil);
    }

    /**
     * Substitui os composites no Keycloak — nunca toca em nenhum usuário diretamente. A
     * expansão do papel composto acontece a cada emissão de token, então a mudança já
     * propaga sozinha no próximo login de quem tem esse perfil atribuído.
     */
    @Transactional
    public PerfilCustomizadoResponse atualizar(UUID id, PerfilCustomizadoRequest req) {
        PerfilCustomizado perfil = buscarOuFalhar(id);
        validar(req, id);
        perfil.setNome(req.nome().trim());
        perfil.setPermissoes(req.permissoes().toArray(new String[0]));
        perfil = repo.saveAndFlush(perfil);

        keycloak.replaceRoleComposites(perfil.getKeycloakRoleName(), req.permissoes());

        return PerfilCustomizadoResponse.from(perfil);
    }

    @Transactional
    public void excluir(UUID id) {
        PerfilCustomizado perfil = buscarOuFalhar(id);
        int emUso = keycloak.countUsersWithRole(perfil.getKeycloakRoleName());
        if (emUso > 0) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                "Não é possível excluir um perfil em uso por " + emUso + " colaborador(es)");
        }
        repo.delete(perfil);
        keycloak.deleteRole(perfil.getKeycloakRoleName());
    }

    private PerfilCustomizado buscarOuFalhar(UUID id) {
        return repo.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Perfil não encontrado"));
    }

    private void validar(PerfilCustomizadoRequest req, UUID idAtual) {
        if (req.nome() == null || req.nome().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome é obrigatório");
        }
        String nome = req.nome().trim();
        boolean duplicado = idAtual == null
            ? repo.existsByNomeIgnoreCase(nome)
            : repo.existsByNomeIgnoreCaseAndIdNot(nome, idAtual);
        if (duplicado) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Já existe um perfil com este nome");
        }
        if (req.permissoes() == null || req.permissoes().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione ao menos uma permissão");
        }
        for (String permissao : req.permissoes()) {
            if (!CATALOGO_PERMISSOES.contains(permissao)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Permissão inválida: " + permissao);
            }
        }
    }
}
