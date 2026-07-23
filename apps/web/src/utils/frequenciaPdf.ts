import type { FrequenciaMedicaResp } from '../api/frequenciasApi'

export interface FrequenciaPdfParams {
  freq: FrequenciaMedicaResp
  medicoNome: string
  medicoCrm: string
  medicoCrmUf: string
  tomadorNome: string
  empresaNome: string
  empresaCnpj: string
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

const MESES_EXT = [
  'JANEIRO', 'FEVEREIRO', 'MARÇO', 'ABRIL', 'MAIO', 'JUNHO',
  'JULHO', 'AGOSTO', 'SETEMBRO', 'OUTUBRO', 'NOVEMBRO', 'DEZEMBRO',
]

function competenciaPorExtenso(competencia: string): string {
  const [ano, mes] = competencia.split('-')
  return `${MESES_EXT[parseInt(mes, 10) - 1]} DE ${ano}`
}

function formatDataCurta(iso: string): string {
  // iso = "2026-07-05" → "05/07/26"
  const [y, m, d] = iso.split('-')
  return `${d}/${m}/${y.slice(2)}`
}

function formatCnpj(cnpj: string): string {
  const digits = cnpj.replace(/\D/g, '')
  if (digits.length === 14) {
    return digits.replace(/(\d{2})(\d{3})(\d{3})(\d{4})(\d{2})/, '$1.$2.$3/$4-$5')
  }
  return cnpj
}

function gerarDataHoraAtual(): string {
  return new Date().toLocaleString('pt-BR', {
    day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  })
}

// ─── Geração do HTML ──────────────────────────────────────────────────────────

function buildHtml(p: FrequenciaPdfParams): string {
  const { freq, medicoNome, medicoCrm, medicoCrmUf, tomadorNome, empresaNome, empresaCnpj } = p
  const competenciaExt = competenciaPorExtenso(freq.competencia)
  const cnpjFormatado  = formatCnpj(empresaCnpj)
  const logoUrl        = `${window.location.origin}/logo-formulario.png`

  // Mês por extenso apenas (sem o ano) para o campo Competência
  const [, mesComp] = freq.competencia.split('-')
  const mesExt = MESES_EXT[parseInt(mesComp, 10) - 1]

  const linhasPreenchidas = freq.itens
    .slice()
    .sort((a, b) => a.dataExecucao < b.dataExecucao ? -1 : 1)
    .map(item => `
      <tr>
        <td style="text-align:center">${formatDataCurta(item.dataExecucao)}</td>
        <td style="text-align:center">${item.modalidadeTurno ?? ''}</td>
        <td style="text-align:center">${item.modalidadeHorario ?? ''}</td>
        <td></td>
        <td>${item.ocorrencia ?? ''}</td>
        <td></td>
      </tr>
    `).join('')

  // Linhas em branco para preenchimento manual (mínimo 20 linhas visíveis no total)
  const totalLinhas = Math.max(20, freq.itens.length + 5)
  const linhasVazias = Array.from({ length: totalLinhas - freq.itens.length }, () => `
    <tr>
      <td></td><td></td><td></td><td></td><td></td><td></td>
    </tr>
  `).join('')

  return `<!DOCTYPE html>
<html lang="pt-BR">
<head>
  <meta charset="UTF-8">
  <title>Relatório de Frequência Médica Individual</title>
  <style>
    * { margin: 0; padding: 0; box-sizing: border-box; }
    body {
      font-family: Arial, Helvetica, sans-serif;
      font-size: 9pt;
      color: #000;
      background: #fff;
    }
    .page {
      width: 210mm;
      min-height: 297mm;
      padding: 10mm 12mm 12mm 12mm;
      margin: 0 auto;
    }

    /* === Header Governo (tabela 2 colunas com borda) === */
    .gov-header-table {
      width: 100%;
      border-collapse: collapse;
      border: 1.5px solid #000;
      margin-bottom: 0;
    }
    .gov-header-table td {
      border: none;
      padding: 0;
      vertical-align: middle;
    }
    .gov-logo-cell {
      width: 175px;
      border-right: 1.5px solid #000 !important;
      text-align: center;
      padding: 4px 6px !important;
    }
    .gov-logo-cell img {
      height: 72px;
      display: block;
      margin: 0 auto;
    }
    .gov-text-cell {
      padding: 6px 10px !important;
      vertical-align: middle;
    }
    .gov-text-cell p {
      font-size: 9pt;
      font-weight: bold;
      line-height: 1.45;
      color: #000;
      margin: 0;
    }

    /* === Título do Formulário === */
    .form-title-table {
      width: 100%;
      border-collapse: collapse;
      border: 1.5px solid #000;
      border-top: none;
      margin-bottom: 0;
    }
    .form-title-table td {
      text-align: center;
      font-size: 11pt;
      font-weight: bold;
      text-transform: uppercase;
      letter-spacing: 0.5px;
      padding: 5px 10px;
      border: none;
    }

    /* === Campos do Formulário (tabela de linhas) === */
    .fields-table {
      width: 100%;
      border-collapse: collapse;
      border: 1.5px solid #000;
      border-top: none;
      margin-bottom: 6px;
    }
    .fields-table td {
      border: none;
      border-top: 1px solid #000;
      padding: 0;
      vertical-align: middle;
    }
    .field-label-cell {
      width: 185px;
      border-right: 1px solid #000 !important;
      padding: 3px 6px !important;
      font-size: 8pt;
      font-style: italic;
      color: #c25a00;
      white-space: nowrap;
      text-align: right;
    }
    .field-value-cell {
      padding: 3px 8px !important;
      font-size: 9pt;
      font-weight: bold;
    }
    .field-value-empresa { color: #c25a00; }
    .field-value-medico  { color: #0047ab; }
    .field-value-normal  { color: #000; }
    .field-value-center  { text-align: center; color: #000; }

    /* Célula do setor na linha Especialidade Médica */
    .field-setor-cell {
      border-left: 1px solid #000 !important;
      padding: 3px 8px !important;
      font-size: 9pt;
      font-weight: bold;
      color: #000;
      white-space: nowrap;
    }

    /* === Tabela de Plantões === */
    table.plantoes {
      width: 100%;
      border-collapse: collapse;
    }
    table.plantoes thead tr { background: #e0e0e0; }
    table.plantoes th {
      border: 1px solid #000;
      padding: 4px 3px;
      font-size: 7.5pt;
      font-weight: bold;
      text-align: center;
      text-transform: uppercase;
      white-space: nowrap;
    }
    table.plantoes td {
      border: 1px solid #aaa;
      padding: 2.5px 4px;
      font-size: 8.5pt;
      height: 17px;
      vertical-align: middle;
    }
    table.plantoes tbody tr:nth-child(even) { background: #fafafa; }
    .col-data    { width: 55px; }
    .col-turno   { width: 70px; }
    .col-horario { width: 105px; }
    .col-rubrica { width: 120px; }
    .col-lider   { width: 45px; }

    /* === Totalizador === */
    .totalizador {
      margin-top: 6px;
      font-size: 8.5pt;
      text-align: right;
      font-weight: bold;
    }

    /* === Assinaturas === */
    .signatures {
      display: flex;
      gap: 20px;
      margin-top: 28px;
      padding-top: 4px;
    }
    .sig-box {
      flex: 1;
      text-align: center;
    }
    .sig-space { height: 38px; }
    .sig-line {
      border-top: 1px solid #000;
      margin: 0 10px 3px 10px;
    }
    .sig-label {
      font-size: 7.5pt;
      font-weight: bold;
      text-transform: uppercase;
      letter-spacing: 0.3px;
    }
    .sig-sub {
      font-size: 7pt;
      color: #555;
      margin-top: 2px;
    }

    /* === Rodapé === */
    .footer {
      margin-top: 14px;
      font-size: 7pt;
      color: #888;
      text-align: center;
      border-top: 1px solid #ccc;
      padding-top: 4px;
    }

    @media print {
      html, body { margin: 0; padding: 0; background: #fff; }
      .page { margin: 0; padding: 8mm 10mm 10mm 10mm; width: 100%; }
      @page {
        size: A4 portrait;
        margin: 0;
      }
    }
  </style>
</head>
<body>
<div class="page">

  <!-- Header institucional: logo | texto oficial -->
  <table class="gov-header-table">
    <tr>
      <td class="gov-logo-cell">
        <img src="${logoUrl}" alt="Secretaria de Saúde de Pernambuco" />
      </td>
      <td class="gov-text-cell">
        <p>GOVERNO DO ESTADO DE PERNAMBUCO</p>
        <p>SECRETARIA DE SAÚDE DO ESTADO DE PERNAMBUCO</p>
        <p>SECRETARIA EXECUTIVA DE ADMINISTRAÇÃO E FINANÇAS</p>
        <p>DIRETORIA GERAL DE FINANÇAS</p>
        <p>RELATÓRIO DE FREQUÊNCIA MÉDICA</p>
      </td>
    </tr>
  </table>

  <!-- Título do formulário -->
  <table class="form-title-table">
    <tr>
      <td>FREQUÊNCIA MÉDICA INDIVIDUAL</td>
    </tr>
  </table>

  <!-- Campos do formulário -->
  <table class="fields-table">
    <tr>
      <td class="field-label-cell">Unidade de Saúde:</td>
      <td class="field-value-cell field-value-normal" colspan="2">${tomadorNome}</td>
    </tr>
    <tr>
      <td class="field-label-cell">Empresa Prestadora do Serviço:</td>
      <td class="field-value-cell field-value-empresa" colspan="2">${empresaNome} CNPJ: ${cnpjFormatado}</td>
    </tr>
    <tr>
      <td class="field-label-cell">Nome do Médico Prestador:</td>
      <td class="field-value-cell field-value-medico" colspan="2">${medicoNome} CRM-${medicoCrmUf} ${medicoCrm}</td>
    </tr>
    <tr>
      <td class="field-label-cell">Competência:</td>
      <td class="field-value-cell field-value-center" colspan="2">${mesExt}</td>
    </tr>
    <tr>
      <td class="field-label-cell">Especialidade Médica:</td>
      <td class="field-value-cell field-value-normal">${freq.tipoMedico ?? ''}</td>
      <td class="field-setor-cell">${freq.servicoOperacionalNome ?? ''}</td>
    </tr>
  </table>

  <!-- Tabela de plantões -->
  <table class="plantoes">
    <thead>
      <tr>
        <th class="col-data">Data</th>
        <th class="col-turno">Turno</th>
        <th class="col-horario">Horário</th>
        <th class="col-rubrica">Rubrica e Carimbo</th>
        <th>Ocorrência</th>
        <th class="col-lider">Líder</th>
      </tr>
    </thead>
    <tbody>
      ${linhasPreenchidas}
      ${linhasVazias}
    </tbody>
  </table>

  <div class="totalizador">
    Total de plantões registrados neste relatório: <span>${freq.itens.length}</span>
  </div>

  <!-- Assinaturas -->
  <div class="signatures">
    <div class="sig-box">
      <div class="sig-space"></div>
      <div class="sig-line"></div>
      <div class="sig-label">Prestador(a)</div>
      <div class="sig-sub">Assinatura e Carimbo</div>
    </div>
    <div class="sig-box">
      <div class="sig-space"></div>
      <div class="sig-line"></div>
      <div class="sig-label">Direção Médica</div>
      <div class="sig-sub">Assinatura e Carimbo</div>
    </div>
    <div class="sig-box">
      <div class="sig-space"></div>
      <div class="sig-line"></div>
      <div class="sig-label">Diretor(a) Adm. Financeiro</div>
      <div class="sig-sub">Assinatura e Carimbo</div>
    </div>
  </div>

  <!-- Rodapé -->
  <div class="footer">
    Gerado pelo Sistema Pin Saúde · ${gerarDataHoraAtual()} ·
    Competência: ${competenciaExt} · ${freq.itens.length} plantão(ões)
  </div>

</div>
<script>
  window.onload = function() {
    setTimeout(function() { window.print(); }, 300);
  };
</script>
</body>
</html>`
}

// ─── API pública ──────────────────────────────────────────────────────────────

export function abrirPdfFrequencia(params: FrequenciaPdfParams): void {
  const html = buildHtml(params)
  const win = window.open('', '_blank', 'width=900,height=1100,scrollbars=yes')
  if (!win) {
    alert('Habilite pop-ups para gerar o PDF desta frequência.')
    return
  }
  win.document.write(html)
  win.document.close()
}
