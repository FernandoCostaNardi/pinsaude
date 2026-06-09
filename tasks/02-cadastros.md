# EPIC-02 — Cadastros

> Prioridade: **P1** — Base para onboarding, emissão e ledger.
> PRD: §7.2. RFs: RF-CAD-01..05

---

## TASK-02.1 — Cadastro de Empresa (CNPJ)

### 1. Objetivo (Por quê?)
A empresa (CNPJ) é o tenant da plataforma. Sem o cadastro completo da empresa — com certificado A1, inscrição municipal e conta bancária — não é possível emitir NFS-e nem receber/repassar. É o primeiro cadastro a ser feito pela operação ao configurar um novo CNPJ.

### 2. Descrição da Solução (O quê?)
CRUD completo da entidade `Empresa` no serviço `onboarding` (ou `auth`), com upload de A1 para o cofre de segredos e validação de CNPJ na Receita Federal.

**Migração Flyway (`onboarding` schema — `V1__create_empresa.sql`):**
```sql
CREATE TABLE onboarding.empresa (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id          UUID NOT NULL UNIQUE,  -- FK lógica para auth.empresa.id
  cnpj             VARCHAR(14) NOT NULL UNIQUE,
  razao_social     VARCHAR(255) NOT NULL,
  nome_fantasia    VARCHAR(255),
  inscricao_municipal VARCHAR(50),
  regime_tributario VARCHAR(20) NOT NULL DEFAULT 'LUCRO_PRESUMIDO',
  municipio        VARCHAR(100) NOT NULL,  -- 'Olinda', 'Eusébio', etc.
  codigo_municipio_ibge VARCHAR(10),
  email_contato    VARCHAR(255),
  telefone         VARCHAR(20),
  endereco_jsonb   JSONB,          -- endereço completo em JSONB
  conta_bancaria_jsonb JSONB,      -- banco, agencia, conta, pix_key (criptografado)
  certificado_a1_vault_path VARCHAR(500),  -- caminho no Vault (não o PFX!)
  certificado_a1_validade TIMESTAMPTZ,
  ativo            BOOLEAN NOT NULL DEFAULT false,  -- ativo após configuração completa
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

**Endpoints REST (`/empresas`):**
```
POST   /empresas                          → criar empresa (role: GESTAO)
GET    /empresas/{id}                     → detalhe (role: GESTAO, CONTABIL)
PUT    /empresas/{id}                     → atualizar dados (role: GESTAO)
POST   /empresas/{id}/certificado-a1      → upload do PFX (multipart, role: GESTAO)
POST   /empresas/{id}/ativar              → ativar empresa (role: GESTAO)
GET    /empresas                          → listar (role: GESTAO)
```

**Upload do A1 (`POST /empresas/{id}/certificado-a1`):**
1. Receber arquivo PFX + senha via multipart.
2. Validar que o certificado é válido e o CNPJ bate.
3. Gravar no Vault em `secret/pinsaude/cnpj/{cnpj_id}/certificado-a1`.
4. Salvar `certificado_a1_vault_path` e `certificado_a1_validade` na tabela.
5. **Nunca persistir o PFX no banco ou disco.**

**Validação de CNPJ na Receita:**
- Chamar adapter `ReceitaFederalPort` (consulta de CNPJ público).
- Preencher `razao_social` automaticamente.
- É o adapter mais simples — GET em API pública, sem autenticação.

**DTO de criação:**
```json
{
  "cnpj": "12345678000199",
  "municipio": "Olinda",
  "email_contato": "financeiro@pinsaude.com.br"
}
```

### 3. Critérios de Aceite
- [ ] `POST /empresas` com CNPJ válido cria empresa com dados da Receita Federal preenchidos.
- [ ] CNPJ inválido (dígito verificador) retorna 400 com mensagem de erro.
- [ ] Upload de A1 com senha errada retorna 400 ("certificado inválido ou senha incorreta").
- [ ] Upload de A1 com CNPJ diferente do da empresa retorna 400.
- [ ] Após upload, `certificado_a1_vault_path` está preenchido e o PFX **não** existe no banco.
- [ ] CNPJ duplicado retorna 409.
- [ ] `conta_bancaria_jsonb` é gravado com dados bancários criptografados (campo sensível).

### 4. Regras de Negócio
- Cada CNPJ tem A1, IM e conta bancária próprios (premissa N3 do PRD).
- O PFX nunca é gravado em banco ou disco — somente no cofre de segredos.
- Empresa deve ter certificado A1 válido para emitir NFS-e.
- Alerta automático quando A1 expira em < 30 dias.
- Dados bancários da empresa criptografados em repouso (ADR-0010).

### 5. Cenários de Testes para o Humano
1. **Criar empresa:** `POST /empresas` com CNPJ da Pin Saúde → verificar que razão social foi preenchida via Receita Federal.
2. **Upload do A1:** Fazer upload do PFX de teste com senha correta → verificar no Vault (`vault kv get secret/pinsaude/cnpj/{id}/certificado-a1`) que o segredo existe.
3. **PFX não no banco:** Após upload, executar `SELECT * FROM onboarding.empresa WHERE cnpj = '...'` → verificar que o campo PFX não existe, apenas o vault path.
4. **Certificado errado:** Fazer upload de PFX com CNPJ diferente → sistema deve retornar 400 com mensagem "CNPJ do certificado não confere com a empresa".
5. **CNPJ duplicado:** Tentar criar empresa com CNPJ já cadastrado → deve retornar 409.

---

## TASK-02.2 — Cadastro de Médico/Sócio

### 1. Objetivo (Por quê?)
O médico é o cliente principal da plataforma. Sem o cadastro completo (CRM, CPF, dados bancários para repasse), não é possível processar emissões nem repassar honorários.

### 2. Descrição da Solução (O quê?)
CRUD da entidade `Medico` com vínculo N:N com empresas, gestão de documentos e dados bancários com criptografia.

**Migração Flyway (`onboarding` schema — `V2__create_medico.sql`):**
```sql
CREATE TABLE onboarding.medico (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id         UUID NOT NULL,     -- tenant
  usuario_id      UUID,              -- FK lógica para auth.usuario.id (após ativação)
  nome_completo   VARCHAR(255) NOT NULL,
  cpf             BYTEA NOT NULL,    -- CPF criptografado (AES-256)
  cpf_hash        VARCHAR(64) NOT NULL UNIQUE,  -- SHA-256 para busca sem descriptografar
  crm             VARCHAR(20) NOT NULL,
  estado_crm      CHAR(2) NOT NULL,
  especialidade   VARCHAR(100),
  email           VARCHAR(255) NOT NULL,
  telefone        VARCHAR(20),
  dados_bancarios_jsonb JSONB,       -- banco, agência, conta, chave_pix (criptografado)
  cpfs_adicionais_jsonb JSONB,       -- CPFs para split de repasse acima R$40k
  status          VARCHAR(20) NOT NULL DEFAULT 'PENDENTE',
  -- PENDENTE → DOCUMENTOS_ENVIADOS → ANALISE_CONDUTA → CONTRATO_ASSINADO → JUNTA_PENDENTE → ATIVO
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_medico_tenant ON onboarding.medico (cnpj_id);
CREATE INDEX idx_medico_cpf_hash ON onboarding.medico (cpf_hash);

CREATE TABLE onboarding.vinculo_medico_empresa (
  id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id    UUID NOT NULL,
  medico_id  UUID NOT NULL REFERENCES onboarding.medico(id),
  empresa_id UUID NOT NULL,
  status_societario VARCHAR(30) NOT NULL DEFAULT 'PENDENTE',
  -- PENDENTE → JUNTA_AGUARDANDO → JUNTA_APROVADO → ATIVO → INATIVO
  data_entrada TIMESTAMPTZ,
  data_saida   TIMESTAMPTZ,
  UNIQUE (medico_id, empresa_id)
);
ENABLE ROW LEVEL SECURITY ON onboarding.medico;
ENABLE ROW LEVEL SECURITY ON onboarding.vinculo_medico_empresa;
```

**Criptografia do CPF:**
```java
// AES-256-GCM via Vault Transit Engine ou chave em cofre
// cpf_hash = SHA-256(cpf) para buscas sem expor o valor
byte[] cpfEncriptado = cipherService.encrypt(cpf);
String cpfHash = DigestUtils.sha256Hex(cpf);
```

**Endpoints REST (`/medicos`):**
```
POST   /medicos                           → criar médico (role: OPERACAO, GESTAO)
GET    /medicos/{id}                      → detalhe
PUT    /medicos/{id}                      → atualizar (role: OPERACAO, GESTAO)
POST   /medicos/{id}/dados-bancarios      → atualizar dados bancários (step-up auth)
GET    /medicos/{id}/documentos           → listar documentos
POST   /medicos/{id}/documentos           → upload de documento
GET    /portal/medico/me                  → médico logado vê os próprios dados
```

**Documentos aceitos (RF-CAD-01):** CRM, diploma, CNH, comprovante de residência.

### 3. Critérios de Aceite
- [ ] `POST /medicos` cria médico com CPF criptografado no banco (não em claro).
- [ ] Busca por CPF (`GET /medicos?cpf=...`) funciona via `cpf_hash` sem descriptografar.
- [ ] Upload de documento aceita PDF/JPG/PNG até 10MB; armazena caminho (não o arquivo em banco).
- [ ] Alteração de `dados_bancarios_jsonb` exige step-up (sessão < 5 min) e gera auditoria.
- [ ] Médico logado (`GET /portal/medico/me`) só vê os próprios dados.
- [ ] RLS ativo: médico de tenant A não aparece em query do tenant B.

### 4. Regras de Negócio
- CPF criptografado em repouso (LGPD + ADR-0010).
- Dados bancários criptografados em repouso.
- Alteração de dados bancários exige reconfirmação (RF-ONB-08).
- Médico pode ter CPFs adicionais para split de repasse acima de R$ 40.000 (RF-REP-03).
- Um médico pode estar vinculado a mais de um CNPJ (N:N — PRD §5.1).

### 5. Cenários de Testes para o Humano
1. **Criação com CPF:** `POST /medicos` com CPF "123.456.789-09" → verificar na tabela que a coluna `cpf` contém bytes (não texto legível) e `cpf_hash` contém o hash SHA-256.
2. **Busca por CPF:** `GET /medicos?cpf=123.456.789-09` → deve retornar o médico sem expor o CPF descriptografado no log.
3. **Upload de documentos:** Fazer upload de CRM em PDF → verificar que o arquivo foi salvo no armazenamento (S3/disco) e o path está na tabela.
4. **Step-up bancário:** Logar como médico, esperar 6 minutos, tentar `PUT /medicos/{id}/dados-bancarios` → deve retornar 401 com `X-Step-Up-Required: true`.
5. **Vínculo N:N:** Criar médico e vinculá-lo a 2 empresas → verificar 2 registros em `vinculo_medico_empresa`.

---

## TASK-02.3 — Cadastro de Tomador

### 1. Objetivo (Por quê?)
O tomador (hospital, clínica, operadora ou paciente PF) determina as regras fiscais da nota: se há retenção federal, se o serviço tem equiparação hospitalar, e se o tomador é PF (CPF) para o caso especial de nota com impostos zerados.

### 2. Descrição da Solução (O quê?)
CRUD do tomador com distinção entre PJ (hospital/clínica) e PF (paciente CPF), validação de CNPJ/CPF e indicador de retenção.

**Migração Flyway (`faturamento` schema — `V1__create_tomador.sql`):**
```sql
CREATE TABLE faturamento.tomador (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id          UUID NOT NULL,   -- tenant
  tipo             VARCHAR(5) NOT NULL CHECK (tipo IN ('PJ', 'PF')),
  -- PJ: hospital, clínica, operadora de plano
  -- PF: paciente pessoa física
  documento        BYTEA NOT NULL,  -- CNPJ ou CPF criptografado
  documento_hash   VARCHAR(64) NOT NULL,  -- para busca
  razao_social     VARCHAR(255),    -- para PJ
  nome             VARCHAR(255),    -- para PF
  municipio        VARCHAR(100),
  codigo_municipio_ibge VARCHAR(10),
  retencao_federal BOOLEAN NOT NULL DEFAULT true,
  -- true: hospital retém IR, PIS, COFINS, CSLL na fonte
  -- false: sem retenção (ex: paciente PF, pequeno prestador)
  ativo            BOOLEAN NOT NULL DEFAULT true,
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tomador_tenant ON faturamento.tomador (cnpj_id);
CREATE INDEX idx_tomador_doc_hash ON faturamento.tomador (documento_hash);
ENABLE ROW LEVEL SECURITY ON faturamento.tomador;
```

**Regra especial para tomador PF (paciente CPF com equiparação):**
```
tomador.tipo = 'PF'
+ servico.equiparado = true
→ nota emitida com impostos ZERADOS (sem destaque, sem retenção na nota)
→ tributos calculados na apuração mensal
→ médico recebe 85% normalmente
```

**Endpoints REST:**
```
POST   /tomadores                   → criar (role: OPERACAO, GESTAO)
GET    /tomadores/{id}              → detalhe
PUT    /tomadores/{id}              → atualizar
GET    /tomadores?q={busca}         → buscar por nome/CNPJ
```

### 3. Critérios de Aceite
- [ ] Criação de tomador PJ com CNPJ válido preenche `razao_social` via Receita Federal.
- [ ] Criação de tomador PF com CPF → `documento` criptografado, `documento_hash` preenchido.
- [ ] `retencao_federal` default true para PJ, false para PF.
- [ ] Busca por CNPJ/CPF funciona via hash (não descriptografa para buscar).
- [ ] RLS: tomadores do tenant A não aparecem em queries do tenant B.
- [ ] CNPJ/CPF inválido retorna 400.

### 4. Regras de Negócio
- Tomador PF com serviço equiparado → nota com impostos zerados (§5.2, RF-NF-02).
- CNPJ/CPF do tomador criptografados em repouso (LGPD).
- `retencao_federal` determina se o hospital retém tributos na fonte (~95% dos casos — PRD §5.2).
- Tomador pode ser reaproveitado em múltiplas produções.

### 5. Cenários de Testes para o Humano
1. **Tomador PJ:** Criar tomador com CNPJ de hospital real → verificar preenchimento automático de razão social.
2. **Tomador PF:** Criar tomador PF com CPF válido → verificar que `documento` está criptografado e `tipo = 'PF'`.
3. **Flag de retenção:** Criar nota para tomador com `retencao_federal = false` → verificar que o motor fiscal não inclui retenções na composição.
4. **Caso especial CPF + equiparação:** Criar produção para tomador PF com serviço equiparado → verificar que a nota é gerada com todos os impostos zerados.

---

## TASK-02.4 — Cadastro de Serviço com Regra Fiscal

### 1. Objetivo (Por quê?)
Cada tipo de serviço médico tem código fiscal diferente (LC 116, CNAE), pode ou não ter equiparação hospitalar, e tem alíquotas de destaque específicas. Este cadastro é a base do motor fiscal — sem ele, as notas não podem ser emitidas corretamente.

### 2. Descrição da Solução (O quê?)
CRUD do tipo de serviço com todos os atributos fiscais necessários para o motor de cálculo.

**Migração Flyway (`fiscal` schema — `V1__create_servico.sql`):**
```sql
CREATE TABLE fiscal.servico (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id               UUID NOT NULL,  -- tenant (cada empresa pode ter variações)
  codigo                VARCHAR(50) NOT NULL,        -- código interno
  descricao             VARCHAR(500) NOT NULL,
  codigo_lc116          VARCHAR(10) NOT NULL,        -- ex: '4.02', '4.03'
  cnae                  VARCHAR(10) NOT NULL,        -- ex: '8610-1/01'
  equiparado            BOOLEAN NOT NULL DEFAULT false,
  -- true: presunção reduzida (IR 8%, CSLL 12%)
  -- false: presunção cheia (IR 32%)
  discriminacao_padrao  TEXT,           -- texto padrão da discriminação na nota
  ativo                 BOOLEAN NOT NULL DEFAULT true,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (cnpj_id, codigo)
);

-- Alíquotas de destaque (versionadas por competência — ver TASK-04.1)
-- Tabela de referência dos CNAEs equiparados:
-- 8610-1/01, 8610-1/02, 8630-5/01, 8640-2/07, 8640-2/08
CREATE INDEX idx_servico_tenant ON fiscal.servico (cnpj_id);
ENABLE ROW LEVEL SECURITY ON fiscal.servico;
```

**Endpoints REST:**
```
POST   /servicos                     → criar (role: CONTABIL, GESTAO)
GET    /servicos/{id}                → detalhe
PUT    /servicos/{id}                → atualizar
GET    /servicos                     → listar (paginado)
```

**Serviços pré-cadastrados (seed de dados para cada empresa):**
```json
[
  {
    "codigo": "CONSUL-MEDICA",
    "descricao": "Consulta Médica",
    "codigo_lc116": "4.03",
    "cnae": "8630-5/01",
    "equiparado": true,
    "discriminacao_padrao": "Honorários médicos referentes a consultas médicas realizadas no período de competência"
  },
  {
    "codigo": "CIRURGIA",
    "descricao": "Procedimento Cirúrgico",
    "codigo_lc116": "4.02",
    "cnae": "8610-1/01",
    "equiparado": true,
    "discriminacao_padrao": "Honorários médicos referentes a procedimentos cirúrgicos realizados no período de competência"
  }
]
```

### 3. Critérios de Aceite
- [ ] `POST /servicos` cria serviço com todos os campos fiscais.
- [ ] Serviço com CNAE da lista de equiparação (`8610-1/01`, `8610-1/02`, `8630-5/01`, `8640-2/07`, `8640-2/08`) pode ter `equiparado = true`.
- [ ] Seed de dados cria ao menos 2 serviços padrão para uma nova empresa.
- [ ] `codigo` único por tenant — CNPJ A pode ter "CONSUL-MEDICA" e CNPJ B também.
- [ ] RLS: serviços do tenant A não aparecem para o tenant B.
- [ ] Apenas roles CONTABIL e GESTAO podem criar/editar serviços.

### 4. Regras de Negócio
- CNAEs elegíveis para equiparação hospitalar: `8610-1/01`, `8610-1/02`, `8630-5/01`, `8640-2/07`, `8640-2/08` (PRD §5.2).
- Código LC 116 obrigatório: `4.02` ou `4.03` (PRD §5.2).
- `equiparado = true` → presunção reduzida (IR 8%, CSLL 12%) na apuração mensal.
- `equiparado = false` → presunção cheia (IR 32%).
- Alíquotas de destaque são parametrizadas por competência (TASK-04.1), não hardcoded no serviço.

### 5. Cenários de Testes para o Humano
1. **Criar serviço equiparado:** Criar serviço com CNAE `8610-1/01` e `equiparado = true` → motor fiscal deve usar presunção reduzida.
2. **Criar serviço não-equiparado:** Criar serviço com CNAE diferente e `equiparado = false` → motor fiscal deve usar presunção cheia.
3. **Unicidade por tenant:** Criar serviço "CONSUL-MEDICA" nos tenants A e B → ambos devem ser criados sem erro (unicidade é por tenant).
4. **Role indevida:** Tentar criar serviço como OPERACAO → deve retornar 403.
5. **Fila de exceção:** Emitir produção com tomador existente mas serviço sem regra fiscal → deve ir para fila de exceção (validar com TASK-05.4).
