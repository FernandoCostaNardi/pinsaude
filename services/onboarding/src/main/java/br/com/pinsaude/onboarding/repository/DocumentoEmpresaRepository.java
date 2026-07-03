package br.com.pinsaude.onboarding.repository;

import br.com.pinsaude.onboarding.domain.DocumentoEmpresa;
import br.com.pinsaude.onboarding.domain.TipoDocumentoEmpresa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentoEmpresaRepository extends JpaRepository<DocumentoEmpresa, UUID> {

    // Listagem principal: todos os tipos, exceto versões antigas de Contrato Social
    @Query("""
        SELECT d FROM DocumentoEmpresa d
        WHERE d.empresaId = :empresaId
          AND (d.tipo <> br.com.pinsaude.onboarding.domain.TipoDocumentoEmpresa.CONTRATO_SOCIAL
               OR d.versaoAtual = true)
        ORDER BY d.createdAt ASC
        """)
    List<DocumentoEmpresa> findListagemPrincipal(@Param("empresaId") UUID empresaId);

    // Histórico completo de Contratos Sociais (todos, do mais recente ao mais antigo)
    List<DocumentoEmpresa> findByEmpresaIdAndTipoOrderByCreatedAtDesc(
            UUID empresaId, TipoDocumentoEmpresa tipo);

    // Busca a versão atual do Contrato Social
    Optional<DocumentoEmpresa> findByEmpresaIdAndTipoAndVersaoAtualTrue(
            UUID empresaId, TipoDocumentoEmpresa tipo);

    // Arquiva versões anteriores de um tipo (marca versaoAtual = false)
    // Native query necessária: JPQL não aplica @ColumnTransformer em @Modifying, causando
    // "operator does not exist: tipo_documento_empresa_enum = character varying"
    @Modifying
    @Query(value = "UPDATE onboarding.documentos_empresa SET versao_atual = false WHERE empresa_id = :empresaId AND tipo = CAST(:tipo AS onboarding.tipo_documento_empresa_enum)", nativeQuery = true)
    void arquivarVersoesPorTipo(
            @Param("empresaId") UUID empresaId,
            @Param("tipo") String tipo);
}
