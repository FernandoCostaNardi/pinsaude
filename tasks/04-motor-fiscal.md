# EPIC-04 — Motor de Cálculo Fiscal

> Prioridade: **P0** — A emissão de NFS-e e o ledger dependem do motor fiscal.
> ADRs: ADR-0007. PRD: §5.2, §5.3, §7.5. RFs: RF-FISC-01..04
> **CRÍTICO:** Nenhuma alíquota/base pode estar "chumbada" no código. Tudo parametrizável por competência.

---

## TASK-04.1 — Modelo de Dados de Regras Fiscais Versionadas por Competência

### 1. Objetivo (Por quê?)
Regras fiscais mudam com o tempo (alíquotas, vigências, transição IBS/CBS a partir de 2027). O motor não pode ter nenhum número fixo no código — toda mudança fiscal deve ser uma alteração de dados, não de código (RF-FISC-04, ADR-0007).

### 2. Descrição da Solução (O quê?)
Criar tabelas de regras e parâmetros fiscais versionados por `vigencia_inicio`/`vigencia_fim`, selecionados pela competência do fato gerador.

**Migração (`fiscal.V2__create_regras_fiscais.sql`):**
```sql
-- Parâmetros fiscais globais (alíquotas, bases de presunção)
CREATE TABLE fiscal.parametro_fiscal (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id         UUID NOT NULL,       -- tenant (cada empresa pode ter parâmetros distintos)
  regime          VARCHAR(30) NOT NULL DEFAULT 'LUCRO_PRESUMIDO',
  vigencia_inicio DATE NOT NULL,
  vigencia_fim    DATE,                -- NULL = vigente até nova versão
  -- Alíquotas de destaque (nota)
  aliq_iss        NUMERIC(5,4) NOT NULL,   -- ex: 0.0200 = 2%
  aliq_ir         NUMERIC(5,4) NOT NULL,   -- ex: 0.0150 = 1.5%
  aliq_csll       NUMERIC(5,4) NOT NULL,   -- ex: 0.0100 = 1%
  aliq_pis        NUMERIC(5,4) NOT NULL,   -- ex: 0.0065 = 0.65%
  aliq_cofins     NUMERIC(5,4) NOT NULL,   -- ex: 0.0300 = 3%
  -- Bases de presunção (apuração mensal)
  base_presuncao_irpj_equiparado   NUMERIC(5,4) NOT NULL DEFAULT 0.0800, -- 8%
  base_presuncao_csll_equiparado   NUMERIC(5,4) NOT NULL DEFAULT 0.1200, -- 12%
  base_presuncao_irpj_sem_equip    NUMERIC(5,4) NOT NULL DEFAULT 0.3200, -- 32%
  base_presuncao_csll_sem_equip    NUMERIC(5,4) NOT NULL DEFAULT 0.3200, -- 32%
  -- Alíquotas de apuração
  aliq_irpj                        NUMERIC(5,4) NOT NULL DEFAULT 0.1500, -- 15%
  aliq_irpj_adicional              NUMERIC(5,4) NOT NULL DEFAULT 0.1000, -- 10%
  limite_irpj_adicional            BIGINT NOT NULL DEFAULT 2000000,      -- R$20.000,00 em centavos/mês
  aliq_csll_apuracao               NUMERIC(5,4) NOT NULL DEFAULT 0.0900, -- 9%
  aliq_pis_apuracao                NUMERIC(5,4) NOT NULL DEFAULT 0.0065, -- 0.65%
  aliq_cofins_apuracao             NUMERIC(5,4) NOT NULL DEFAULT 0.0300, -- 3%
  -- Taxa administrativa da Pin
  taxa_administrativa              NUMERIC(5,4) NOT NULL DEFAULT 0.1500, -- 15%
  percentual_repasse               NUMERIC(5,4) NOT NULL DEFAULT 0.8500, -- 85%
  -- IBS/CBS (reforma tributária - a partir de 2027)
  ibs_cbs_ativo                   BOOLEAN NOT NULL DEFAULT false,
  aliq_ibs_cbs                    NUMERIC(5,4),
  reducao_ibs_cbs_saude           NUMERIC(5,4),  -- 60% de redução para saúde humana
  observacoes                     TEXT,
  created_by                      UUID NOT NULL,
  created_at                      TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT vigencia_valida CHECK (vigencia_fim IS NULL OR vigencia_fim > vigencia_inicio)
);
CREATE INDEX idx_param_fiscal_vigencia ON fiscal.parametro_fiscal (cnpj_id, vigencia_inicio DESC);
ENABLE ROW LEVEL SECURITY ON fiscal.parametro_fiscal;
```

**Seed de dados inicial (competência 2026):**
```sql
INSERT INTO fiscal.parametro_fiscal (
  cnpj_id, vigencia_inicio, aliq_iss, aliq_ir, aliq_csll, aliq_pis, aliq_cofins, created_by
) VALUES (
  '<cnpj_id_empresa_1>', '2026-01-01',
  0.0200, 0.0150, 0.0100, 0.0065, 0.0300,
  '<id_usuario_sistema>'
);
```

**Método de resolução de parâmetros (Java):**
```java
public ParametroFiscal resolverParaCompetencia(UUID cnpjId, YearMonth competencia) {
    // Busca o parâmetro com vigencia_inicio <= primeiro dia da competência
    // e vigencia_fim IS NULL ou vigencia_fim >= último dia da competência
    // Ordena por vigencia_inicio DESC, pega o primeiro
    return parametroFiscalRepository
        .findActiveForCompetencia(cnpjId, competencia.atDay(1), competencia.atEndOfMonth())
        .orElseThrow(() -> new ParametroFiscalNaoEncontradoException(cnpjId, competencia));
}
```

### 3. Critérios de Aceite
- [ ] Método `resolverParaCompetencia` retorna o parâmetro correto para competência 2026-06.
- [ ] Se houver 2 registros (jan/2026 e jan/2027), competência 2026-06 retorna o de jan/2026; 2027-01 retorna o de jan/2027.
- [ ] Competência sem parâmetro configurado lança `ParametroFiscalNaoEncontradoException` (nunca silencia).
- [ ] Endpoint de parametrização `POST /parametros-fiscais` (role: CONTABIL, GESTAO) cria novo parâmetro.
- [ ] Parâmetro com `vigencia_inicio` que sobrepõe outro já existente retorna 409.
- [ ] `GET /parametros-fiscais/vigente?competencia=2026-06` retorna o parâmetro ativo.

### 4. Regras de Negócio
- Alíquotas padrão Lucro Presumido: ISS 2%, IR 1,5%, CSLL 1%, PIS 0,65%, COFINS 3% (PRD §5.2).
- Taxa administrativa = 15%; repasse médico = 85% (invariante absoluta — PRD §5.3).
- IBS/CBS: início em 2027 com fase de 1%; enquadramento NBS 200029 / Anexo III / redução 60% (PRD §5.2).
- A homologação das fórmulas é responsabilidade da contabilidade da Pin antes do go-live.
- Nenhum valor numérico fiscal fixo no código-fonte (RF-FISC-04).

### 5. Cenários de Testes para o Humano
1. **Resolução correta:** Cadastrar parâmetro com vigência jan/2026. Chamar `GET /parametros-fiscais/vigente?competencia=2026-06` → deve retornar o parâmetro de jan/2026.
2. **Troca de vigência:** Cadastrar segundo parâmetro com vigência jan/2027 (alíquota ISS 3%). Chamar para 2026-12 → retorna 2%, para 2027-01 → retorna 3%.
3. **Competência sem parâmetro:** Chamar para 2025-01 (antes de qualquer parâmetro) → deve retornar erro 422 com mensagem clara.
4. **Auditoria de parametrização:** Criar novo parâmetro como contabilista → verificar registro em `audit_log` com `action = 'parametro-fiscal.criado'`.

---

## TASK-04.2 — Determinação de Equiparação Hospitalar

### 1. Objetivo (Por quê?)
Serviços com equiparação hospitalar usam presunção reduzida (IR 8%, CSLL 12%) vs. presunção cheia (IR 32%), impactando diretamente o imposto apurado mensalmente. A determinação é por CNAE + serviço (RF-FISC-01).

### 2. Descrição da Solução (O quê?)
Lógica de negócio pura (sem infra) que determina equiparação com base no cadastro do serviço e do tomador.

**Classe de domínio `EquiparacaoHospitalar.java`:**
```java
public class EquiparacaoHospitalar {

    // CNAEs elegíveis para equiparação (PRD §5.2)
    private static final Set<String> CNAES_EQUIPARADOS = Set.of(
        "8610-1/01", "8610-1/02", "8630-5/01", "8640-2/07", "8640-2/08"
    );

    /**
     * Determina se o serviço tem equiparação hospitalar.
     * REGRA: CNAE do serviço deve estar na lista de CNAEs equiparados
     * E o flag equiparado deve estar ativo no cadastro do serviço.
     */
    public static boolean determinar(Servico servico) {
        return servico.isEquiparado()
            && CNAES_EQUIPARADOS.contains(servico.getCnae());
    }

    /**
     * Determina se a nota deve ter impostos ZERADOS.
     * CASO ESPECIAL (PRD §5.2): tomador PF (CPF) + serviço equiparado
     * → nota com impostos zerados; tributos calculados na apuração mensal
     */
    public static boolean deveEmitirComImpostosZerados(Tomador tomador, Servico servico) {
        return TipoTomador.PF.equals(tomador.getTipo())
            && determinar(servico);
    }
}
```

**Testes unitários obrigatórios:**
```java
class EquiparacaoHospitalarTest {
    @Test void cnaeEquiparadoComFlagAtivo_deveSerEquiparado();
    @Test void cnaeEquiparadoComFlagInativo_naoDeveSerEquiparado();
    @Test void cnaeNaoListado_naoDeveSerEquiparado();
    @Test void tomadorPF_comEquiparacao_deveEmitirZerado();
    @Test void tomadorPJ_comEquiparacao_naoDeveEmitirZerado();
    @Test void tomadorPF_semEquiparacao_naoDeveEmitirZerado();
}
```

### 3. Critérios de Aceite
- [ ] CNAE `8610-1/01` + `equiparado=true` → `determinar()` retorna `true`.
- [ ] CNAE `8610-1/01` + `equiparado=false` → `determinar()` retorna `false`.
- [ ] CNAE `9999-9/99` + `equiparado=true` → `determinar()` retorna `false`.
- [ ] Tomador PF + serviço equiparado → `deveEmitirComImpostosZerados()` retorna `true`.
- [ ] Tomador PJ + serviço equiparado → `deveEmitirComImpostosZerados()` retorna `false`.
- [ ] 100% de cobertura de testes unitários nesta classe.

### 4. Regras de Negócio
- CNAEs elegíveis (PRD §5.2): `8610-1/01`, `8610-1/02`, `8630-5/01`, `8640-2/07`, `8640-2/08`.
- Ambas as condições precisam ser verdadeiras: CNAE na lista E flag `equiparado=true`.
- Tomador CPF (PF) com equiparação → nota zerada (NOVO, incorporado na v1.1).
- Presunção reduzida: IRPJ base 8%, CSLL base 12%.
- Presunção cheia: IRPJ base 32%, CSLL base 32%.

### 5. Cenários de Testes para o Humano
1. **Equiparação ativa:** Cadastrar serviço com CNAE `8630-5/01` e `equiparado=true` → ao emitir nota, verificar que a presunção da apuração usa base de 8% (IR) e 12% (CSLL).
2. **Sem equiparação:** Cadastrar serviço com CNAE `8630-5/01` e `equiparado=false` → apuração usa base de 32%.
3. **CPF + equiparação → nota zerada:** Produção de médico para tomador PF com serviço equiparado → nota gerada deve ter todos os impostos = 0.
4. **CPF + sem equiparação → nota normal:** Produção para tomador PF com serviço SEM equiparação → nota gerada normalmente com impostos.

---

## TASK-04.3 — Cálculo de Destaque da Nota e Líquido do Médico

### 1. Objetivo (Por quê?)
Para cada nota emitida, o sistema precisa calcular os valores de ISS, IR, CSLL, PIS e COFINS a destacar na nota, e o valor líquido (85%) que será repassado ao médico. Este cálculo é o coração financeiro da plataforma (RF-FISC-02, PRD §5.3).

### 2. Descrição da Solução (O quê?)
Classe de domínio `CalculoFiscal` que recebe o valor bruto, o parâmetro fiscal da competência, o tipo de tomador e o flag de equiparação, e retorna todos os valores calculados.

**Classe `CalculoFiscal.java` (domínio puro, sem Spring):**
```java
public record ResultadoCalculo(
    long valorBruto,          // centavos
    long valorIss,            // centavos (0 se nota zerada)
    long valorIr,             // centavos (0 se nota zerada)
    long valorCsll,           // centavos (0 se nota zerada)
    long valorPis,            // centavos (0 se nota zerada)
    long valorCofins,         // centavos (0 se nota zerada)
    long totalRetencoes,      // soma das retenções sofridas pelo tomador
    long taxaAdministrativa,  // complemento até 15%
    long valorRepasse,        // = 85% do bruto (invariante absoluta)
    boolean impostosZerados,  // true para CPF + equiparação
    String regraAplicada      // ID da regra fiscal usada (para auditoria)
) {}

public class CalculoFiscal {

    public ResultadoCalculo calcular(
            long valorBruto,          // em centavos
            ParametroFiscal params,
            TipoTomador tipoTomador,
            boolean equiparado,
            boolean retencaoFederal   // tomador retém na fonte?
    ) {
        // 1. Invariante absoluta: repasse = 85%
        long valorRepasse = arredondar(valorBruto * params.getPercentualRepasse());

        // 2. Caso especial: tomador PF + equiparado → nota zerada
        if (TipoTomador.PF.equals(tipoTomador) && equiparado) {
            long taxaAdm = valorBruto - valorRepasse;
            return new ResultadoCalculo(valorBruto, 0, 0, 0, 0, 0, 0, taxaAdm, valorRepasse, true, params.getId().toString());
        }

        // 3. Cálculo normal
        long valorIss     = arredondar(valorBruto * params.getAliqIss());
        long valorIr      = arredondar(valorBruto * params.getAliqIr());
        long valorCsll    = arredondar(valorBruto * params.getAliqCsll());
        long valorPis     = arredondar(valorBruto * params.getAliqPis());
        long valorCofins  = arredondar(valorBruto * params.getAliqCofins());

        // 4. Retenções: quem paga?
        // retencaoFederal=true: hospital retém IR+PIS+COFINS+CSLL na fonte
        // ISS sempre recolhido pela Pin
        long totalRetencoes = retencaoFederal
            ? valorIr + valorPis + valorCofins + valorCsll
            : 0L;

        // 5. Taxa administrativa = 15% - retenções já sofridas
        long taxaAdm = (valorBruto - valorRepasse) - valorIss - totalRetencoes;

        return new ResultadoCalculo(
            valorBruto, valorIss, valorIr, valorCsll, valorPis, valorCofins,
            totalRetencoes, taxaAdm, valorRepasse, false, params.getId().toString()
        );
    }

    private long arredondar(double valor) {
        return Math.round(valor);  // arredondamento HALF_UP, em centavos
    }
}
```

**Exemplo de referência (PRD §5.3, nota de R$ 10.000,00):**
```
Bruto:              R$ 10.000,00  →  1.000.000 centavos
ISS (2%):           R$    200,00  →     20.000 centavos
IR (1,5%):          R$    150,00  →     15.000 centavos
CSLL (1%):          R$    100,00  →     10.000 centavos
PIS (0,65%):        R$     65,00  →      6.500 centavos
COFINS (3%):        R$    300,00  →     30.000 centavos
Retenções federal:  R$    615,00  →     61.500 centavos (IR+PIS+COFINS+CSLL)
Taxa administrativa: R$   685,00  →     68.500 centavos
Repasse (85%):      R$ 8.500,00  →    850.000 centavos
```

**PBT obrigatórios (jqwik):**
```java
@Property
void medicoSempreRecebe85Porcento(@ForAll @LongRange(min=1, max=100_000_000) long bruto) {
    var params = parametroFiscalFixo();
    var resultado = calculoFiscal.calcular(bruto, params, TipoTomador.PJ, true, true);
    assertThat(resultado.valorRepasse()).isEqualTo(Math.round(bruto * 0.85));
}

@Property
void somaComponentesSempreIgualBruto(@ForAll @LongRange(min=1) long bruto) {
    var r = calculoFiscal.calcular(bruto, params, TipoTomador.PJ, true, true);
    assertThat(r.valorRepasse() + r.valorIss() + r.taxaAdministrativa() + r.totalRetencoes())
        .isEqualTo(bruto);
}
```

### 3. Critérios de Aceite
- [ ] Nota de R$ 10.000 com tomador PJ e retenção federal retorna exatamente os valores do PRD §5.3.
- [ ] Nota para tomador PF com equiparação retorna todos os impostos zerados e repasse = 85%.
- [ ] PBT: 1.000+ casos validam que repasse = 85% do bruto para qualquer valor.
- [ ] PBT: soma dos componentes sempre igual ao valor bruto.
- [ ] Nenhum `double` ou `float` — apenas `long` (centavos) e `NUMERIC` com escala explícita.
- [ ] Todos os testes executam sem Spring Boot (domínio puro).

### 4. Regras de Negócio
- Repasse ao médico = 85% (invariante absoluta — PRD §5.3). NUNCA pode ser diferente.
- Aritmética: SEMPRE em inteiros (centavos). Arredondamento HALF_UP.
- NUNCA usar `double`/`float` para dinheiro (ADR-0008).
- Resultado do cálculo deve registrar qual `regra_aplicada` (ID do parâmetro fiscal) para auditoria.
- Tomador PF + equiparação → impostos zerados na nota (PRD §5.2, v1.1).

### 5. Cenários de Testes para o Humano
1. **Nota padrão R$10.000:** Emitir nota de R$ 10.000 para hospital (PJ, com retenção) → verificar no PDF da nota os valores: ISS R$200, IR R$150, CSLL R$100, PIS R$65, COFINS R$300 e repasse R$8.500.
2. **Nota CPF zerada:** Emitir nota de R$ 5.000 para paciente PF com serviço equiparado → nota gerada com todos os impostos = 0 e repasse = R$4.250.
3. **Arredondamento:** Emitir nota de R$ 333,33 (33.333 centavos) → verificar que o arredondamento é consistente e a soma dos componentes equals ao valor bruto.
4. **Registro da regra:** Verificar no ledger que o lançamento referencia o ID do parâmetro fiscal usado.

---

## TASK-04.4 — Registro de Retenções na Fonte Sofridas

### 1. Objetivo (Por quê?)
~95% das notas têm retenção federal na fonte pelo hospital (IR, PIS, COFINS, CSLL). Esses valores são créditos para a apuração mensal — a Pin não os paga novamente. Sem esse registro, a apuração ficará errada (RF-FISC-03).

### 2. Descrição da Solução (O quê?)
Registrar as retenções sofridas por nota fiscal para uso na apuração mensal. Esses dados compõem o "crédito de antecipação" que é deduzido do tributo devido no período.

**Migração (`fiscal.V3__create_retencoes_sofridas.sql`):**
```sql
CREATE TABLE fiscal.retencao_sofrida (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  cnpj_id         UUID NOT NULL,
  nota_fiscal_id  UUID NOT NULL,  -- FK lógica para faturamento.nota_fiscal
  tomador_id      UUID NOT NULL,
  competencia     DATE NOT NULL,  -- primeiro dia do mês de competência
  valor_ir        BIGINT NOT NULL DEFAULT 0,
  valor_pis       BIGINT NOT NULL DEFAULT 0,
  valor_cofins    BIGINT NOT NULL DEFAULT 0,
  valor_csll      BIGINT NOT NULL DEFAULT 0,
  total_retencao  BIGINT GENERATED ALWAYS AS (valor_ir + valor_pis + valor_cofins + valor_csll) STORED,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_retencao_competencia ON fiscal.retencao_sofrida (cnpj_id, competencia);
ENABLE ROW LEVEL SECURITY ON fiscal.retencao_sofrida;
```

**Lógica de criação:**
- Ao emitir nota com `retencao_federal = true`, o serviço `fiscal` cria automaticamente um registro de `retencao_sofrida` com os valores calculados pelo `CalculoFiscal`.
- Ao emitir nota com `impostosZerados = true` (CPF + equiparação), cria `retencao_sofrida` com todos os valores = 0 (sem retenção).

**Método de consolidação para apuração:**
```java
public RetencoesConsolidadas consolidarParaApuracao(UUID cnpjId, YearMonth competencia) {
    return retencaoSofridaRepository.sumByCompetencia(cnpjId, competencia.atDay(1));
    // Retorna: totalIr, totalPis, totalCofins, totalCsll do período
}
```

### 3. Critérios de Aceite
- [ ] Ao emitir nota com retenção, registro em `retencao_sofrida` criado automaticamente.
- [ ] Nota zerada (CPF + equiparação) cria `retencao_sofrida` com todos os valores = 0.
- [ ] `consolidarParaApuracao(cnpjId, 2026-06)` soma corretamente todas as retenções do mês de junho/2026.
- [ ] Cancelamento de nota cria estorno das retenções (registro negativo ou deleção lógica).
- [ ] RLS isola retenções por tenant.

### 4. Regras de Negócio
- Retenção federal na fonte: IR, PIS, COFINS, CSLL (~95% das notas — PRD §5.2).
- ISS sempre recolhido pela Pin (não retido pelo hospital).
- Retenções são créditos na apuração mensal (RF-FISC-03).
- Cancelamento de nota invalida as retenções associadas.

### 5. Cenários de Testes para o Humano
1. **Criação automática:** Emitir nota de R$10.000 para hospital com retenção → verificar `retencao_sofrida` criada com IR=150, PIS=65, COFINS=300, CSLL=100.
2. **Nota zerada:** Emitir nota para CPF com equiparação → verificar `retencao_sofrida` com todos valores = 0.
3. **Consolidação mensal:** Emitir 3 notas no mês → `GET /apuracao/retencoes?competencia=2026-06` deve retornar a soma correta de todas as retenções.
4. **Cancelamento:** Cancelar nota → verificar que a retenção é invalidada e não aparece na consolidação.

---

## TASK-04.5 — Parametrização IBS/CBS (Reforma Tributária 2027)

### 1. Objetivo (Por quê?)
A partir de 2027, o Brasil inicia a transição para o IBS/CBS (reforma tributária). Serviços de saúde humana têm redução de 60% da alíquota. O motor precisa suportar este novo regime sem reescrita de código — apenas parametrização de vigência (PRD §5.2, RF-FISC-04, ADR-0007).

### 2. Descrição da Solução (O quê?)
Adicionar suporte a parâmetros de IBS/CBS na tabela `parametro_fiscal` (já criada em TASK-04.1) e implementar a lógica de seleção do regime correto por competência.

**Novos campos já contemplados em `parametro_fiscal` (TASK-04.1):**
```
ibs_cbs_ativo:           BOOLEAN - ligar em jan/2027
aliq_ibs_cbs:            NUMERIC - alíquota base (a definir pela regulamentação)
reducao_ibs_cbs_saude:   NUMERIC - 0.6000 (60% de redução para NBS 200029)
```

**Parâmetro de transição a ser criado para jan/2027:**
```sql
INSERT INTO fiscal.parametro_fiscal (
  cnpj_id, vigencia_inicio,
  -- Manter alíquotas atuais para tributos ainda vigentes
  aliq_iss, aliq_ir, aliq_csll, aliq_pis, aliq_cofins,
  -- Ativar IBS/CBS
  ibs_cbs_ativo, aliq_ibs_cbs, reducao_ibs_cbs_saude,
  observacoes, ...
) VALUES (
  '<cnpj_id>', '2027-01-01',
  0.0200, 0.0000, 0.0000, 0.0000, 0.0000,  -- tributos antigos em extinção
  true, 0.0100, 0.6000,                    -- IBS/CBS fase-teste 1%
  'Regime IBS/CBS conforme reforma tributária. Alíquotas a homologar.', ...
);
```

**Lógica no `CalculoFiscal` para IBS/CBS:**
```java
if (params.isIbsCbsAtivo()) {
    // Alíquota efetiva = aliq_ibs_cbs * (1 - reducao_ibs_cbs_saude)
    double aliqEfetiva = params.getAliqIbsCbs()
        .multiply(BigDecimal.ONE.subtract(params.getReducaoIbsCbsSaude()))
        .doubleValue();
    long valorIbsCbs = arredondar(valorBruto * aliqEfetiva);
    // ... compor resultado com IBS/CBS em vez dos tributos antigos
}
```

**Tela de gestão (backoffice) — endpoint:**
```
POST /parametros-fiscais/ibs-cbs
  body: { "competencia_inicio": "2027-01", "aliq_ibs_cbs": 0.01, "reducao_saude": 0.60 }
  role: CONTABIL, GESTAO
  → cria parâmetro para jan/2027 com IBS/CBS ativo
```

### 3. Critérios de Aceite
- [ ] Competência 2026-12 usa o regime atual (sem IBS/CBS).
- [ ] Competência 2027-01 usa o regime IBS/CBS (com `ibs_cbs_ativo = true`).
- [ ] Alíquota efetiva para saúde = `aliq_ibs_cbs * (1 - 0.60)`.
- [ ] Transição não quebra o cálculo de notas de competências anteriores (reprocessamento histórico fiel).
- [ ] Parâmetro IBS/CBS pode ser criado via API (sem deploy) pela contabilidade.
- [ ] Observação "a homologar" visível no parâmetro enquanto não validado pela contabilidade.

### 4. Regras de Negócio
- IBS/CBS: NBS 200029, Anexo III, redução de 60% da alíquota, início 2027 (PRD §5.2).
- Fase-teste em 2027: alíquota de 1%.
- Motor seleciona regime pela competência do FATO GERADOR, não pela data atual.
- Alíquotas e enquadramento devem ser homologados pela contabilidade (PRD §13).
- Parâmetro marcado como "a homologar" até validação da contabilidade.

### 5. Cenários de Testes para o Humano
1. **Coexistência de regimes:** Emitir nota para competência 2026-12 → regime atual aplicado. Emitir nota para competência 2027-01 → IBS/CBS aplicado. Verificar parâmetros diferentes usados em cada caso.
2. **Reprocessamento histórico:** Emitir nota para competência 2026-06, depois criar parâmetro IBS/CBS para jan/2027. Reprocessar a nota de 2026-06 → deve usar o parâmetro de 2026, não o de 2027.
3. **Alíquota efetiva:** Parâmetro com `aliq_ibs_cbs = 0.10` e `reducao_saude = 0.60` → alíquota efetiva calculada = 4% (0.10 * 0.40).
4. **Criação via backoffice:** Logar como CONTABIL, criar parâmetro IBS/CBS para jan/2027 → verificar criação sem precisar de deploy.
